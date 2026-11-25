/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.services.system;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import projekt.substratum.R;
import projekt.substratum.common.References;
import projekt.substratum.common.platform.AmoledRules;
import projekt.substratum.common.platform.SystemAmoled;

/**
 * Runs "AMOLED for every app" in the foreground, with a progress notification, and broadcasts
 * {@link #PROGRESS} so an open Substratum screen can show the same progress.
 */
public class SystemAmoledService extends Service {

    public static final String PROGRESS = "projekt.substratum.AMOLED_PROGRESS";
    public static final String EXTRA_APPLY = "apply";
    public static final String EXTRA_THEME = "theme";
    public static final String EXTRA_ONLY = "only";
    public static final String EXTRA_RESTORE = "restore";
    public static final String EXTRA_DONE = "done";
    public static final String EXTRA_TOTAL = "total";
    public static final String EXTRA_PACKAGE = "package";
    public static final String EXTRA_FINISHED = "finished";
    public static final String EXTRA_RESULT = "result";
    private static final int NOTIFICATION_ID = 17589716;
    private static volatile boolean running;

    public static boolean isRunning() {
        return running;
    }

    public static void start(Context context, boolean apply, String themePackage) {
        Intent intent = new Intent(context, SystemAmoledService.class)
                .putExtra(EXTRA_APPLY, apply)
                .putExtra(EXTRA_THEME, themePackage);
        context.startForegroundService(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getBooleanExtra(EXTRA_RESTORE, false)) {
            startForeground(NOTIFICATION_ID, notification(
                    getString(R.string.system_amoled_restoring), 0, 0, true));
            new Thread(() -> {
                try {
                    SystemAmoled.restoreAll();
                } catch (Throwable t) {
                    Log.e("SubstratumAmoled", "restore failed", t);
                }
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            }).start();
            return START_NOT_STICKY;
        }
        boolean apply = intent == null || intent.getBooleanExtra(EXTRA_APPLY, true);
        String theme = intent != null ? intent.getStringExtra(EXTRA_THEME) : null;
        String only = intent != null ? intent.getStringExtra(EXTRA_ONLY) : null;
        startForeground(NOTIFICATION_ID, notification(
                getString(apply ? R.string.system_amoled_working : R.string.system_amoled_removing),
                0, 0, true));
        if (running) return START_NOT_STICKY;
        running = true;
        new Thread(() -> {
            String result;
            try {
                if (apply) {
                    AmoledRules rules = AmoledRules.defaults();
                    if (theme != null) {
                        try {
                            rules = AmoledRules.fromTheme(this, theme);
                        } catch (Exception e) {
                            Log.w("SubstratumAmoled", "Using default rules, " + theme + ": " + e);
                        }
                    }
                    int[] done = SystemAmoled.applyAll(this, rules, this::progress,
                            only == null ? null : java.util.Arrays.asList(only.split(",")));
                    result = getString(R.string.system_amoled_done, done[0], done[1]);
                    Log.i("SubstratumAmoled", "applied to " + done[0] + " apps, " + done[1] +
                            " colors");
                } else {
                    SystemAmoled.removeAll(this);
                    result = getString(R.string.system_amoled_removed);
                }
            } catch (Throwable t) {
                result = getString(R.string.system_amoled_failed);
                Log.e("SubstratumAmoled", "failed", t);
            }
            running = false;
            finish(result);
        }).start();
        return START_NOT_STICKY;
    }

    private void progress(String pkg, int index, int total) {
        String label;
        try {
            label = getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            label = pkg;
        }
        String text = getString(R.string.system_amoled_progress, index + 1, total, label);
        getSystemService(NotificationManager.class)
                .notify(NOTIFICATION_ID, notification(text, index + 1, total, false));
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(PROGRESS)
                .putExtra(EXTRA_DONE, index + 1).putExtra(EXTRA_TOTAL, total)
                .putExtra(EXTRA_PACKAGE, label));
    }

    private void finish(String result) {
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(PROGRESS)
                .putExtra(EXTRA_FINISHED, true).putExtra(EXTRA_RESULT, result));
        stopForeground(STOP_FOREGROUND_DETACH);
        Notification done = new NotificationCompat.Builder(this,
                References.DEFAULT_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.notification_icon)
                .setContentTitle(getString(R.string.system_amoled_title))
                .setContentText(result)
                .setAutoCancel(true)
                .build();
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, done);
        stopSelf();
    }

    private Notification notification(String text, int done, int total, boolean indeterminate) {
        return new NotificationCompat.Builder(this, References.ONGOING_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.notification_icon)
                .setContentTitle(getString(R.string.system_amoled_title))
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(total, done, indeterminate)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
