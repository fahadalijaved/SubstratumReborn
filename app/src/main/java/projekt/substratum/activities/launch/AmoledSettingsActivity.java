/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.activities.launch;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.slider.Slider;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import projekt.substratum.R;
import projekt.substratum.Substratum;
import projekt.substratum.common.platform.AmoledJobs;
import projekt.substratum.common.platform.AmoledRules;
import projekt.substratum.common.platform.AmoledState;
import projekt.substratum.common.platform.QsOptions;
import projekt.substratum.common.platform.SystemAmoled;
import projekt.substratum.util.helpers.Root;

/**
 * AMOLED for every app: apply or remove it, the One UI 9 quick settings options carried out by
 * the LSPosed module, and every themed app with a switch. Everything that changes the device runs
 * as an {@link AmoledJobs} job; this screen follows the queue.
 */
public class AmoledSettingsActivity extends AppCompatActivity {

    public static final String EXTRA_THEME = "theme";
    public static final String EXTRA_THEME_NAME = "theme_name";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private String theme;
    private SharedPreferences prefs;
    private LinearProgressIndicator progress;
    private TextView status;
    private MaterialButton apply;
    private MaterialButton remove;
    private MaterialButton qsApply;
    private TextView qsOffNote;
    private boolean qsLoading;
    private boolean qsDirty;
    private boolean amoledOn;

    private final AmoledJobs.Listener jobs = new AmoledJobs.Listener() {
        @Override
        public void onJobStarted(AmoledJobs.Job job) {
            if (job.kind == AmoledJobs.Kind.SET_ENABLED) return;
            showRunning(getString(job.kind == AmoledJobs.Kind.REMOVE ?
                    R.string.system_amoled_removing : job.kind == AmoledJobs.Kind.RESTORE ?
                    R.string.system_amoled_restoring : R.string.system_amoled_working));
        }

        @Override
        public void onProgress(AmoledJobs.Job job, String pkg, int done, int total) {
            showRunning(getString(R.string.system_amoled_progress, done, total,
                    AmoledJobs.labelOf(pkg)));
            progress.setIndeterminate(false);
            progress.setMax(Math.max(total, 1));
            progress.setProgressCompat(done, true);
        }

        @Override
        public void onJobFinished(AmoledJobs.Job job, boolean ok, String message) {
            if (message == null) return;
            if (job.kind == AmoledJobs.Kind.SET_ENABLED) {
                Snackbar.make(findViewById(android.R.id.content), message,
                        Snackbar.LENGTH_SHORT).show();
            } else {
                status.setText(message);
            }
        }

        @Override
        public void onQueueIdle() {
            progress.setVisibility(View.GONE);
            apply.setEnabled(true);
            remove.setEnabled(true);
            loadApps();
        }
    };

    public static void launch(Context context, String themePackage, CharSequence themeName) {
        context.startActivity(new Intent(context, AmoledSettingsActivity.class)
                .putExtra(EXTRA_THEME, themePackage)
                .putExtra(EXTRA_THEME_NAME, themeName));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.amoled_settings_activity);
        prefs = Substratum.getPreferences();
        theme = getIntent().getStringExtra(EXTRA_THEME);
        CharSequence themeName = getIntent().getCharSequenceExtra(EXTRA_THEME_NAME);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.system_amoled_title);
        toolbar.setNavigationOnClickListener(v -> finish());
        ((TextView) findViewById(R.id.amoled_theme_name)).setText(
                themeName != null ? themeName : getString(R.string.system_amoled_title));

        progress = findViewById(R.id.amoled_progress);
        status = findViewById(R.id.amoled_status);
        apply = findViewById(R.id.amoled_apply);
        remove = findViewById(R.id.amoled_remove);
        apply.setOnClickListener(v -> {
            if (needsRoot()) return;
            AmoledJobs.applyAll(this, theme);
            showRunning(getString(R.string.system_amoled_working));
        });
        remove.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.system_amoled_remove_title)
                .setMessage(R.string.system_amoled_remove_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.system_amoled_remove, (d, w) -> {
                    if (needsRoot()) return;
                    AmoledJobs.remove(this);
                    showRunning(getString(R.string.system_amoled_removing));
                })
                .show());

        setUpQuickSettings();
    }

    @Override
    protected void onStart() {
        super.onStart();
        AmoledJobs.addListener(jobs);
        AmoledJobs.Snapshot snapshot = AmoledJobs.snapshot();
        if (snapshot.isIdle()) {
            progress.setVisibility(View.GONE);
            apply.setEnabled(true);
            remove.setEnabled(true);
        } else {
            showRunning(getString(snapshot.current != null &&
                    snapshot.current.kind == AmoledJobs.Kind.REMOVE ?
                    R.string.system_amoled_removing : R.string.system_amoled_working));
        }
        loadApps();
    }

    @Override
    protected void onStop() {
        AmoledJobs.removeListener(jobs);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }

    private boolean needsRoot() {
        if (Root.checkRootAccess()) return false;
        Toast.makeText(this, R.string.system_amoled_needs_root, Toast.LENGTH_LONG).show();
        return true;
    }

    private void showRunning(String text) {
        // A new run starts as "working" until it reports progress (switched while hidden only)
        if (progress.getVisibility() != View.VISIBLE) progress.setIndeterminate(true);
        progress.setVisibility(View.VISIBLE);
        apply.setEnabled(false);
        remove.setEnabled(false);
        status.setText(text);
    }

    // Quick settings

    private void setUpQuickSettings() {
        Slider slider = findViewById(R.id.amoled_qs_row_slider);
        TextView value = findViewById(R.id.amoled_qs_row_value);
        MaterialButtonToggleGroup tiles = findViewById(R.id.amoled_qs_tile_group);
        MaterialSwitch shade = findViewById(R.id.amoled_qs_shade);
        MaterialSwitch track = findViewById(R.id.amoled_qs_track);
        qsApply = findViewById(R.id.amoled_qs_apply);
        qsOffNote = findViewById(R.id.amoled_qs_off_note);

        qsLoading = true;
        QsOptions options = QsOptions.read();
        float aspect = options.aspect > 0 ? options.aspect : QsOptions.STOCK_ASPECT;
        slider.setValue(Math.max(50, Math.min(92, Math.round(aspect * 100))));
        value.setText(rowLabel(slider.getValue()));
        shade.setChecked(options.shade);
        track.setChecked(options.track);
        String tile = prefs.getString(AmoledRules.PREF_ACTIVE_TILE, AmoledRules.TILE_WHITE);
        tiles.check(AmoledRules.TILE_BLUE.equals(tile) ? R.id.amoled_qs_tile_blue :
                AmoledRules.TILE_STOCK.equals(tile) ? R.id.amoled_qs_tile_stock :
                        R.id.amoled_qs_tile_white);
        qsLoading = false;

        slider.addOnChangeListener((s, v, fromUser) -> {
            value.setText(rowLabel(v));
            qsChanged();
        });
        tiles.addOnButtonCheckedListener((group, id, checked) -> {
            if (checked) qsChanged();
        });
        shade.setOnCheckedChangeListener((b, c) -> qsChanged());
        track.setOnCheckedChangeListener((b, c) -> qsChanged());

        qsApply.setOnClickListener(v -> {
            if (needsRoot()) return;
            if (!amoledOn) return; // the button is off then; a late tap does nothing
            QsOptions chosen = new QsOptions();
            float rows = slider.getValue() / 100f;
            chosen.aspect = rows >= QsOptions.STOCK_ASPECT - 0.005f ? 0 : rows;
            chosen.shade = shade.isChecked();
            chosen.track = track.isChecked();
            int checked = tiles.getCheckedButtonId();
            String tileChoice = checked == R.id.amoled_qs_tile_blue ? AmoledRules.TILE_BLUE :
                    checked == R.id.amoled_qs_tile_stock ? AmoledRules.TILE_STOCK :
                            AmoledRules.TILE_WHITE;
            String previousTile = prefs.getString(AmoledRules.PREF_ACTIVE_TILE,
                    AmoledRules.TILE_WHITE);
            prefs.edit().putString(AmoledRules.PREF_ACTIVE_TILE, tileChoice)
                    .putBoolean(AmoledRules.PREF_QS_CHOSEN, true).apply();
            qsDirty = false;
            qsApply.setEnabled(false);
            boolean colorsChanged = !tileChoice.equals(previousTile);
            Context app = getApplicationContext();
            String rulesTheme = theme;
            // Written and carried out in one task: leaving or recreating the screen right after
            // the tap neither loses the apply nor hands work to a stopped executor
            worker.execute(() -> {
                boolean written = chosen.write(app);
                if (written) {
                    if (colorsChanged) {
                        // New tile color: register System UI's colors again, then restart it
                        // (the job only does that while AMOLED is applied)
                        AmoledJobs.applySystemUi(app, rulesTheme);
                    } else {
                        QsOptions.restartSystemUi();
                    }
                }
                main.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (!written) {
                        Toast.makeText(this, R.string.system_amoled_failed,
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    // Otherwise the listener shows the job when it starts (or it already ended)
                    if (colorsChanged && AmoledJobs.isBusy()) {
                        showRunning(getString(R.string.system_amoled_working));
                    }
                    Toast.makeText(this, R.string.amoled_qs_applied, Toast.LENGTH_SHORT).show();
                });
            });
        });
        // Enabled (or explained) once loadApps has read whether AMOLED is applied
    }

    private void qsChanged() {
        if (qsLoading) return;
        qsDirty = true;
        updateQsApply();
    }

    /**
     * The quick settings colors and switches act on top of AMOLED: no apply while it is off.
     */
    private void updateQsApply() {
        qsApply.setEnabled(amoledOn && qsDirty);
        qsOffNote.setVisibility(amoledOn ? View.GONE : View.VISIBLE);
    }

    private String rowLabel(float percent) {
        return percent >= 92 ? getString(R.string.amoled_qs_row_stock) :
                String.format(Locale.US, "%.2f", percent / 100f);
    }

    // Apps

    private void loadApps() {
        LinearLayout list = findViewById(R.id.amoled_apps);
        CircularProgressIndicator loading = findViewById(R.id.amoled_apps_loading);
        TextView title = findViewById(R.id.amoled_apps_title);
        loading.setVisibility(View.VISIBLE);
        worker.execute(() -> {
            List<SystemAmoled.AppState> apps = Root.checkRootAccess() ?
                    SystemAmoled.listApps(this) : java.util.Collections.emptyList();
            boolean on = AmoledState.read(this).enabled;
            PackageManager pm = getPackageManager();
            Object[][] rows = new Object[apps.size()][];
            int themed = 0;
            for (int i = 0; i < apps.size(); i++) {
                SystemAmoled.AppState app = apps.get(i);
                CharSequence label = app.packageName;
                Drawable icon = null;
                try {
                    ApplicationInfo info = pm.getApplicationInfo(app.packageName, 0);
                    label = pm.getApplicationLabel(info);
                    icon = pm.getApplicationIcon(info);
                } catch (PackageManager.NameNotFoundException ignored) {
                    // uninstalled since
                }
                if (app.enabled && app.active) themed++;
                boolean restartable = app.restartNeeded &&
                        SystemAmoled.safeToRestart(this, app.packageName);
                rows[i] = new Object[]{app, label, icon, restartable};
            }
            java.util.Arrays.sort(rows, (a, b) -> a[1].toString()
                    .compareToIgnoreCase(b[1].toString()));
            int count = themed;
            main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                amoledOn = on;
                updateQsApply();
                loading.setVisibility(View.GONE);
                title.setText(getString(R.string.amoled_apps_title_count, count, rows.length));
                list.removeAllViews();
                LayoutInflater inflater = LayoutInflater.from(this);
                for (Object[] row : rows) list.addView(appRow(inflater, list, row));
            });
        });
    }

    private View appRow(LayoutInflater inflater, LinearLayout parent, Object[] row) {
        SystemAmoled.AppState app = (SystemAmoled.AppState) row[0];
        View view = inflater.inflate(R.layout.amoled_app_item, parent, false);
        ((TextView) view.findViewById(R.id.amoled_app_label)).setText((CharSequence) row[1]);
        if (row[2] != null) {
            ((ImageView) view.findViewById(R.id.amoled_app_icon)).setImageDrawable((Drawable) row[2]);
        }
        TextView summary = view.findViewById(R.id.amoled_app_summary);
        MaterialSwitch toggle = view.findViewById(R.id.amoled_app_switch);
        MaterialButton restart = view.findViewById(R.id.amoled_app_restart);
        switch (app.route) {
            case MODULE:
                summary.setText(R.string.amoled_app_module);
                break;
            case UNSUPPORTED:
                summary.setText(R.string.amoled_app_unsupported);
                toggle.setEnabled(false);
                break;
            case NONE:
                summary.setText(R.string.amoled_app_not_themed);
                break;
            default:
                summary.setText(app.packageName);
                break;
        }
        if (app.restartNeeded) {
            summary.setText(R.string.amoled_app_restart_needed);
            if ((Boolean) row[3]) {
                restart.setVisibility(View.VISIBLE);
                restart.setOnClickListener(v -> {
                    restart.setEnabled(false);
                    String label = row[1].toString();
                    worker.execute(() -> {
                        boolean done = SystemAmoled.restartApp(this, app.packageName);
                        main.post(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            if (done) {
                                Snackbar.make(findViewById(android.R.id.content),
                                        getString(R.string.amoled_app_restarted, label),
                                        Snackbar.LENGTH_SHORT).show();
                            }
                            loadApps();
                        });
                    });
                });
            }
        }
        toggle.setChecked(app.enabled);
        toggle.setOnCheckedChangeListener((b, checked) ->
                AmoledJobs.setEnabled(this, app.packageName, checked));
        view.setOnClickListener(v -> {
            if (toggle.isEnabled()) toggle.toggle();
        });
        return view;
    }
}
