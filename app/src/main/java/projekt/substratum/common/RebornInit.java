/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.common;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

import projekt.substratum.Substratum;
import projekt.substratum.util.helpers.BinaryInstaller;

import static projekt.substratum.common.References.APP_THEME;
import static projekt.substratum.common.References.AUTO_THEME;
import static projekt.substratum.common.References.DEFAULT_THEME;

/**
 * What the splash screen, the intro slides and Classic's MainActivity used to set up before
 * anything else ran, for the Reborn shell, which opens straight to Home. Notification channels
 * are created by {@link Substratum#onCreate()}; the storage permission is asked by Classic when
 * it is opened.
 */
public final class RebornInit {

    /**
     * Set once the setup checklist was left: it is not shown on its own again
     */
    public static final String PREF_SETUP_DONE = "reborn_setup_done";
    public static final String PREF_THEME_MIGRATED = "reborn_theme_migrated";
    private static final String TAG = "RebornInit";
    private static final AtomicBoolean STARTED = new AtomicBoolean();

    private RebornInit() {
    }

    /**
     * Once: an app theme stored as "light" (the old default, which nobody picked on purpose)
     * becomes "follow the system". Called before the night mode is chosen.
     */
    public static void migrateAppTheme(SharedPreferences prefs) {
        if (prefs == null || prefs.getBoolean(PREF_THEME_MIGRATED, false)) return;
        SharedPreferences.Editor editor = prefs.edit();
        if (DEFAULT_THEME.equals(prefs.getString(APP_THEME, null))) {
            editor.putString(APP_THEME, AUTO_THEME);
        }
        editor.putBoolean(PREF_THEME_MIGRATED, true).apply();
    }

    public static boolean isSetupDone() {
        SharedPreferences prefs = Substratum.getPreferences();
        return prefs != null && prefs.getBoolean(PREF_SETUP_DONE, false);
    }

    public static void setSetupDone() {
        SharedPreferences prefs = Substratum.getPreferences();
        if (prefs != null) prefs.edit().putBoolean(PREF_SETUP_DONE, true).apply();
    }

    /**
     * Start the first-run and per-launch setup on a background thread, once per process.
     */
    public static void start(Context context) {
        if (!STARTED.compareAndSet(false, true)) return;
        Context app = context.getApplicationContext();
        Thread thread = new Thread(() -> run(app), "RebornInit");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Blocking: root, the shell and asset copies.
     */
    static void run(Context context) {
        SharedPreferences prefs = Substratum.getPreferences();
        if (prefs == null) return;
        boolean firstRun = prefs.getBoolean("first_run", true);
        try {
            if (firstRun) {
                // The end of the intro: default preferences (first_run off, follow-the-system
                // theme), compiler binaries extracted, launcher icon enabled
                References.loadDefaultConfig(context);
            } else {
                // MainActivity's start: binaries refreshed after an update of the app
                BinaryInstaller.install(context, false);
            }
            // MainActivity's start
            Systems.setROMVersion(false);
            Systems.setAndCheckOMS(context);
            Systems.setAndCheckSubstratumService();
            Theming.refreshInstalledThemesPref(context);
            // The splash screen's engine detection: forced on the first run only, so a chosen
            // engine (LSPosed) stays as it is
            Systems.checkThemeSystemModule(context, firstRun);
            Systems.checkPackageSupport(context, false);
        } catch (Throwable t) {
            Log.e(TAG, "Setup did not finish", t);
        }
        // Apps updated since AMOLED read them are re-themed now, and from now on every hour
        try {
            projekt.substratum.services.system.AmoledWatchJob.schedule(context);
            projekt.substratum.services.system.AmoledWatchJob.check(context, "open");
        } catch (Throwable t) {
            Log.e(TAG, "AMOLED update check failed", t);
        }
    }
}
