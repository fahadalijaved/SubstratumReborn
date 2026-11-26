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
import android.text.InputType;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.slider.Slider;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import projekt.substratum.R;
import projekt.substratum.Substratum;
import projekt.substratum.common.platform.AmoledJobs;
import projekt.substratum.common.platform.AmoledLevel;
import projekt.substratum.common.platform.AmoledPresets;
import projekt.substratum.common.platform.AmoledPreview;
import projekt.substratum.common.platform.AmoledRules;
import projekt.substratum.common.platform.AmoledState;
import projekt.substratum.common.platform.QsOptions;
import projekt.substratum.common.platform.SystemAmoled;
import projekt.substratum.util.helpers.Root;

/**
 * AMOLED for every app: apply or remove it, its look (presets, surface level, accent, with a live
 * preview), the One UI 9 quick settings options carried out by the LSPosed module, and every
 * themed app with a switch. Everything that changes the device runs as an {@link AmoledJobs} job;
 * this screen follows the queue. A change of look is saved at once and, while AMOLED is applied,
 * applied to every app shortly after the last change.
 */
public class AmoledSettingsActivity extends AppCompatActivity {

    public static final String EXTRA_THEME = "theme";
    public static final String EXTRA_THEME_NAME = "theme_name";
    /**
     * Opens this screen itself; without it the intent goes on to AMOLED Studio
     */
    public static final String EXTRA_CLASSIC = "classic";

    private static final long LOOK_APPLY_DELAY = 1200;
    private static final int[] LEVEL_BUTTONS = {R.id.amoled_level_pure, R.id.amoled_level_dim,
            R.id.amoled_level_elevated, R.id.amoled_level_tinted};
    private static final AmoledLevel[] LEVELS = {AmoledLevel.PURE, AmoledLevel.DIM,
            AmoledLevel.ELEVATED, AmoledLevel.TINTED};
    private static final int[] LEVEL_SUMMARIES = {R.string.amoled_level_pure_summary,
            R.string.amoled_level_dim_summary, R.string.amoled_level_elevated_summary,
            R.string.amoled_level_tinted_summary};

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    // The preview never waits behind the app list's root calls
    private final ExecutorService previewWorker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private String theme;
    private SharedPreferences prefs;
    private LinearProgressIndicator progress;
    private TextView status;
    private MaterialButton apply;
    private MaterialButton remove;
    private MaterialButton qsApply;
    private TextView qsOffNote;
    private Slider qsRows;
    private MaterialButtonToggleGroup qsTiles;
    private MaterialSwitch qsShade;
    private MaterialSwitch qsTrack;
    private boolean qsLoading;
    private boolean qsDirty;
    private boolean amoledOn;
    // The options as last read or applied: a change of the row height alone needs no AMOLED
    private float savedRows;
    private boolean savedShade;
    private boolean savedTrack;
    private int savedTile;
    // Look
    private AmoledLookPreview lookPreview;
    private ChipGroup presetGroup;
    private MaterialButtonToggleGroup levelGroup;
    private TextView levelSummary;
    private TextView accentName;
    private TextView lookNote;
    private MaterialSwitch accentLight;
    private final List<AmoledSwatch> swatches = new ArrayList<>();
    private final List<String> swatchAccents = new ArrayList<>();
    private boolean lookLoading;
    private volatile AmoledPreview.Stock previewStock;
    private int previewRequest;
    private boolean lookApplyPending;
    // An apply for a look change was queued and the queue has not been idle since
    private boolean lookApplyQueued;
    private final Runnable applyLook = this::applyLookNow;

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
            lookApplyQueued = false;
            loadApps();
            // The device's own stock colors may be known now
            previewStock = null;
            updatePreview(true);
        }
    };

    /**
     * AMOLED for every app: AMOLED Studio (the Reborn shell's AMOLED tab), applying with the rules
     * of {@code themePackage} (null keeps the Studio's choice).
     */
    public static void launch(Context context, String themePackage, CharSequence themeName) {
        Intent intent = new Intent(context, projekt.substratum.reborn.RebornActivity.class)
                .putExtra(projekt.substratum.reborn.RebornActivity.EXTRA_TAB, "amoled")
                .putExtra(projekt.substratum.reborn.RebornActivity.EXTRA_THEME, themePackage);
        if (!(context instanceof android.app.Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /**
     * This screen, kept as "Classic AMOLED screen" (the Studio's menu).
     */
    public static void launchClassic(Context context, String themePackage, CharSequence themeName) {
        Intent intent = new Intent(context, AmoledSettingsActivity.class)
                .putExtra(EXTRA_THEME, themePackage)
                .putExtra(EXTRA_THEME_NAME, themeName)
                .putExtra(EXTRA_CLASSIC, true);
        if (!(context instanceof android.app.Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!getIntent().getBooleanExtra(EXTRA_CLASSIC, false)) {
            // Any older way in leads to AMOLED Studio
            launch(this, getIntent().getStringExtra(EXTRA_THEME),
                    getIntent().getCharSequenceExtra(EXTRA_THEME_NAME));
            finish();
            return;
        }
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
            // A look change waiting to apply goes with this apply
            main.removeCallbacks(applyLook);
            lookApplyPending = false;
            lookNote.setVisibility(View.GONE);
            AmoledJobs.applyAll(this, theme);
            showRunning(getString(R.string.system_amoled_working));
        });
        remove.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.system_amoled_remove_title)
                .setMessage(R.string.system_amoled_remove_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.system_amoled_remove, (d, w) -> {
                    if (needsRoot()) return;
                    // A look change waiting to apply would apply AMOLED again right after the
                    // removal: it stays saved for the next apply instead
                    main.removeCallbacks(applyLook);
                    lookApplyPending = false;
                    lookNote.setVisibility(View.GONE);
                    amoledOn = false;
                    updateQsApply();
                    AmoledJobs.remove(this);
                    showRunning(getString(R.string.system_amoled_removing));
                })
                .show());

        setUpLook();
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
        checkWallpaperAccent();
    }

    @Override
    protected void onStop() {
        AmoledJobs.removeListener(jobs);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        // A look change still waiting is applied rather than lost
        if (lookApplyPending) {
            main.removeCallbacks(applyLook);
            try {
                if (amoledOn) AmoledJobs.applyAll(getApplicationContext(), theme);
            } catch (RuntimeException e) {
                // Not allowed from the background: the look is saved for the next apply
            }
        }
        worker.shutdown();
        previewWorker.shutdown();
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

    // Look

    private void setUpLook() {
        lookPreview = findViewById(R.id.amoled_look_preview);
        presetGroup = findViewById(R.id.amoled_preset_group);
        levelGroup = findViewById(R.id.amoled_level_group);
        levelSummary = findViewById(R.id.amoled_level_summary);
        accentName = findViewById(R.id.amoled_accent_name);
        accentLight = findViewById(R.id.amoled_accent_light);
        lookNote = findViewById(R.id.amoled_look_note);

        LayoutInflater inflater = LayoutInflater.from(this);
        String[] presets = {AmoledPresets.PURE_BLACK, AmoledPresets.ELEVATED_CARDS,
                AmoledPresets.DIM, AmoledPresets.SPRITE, AmoledPresets.CUSTOM};
        int[] names = {R.string.amoled_preset_pure, R.string.amoled_preset_elevated,
                R.string.amoled_preset_dim, R.string.amoled_preset_sprite,
                R.string.amoled_preset_custom};
        for (int i = 0; i < presets.length; i++) {
            Chip chip = (Chip) inflater.inflate(R.layout.amoled_preset_chip, presetGroup, false);
            chip.setId(View.generateViewId());
            chip.setText(names[i]);
            chip.setTag(presets[i]);
            presetGroup.addView(chip);
        }
        presetGroup.setOnCheckedStateChangeListener((group, checked) -> {
            if (lookLoading || checked.isEmpty()) return;
            String id = (String) group.findViewById(checked.get(0)).getTag();
            if (AmoledPresets.CUSTOM.equals(id)) {
                // Custom is whatever the level and accent below say
                prefs.edit().putString(AmoledPresets.PREF_PRESET, AmoledPresets.CUSTOM).apply();
                return;
            }
            String tileBefore = tileChoice();
            AmoledPresets.choose(prefs, id);
            group.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
            lookChanged(!tileBefore.equals(tileChoice()));
        });

        levelGroup.addOnButtonCheckedListener((group, id, checked) -> {
            if (lookLoading || !checked) return;
            for (int i = 0; i < LEVEL_BUTTONS.length; i++) {
                if (LEVEL_BUTTONS[i] != id) continue;
                AmoledPresets.setLevel(prefs, LEVELS[i]);
                lookChanged(false);
            }
        });

        LinearLayout row = findViewById(R.id.amoled_accent_row);
        List<String> accents = new ArrayList<>(AmoledPresets.ACCENTS);
        for (String accent : accents) addSwatch(row, accent);
        addSwatch(row, null);

        accentLight.setOnCheckedChangeListener((b, on) -> {
            if (lookLoading) return;
            AmoledPresets.setAccentLight(prefs, on);
            lookChanged(false);
        });
        showLook(false);
        // Whether a look change applies at once, before the app list has loaded
        Context app = getApplicationContext();
        previewWorker.execute(() -> {
            boolean on = AmoledState.read(app).enabled;
            main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                amoledOn = on;
                updateQsApply();
            });
        });
    }

    private AmoledSwatch addSwatch(LinearLayout row, String accent) {
        AmoledSwatch swatch = new AmoledSwatch(this);
        // Evenly spread over the row
        FrameLayout cell = new FrameLayout(this);
        row.addView(cell, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        cell.addView(swatch, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, android.view.Gravity.CENTER));
        swatches.add(swatch);
        swatchAccents.add(accent);
        swatch.setContentDescription(getString(accentLabel(accent)));
        swatch.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
            if (accent == null) {
                pickCustomAccent();
                return;
            }
            chooseAccent(accent);
        });
        return swatch;
    }

    private void chooseAccent(String accent) {
        String tileBefore = tileChoice();
        AmoledPresets.setAccent(prefs, accent);
        lookChanged(!tileBefore.equals(tileChoice()));
    }

    private void pickCustomAccent() {
        String current = prefs.getString(AmoledPresets.PREF_ACCENT, null);
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setSingleLine(true);
        input.setHint(R.string.amoled_accent_custom_hint);
        if (AmoledPresets.isCustom(current)) input.setText(current);
        FrameLayout frame = new FrameLayout(this);
        int pad = Math.round(24 * getResources().getDisplayMetrics().density);
        frame.setPadding(pad, pad / 3, pad, 0);
        frame.addView(input);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.amoled_accent_custom_title)
                .setView(frame)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(
                androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String text = input.getText().toString().trim();
            if (!text.startsWith("#")) text = "#" + text;
            Integer color = text.length() == 7 ? AmoledPresets.resolveAccent(text, null) : null;
            if (color == null) {
                input.setError(getString(R.string.amoled_accent_custom_invalid));
                return;
            }
            dialog.dismiss();
            chooseAccent(String.format(Locale.ROOT, "#%06X", color & 0xffffff));
        }));
        dialog.show();
    }

    private static int accentLabel(String accent) {
        if (accent == null) return R.string.amoled_accent_custom;
        switch (accent) {
            case AmoledPresets.ACCENT_MONET:
                return R.string.amoled_accent_monet;
            case AmoledPresets.ACCENT_ONEUI:
                return R.string.amoled_accent_oneui;
            case AmoledPresets.ACCENT_SPRITE:
                return R.string.amoled_accent_sprite;
            case AmoledPresets.ACCENT_WHITE:
                return R.string.amoled_accent_white;
            case AmoledPresets.ACCENT_NONE:
            default:
                return R.string.amoled_accent_none;
        }
    }

    private String tileChoice() {
        return prefs.getString(AmoledRules.PREF_ACTIVE_TILE, AmoledRules.TILE_WHITE);
    }

    /**
     * Show the saved look on the controls (without reporting it as a change).
     */
    private void showLook(boolean animate) {
        lookLoading = true;
        AmoledPresets.Look look = AmoledPresets.look(prefs);
        AmoledLevel level = look.level != null ? look.level : AmoledLevel.PURE;
        String accent = look.accent != null ? look.accent : AmoledPresets.ACCENT_NONE;
        String preset = look.preset != null ? look.preset :
                AmoledPresets.matching(level, accent, tileChoice());
        for (int i = 0; i < presetGroup.getChildCount(); i++) {
            Chip chip = (Chip) presetGroup.getChildAt(i);
            if (preset.equals(chip.getTag())) presetGroup.check(chip.getId());
        }
        for (int i = 0; i < LEVELS.length; i++) {
            if (LEVELS[i] == level) {
                levelGroup.check(LEVEL_BUTTONS[i]);
                levelSummary.setText(LEVEL_SUMMARIES[i]);
            }
        }
        Integer monet = AmoledPresets.monetAccent(this);
        boolean custom = AmoledPresets.isCustom(accent);
        for (int i = 0; i < swatches.size(); i++) {
            String choice = swatchAccents.get(i);
            AmoledSwatch swatch = swatches.get(i);
            if (choice == null) {
                Integer color = custom ? AmoledPresets.resolveAccent(accent, null) : null;
                swatch.setSwatch(color != null ? AmoledSwatch.Kind.COLOR :
                        AmoledSwatch.Kind.PICKER, color != null ? color : 0);
                swatch.setChosen(custom, animate);
            } else {
                Integer color = AmoledPresets.resolveAccent(choice, monet);
                swatch.setSwatch(color == null ? AmoledSwatch.Kind.NONE : AmoledSwatch.Kind.COLOR,
                        color == null ? 0 : color);
                swatch.setChosen(choice.equalsIgnoreCase(accent), animate);
            }
        }
        accentName.setText(custom ? getString(R.string.amoled_accent_custom_value,
                accent.toUpperCase(Locale.ROOT)) : getString(accentLabel(accent)));
        accentLight.setChecked(look.accentLight != null && look.accentLight);
        accentLight.setEnabled(!AmoledPresets.ACCENT_NONE.equals(accent));
        lookLoading = false;
        updatePreview(animate);
    }

    /**
     * A part of the look was chosen and saved: show it, and apply it soon if AMOLED is on.
     *
     * @param tileChanged The active tile option changed with it (a preset)
     */
    private void lookChanged(boolean tileChanged) {
        showLook(true);
        if (tileChanged) showTileChoice();
        main.removeCallbacks(applyLook);
        if (amoledOn) {
            lookApplyPending = true;
            main.postDelayed(applyLook, LOOK_APPLY_DELAY);
            lookNote.setText(R.string.amoled_look_applying);
        } else {
            lookApplyPending = false;
            lookNote.setText(R.string.amoled_look_saved);
        }
        lookNote.setVisibility(View.VISIBLE);
    }

    private void applyLookNow() {
        lookApplyPending = false;
        lookNote.setVisibility(View.GONE);
        if (!amoledOn || needsRoot()) return;
        AmoledJobs.applyAll(this, theme);
        lookApplyQueued = true;
        showRunning(getString(R.string.system_amoled_working));
        // The quick settings tiles are recreated with System UI
        if (AmoledRules.TILE_ACCENT.equals(tileChoice()) || qsTileDirty()) {
            Context app = getApplicationContext();
            String rulesTheme = theme;
            Snackbar.make(findViewById(android.R.id.content), R.string.amoled_look_tile_restart,
                    Snackbar.LENGTH_LONG)
                    .setAction(R.string.amoled_look_restart_systemui, v ->
                            AmoledJobs.applySystemUi(app, rulesTheme))
                    .show();
        }
    }

    /**
     * Work out the preview of the saved look (the rules, the device's stock colors) off the main
     * thread, and morph to it.
     */
    private void updatePreview(boolean animate) {
        if (lookPreview == null || previewWorker.isShutdown()) return;
        int request = ++previewRequest;
        Context app = getApplicationContext();
        String rulesTheme = theme;
        previewWorker.execute(() -> {
            AmoledPreview.Stock stock = previewStock;
            if (stock == null) previewStock = stock = AmoledPreview.stock(app);
            AmoledRules rules = AmoledRules.effective(app, rulesTheme, prefs);
            Map<String, int[]> roles = AmoledPreview.roles(stock, rules);
            main.post(() -> {
                if (isFinishing() || isDestroyed() || request != previewRequest) return;
                lookPreview.setRoles(roles, animate);
            });
        });
    }

    /**
     * The accent follows the wallpaper and the wallpaper changed since the last apply: offer to
     * apply again.
     */
    private void checkWallpaperAccent() {
        if (previewWorker.isShutdown()) return;
        Context app = getApplicationContext();
        previewWorker.execute(() -> {
            if (!AmoledPresets.monetChanged(app, prefs)) return;
            main.post(() -> {
                if (isFinishing() || isDestroyed() || AmoledJobs.isBusy()) return;
                updatePreview(true);
                Snackbar.make(findViewById(android.R.id.content),
                        R.string.amoled_look_monet_changed, Snackbar.LENGTH_INDEFINITE)
                        .setAction(R.string.system_amoled_apply, v -> {
                            if (needsRoot()) return;
                            AmoledJobs.applyAll(this, theme);
                            showRunning(getString(R.string.system_amoled_working));
                        })
                        .show();
            });
        });
    }

    // Quick settings

    private int tileButton(String tile) {
        if (AmoledRules.TILE_BLUE.equals(tile)) return R.id.amoled_qs_tile_blue;
        if (AmoledRules.TILE_STOCK.equals(tile)) return R.id.amoled_qs_tile_stock;
        if (AmoledRules.TILE_ACCENT.equals(tile)) return R.id.amoled_qs_tile_accent;
        return R.id.amoled_qs_tile_white;
    }

    private String tileOf(int button) {
        if (button == R.id.amoled_qs_tile_blue) return AmoledRules.TILE_BLUE;
        if (button == R.id.amoled_qs_tile_stock) return AmoledRules.TILE_STOCK;
        if (button == R.id.amoled_qs_tile_accent) return AmoledRules.TILE_ACCENT;
        return AmoledRules.TILE_WHITE;
    }

    /**
     * A preset changed the active tile: show it, still to be applied to System UI (the QS Apply
     * turns on).
     */
    private void showTileChoice() {
        if (qsTiles == null) return;
        qsTiles.check(tileButton(tileChoice()));
        qsChanged();
    }

    private boolean qsTileDirty() {
        return qsTiles != null && qsTiles.getCheckedButtonId() != savedTile;
    }

    private void setUpQuickSettings() {
        Slider slider = qsRows = findViewById(R.id.amoled_qs_row_slider);
        TextView value = findViewById(R.id.amoled_qs_row_value);
        MaterialButtonToggleGroup tiles = qsTiles = findViewById(R.id.amoled_qs_tile_group);
        MaterialSwitch shade = qsShade = findViewById(R.id.amoled_qs_shade);
        MaterialSwitch track = qsTrack = findViewById(R.id.amoled_qs_track);
        qsApply = findViewById(R.id.amoled_qs_apply);
        qsOffNote = findViewById(R.id.amoled_qs_off_note);

        qsLoading = true;
        QsOptions options = QsOptions.read();
        float aspect = options.aspect > 0 ? options.aspect : QsOptions.STOCK_ASPECT;
        slider.setValue(Math.max(50, Math.min(92, Math.round(aspect * 100))));
        value.setText(rowLabel(slider.getValue()));
        shade.setChecked(options.shade);
        track.setChecked(options.track);
        tiles.check(tileButton(tileChoice()));
        rememberQsChoice();
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
            // Without AMOLED only the row height applies; the button is off otherwise, so a
            // late tap does nothing
            if (!amoledOn && !onlyRowsChanged()) return;
            // A look change waiting to apply goes first, so System UI restarts after it
            if (lookApplyPending) {
                main.removeCallbacks(applyLook);
                applyLookNow();
            }
            float rows = slider.getValue() / 100f;
            float chosenAspect = rows >= QsOptions.STOCK_ASPECT - 0.005f ? 0 : rows;
            boolean chosenShade = shade.isChecked();
            boolean chosenTrack = track.isChecked();
            String tileChoice = tileOf(tiles.getCheckedButtonId());
            String previousTile = tileChoice();
            prefs.edit().putString(AmoledRules.PREF_ACTIVE_TILE, tileChoice)
                    .putBoolean(AmoledRules.PREF_QS_CHOSEN, true).apply();
            rememberQsChoice();
            qsDirty = false;
            qsApply.setEnabled(false);
            // An apply of a new look still running: System UI is read again after it
            boolean colorsChanged = !tileChoice.equals(previousTile) || lookApplyQueued ||
                    AmoledJobs.isBusy();
            if (!tileChoice.equals(previousTile)) {
                // The tile is part of a preset (Sprite): another tile makes the look custom
                // (the preset wrote its level and accent, which stay)
                AmoledPresets.Preset preset = AmoledPresets.preset(
                        prefs.getString(AmoledPresets.PREF_PRESET, null));
                if (preset != null && preset.tile != null && !preset.tile.equals(tileChoice)) {
                    prefs.edit().putString(AmoledPresets.PREF_PRESET, AmoledPresets.CUSTOM)
                            .apply();
                }
                showLook(true);
            }
            Context app = getApplicationContext();
            String rulesTheme = theme;
            // Written and carried out in one task: leaving or recreating the screen right after
            // the tap neither loses the apply nor hands work to a stopped executor
            worker.execute(() -> {
                // Read here (a file): keeps the rules' track color
                QsOptions chosen = QsOptions.read();
                chosen.aspect = chosenAspect;
                chosen.shade = chosenShade;
                chosen.track = chosenTrack;
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
        qsDirty = qsRows.getValue() != savedRows || qsShade.isChecked() != savedShade ||
                qsTrack.isChecked() != savedTrack || qsTiles.getCheckedButtonId() != savedTile;
        updateQsApply();
    }

    private void rememberQsChoice() {
        savedRows = qsRows.getValue();
        savedShade = qsShade.isChecked();
        savedTrack = qsTrack.isChecked();
        savedTile = qsTiles.getCheckedButtonId();
    }

    /**
     * Only the panel row height differs from what is applied: the module lays out the panel
     * with or without AMOLED.
     */
    private boolean onlyRowsChanged() {
        return qsRows.getValue() != savedRows && qsShade.isChecked() == savedShade &&
                qsTrack.isChecked() == savedTrack && qsTiles.getCheckedButtonId() == savedTile;
    }

    /**
     * The quick settings colors and switches act on top of AMOLED: while it is off only a row
     * height change can be applied.
     */
    private void updateQsApply() {
        qsApply.setEnabled(qsDirty && (amoledOn || onlyRowsChanged()));
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
