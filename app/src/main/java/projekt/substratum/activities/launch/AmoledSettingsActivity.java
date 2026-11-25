/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.activities.launch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.slider.Slider;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import projekt.substratum.R;
import projekt.substratum.Substratum;
import projekt.substratum.common.platform.AmoledRules;
import projekt.substratum.common.platform.QsOptions;
import projekt.substratum.common.platform.SystemAmoled;
import projekt.substratum.services.system.SystemAmoledService;
import projekt.substratum.util.helpers.Root;

/**
 * AMOLED for every app: apply or remove it, the One UI 9 quick settings options carried out by
 * the LSPosed module, and every themed app with a switch.
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
    private BroadcastReceiver receiver;
    private boolean qsLoading;

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
        apply.setOnClickListener(v -> run(true));
        remove.setOnClickListener(v -> run(false));
        if (SystemAmoledService.isRunning()) showRunning(getString(R.string.system_amoled_working));

        setUpQuickSettings();
        loadApps();
    }

    @Override
    protected void onStart() {
        super.onStart();
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getBooleanExtra(SystemAmoledService.EXTRA_FINISHED, false)) {
                    progress.setVisibility(View.GONE);
                    apply.setEnabled(true);
                    remove.setEnabled(true);
                    status.setText(intent.getStringExtra(SystemAmoledService.EXTRA_RESULT));
                    loadApps();
                    return;
                }
                int done = intent.getIntExtra(SystemAmoledService.EXTRA_DONE, 0);
                int total = intent.getIntExtra(SystemAmoledService.EXTRA_TOTAL, 0);
                showRunning(context.getString(R.string.system_amoled_progress, done, total,
                        intent.getStringExtra(SystemAmoledService.EXTRA_PACKAGE)));
                progress.setIndeterminate(false);
                progress.setMax(Math.max(total, 1));
                progress.setProgressCompat(done, true);
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver,
                new IntentFilter(SystemAmoledService.PROGRESS));
    }

    @Override
    protected void onStop() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }

    private void run(boolean applyAll) {
        if (!Root.checkRootAccess()) {
            Toast.makeText(this, R.string.system_amoled_needs_root, Toast.LENGTH_LONG).show();
            return;
        }
        SystemAmoledService.start(this, applyAll, applyAll ? theme : null);
        showRunning(getString(applyAll ? R.string.system_amoled_working :
                R.string.system_amoled_removing));
    }

    private void showRunning(String text) {
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
            if (!Root.checkRootAccess()) {
                Toast.makeText(this, R.string.system_amoled_needs_root, Toast.LENGTH_LONG).show();
                return;
            }
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
            qsApply.setEnabled(false);
            boolean colorsChanged = !tileChoice.equals(previousTile);
            worker.execute(() -> {
                boolean written = chosen.write(this);
                main.post(() -> {
                    if (!written) {
                        Toast.makeText(this, R.string.system_amoled_failed,
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (colorsChanged) {
                        // New tile color: register System UI's colors again, then restart it
                        SystemAmoledService.applySystemUi(this, theme);
                        showRunning(getString(R.string.system_amoled_working));
                    } else {
                        worker.execute(QsOptions::restartSystemUi);
                    }
                    Toast.makeText(this, R.string.amoled_qs_applied, Toast.LENGTH_SHORT).show();
                });
            });
        });
    }

    private void qsChanged() {
        if (!qsLoading) qsApply.setEnabled(true);
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
                    SystemAmoled.listApps() : java.util.Collections.emptyList();
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
                if (app.enabled) themed++;
                rows[i] = new Object[]{app, label, icon};
            }
            java.util.Arrays.sort(rows, (a, b) -> a[1].toString()
                    .compareToIgnoreCase(b[1].toString()));
            int count = themed;
            main.post(() -> {
                if (isFinishing()) return;
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
        switch (app.kind) {
            case MODULE:
                summary.setText(R.string.amoled_app_module);
                break;
            case UNSUPPORTED:
                summary.setText(R.string.amoled_app_unsupported);
                toggle.setEnabled(false);
                break;
            default:
                summary.setText(app.packageName);
                break;
        }
        toggle.setChecked(app.enabled);
        toggle.setOnCheckedChangeListener((b, checked) ->
                worker.execute(() -> SystemAmoled.setAppEnabled(app.packageName, checked)));
        view.setOnClickListener(v -> {
            if (toggle.isEnabled()) toggle.toggle();
        });
        return view;
    }
}
