/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.services.system;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import projekt.substratum.R;
import projekt.substratum.common.References;
import projekt.substratum.common.platform.AmoledJobs;
import projekt.substratum.common.platform.AmoledRules;
import projekt.substratum.common.platform.AmoledState;
import projekt.substratum.common.platform.QsOptions;
import projekt.substratum.common.platform.SystemAmoled;
import projekt.substratum.util.helpers.Root;

/**
 * Runs the jobs of "AMOLED for every app" ({@link AmoledJobs}) one after the other in the
 * foreground, with a progress notification. Every start adds a job to one queue; when the queue
 * is empty the service stops. Progress also goes out as the local broadcast {@link #PROGRESS}.
 */
public class SystemAmoledService extends Service {

    public static final String PROGRESS = "projekt.substratum.AMOLED_PROGRESS";
    public static final String EXTRA_APPLY = "apply";
    public static final String EXTRA_THEME = "theme";
    public static final String EXTRA_ONLY = "only";
    public static final String EXTRA_RESTORE = "restore";
    public static final String EXTRA_RESTART_SYSTEMUI = "restart_systemui";
    public static final String EXTRA_DONE = "done";
    public static final String EXTRA_TOTAL = "total";
    public static final String EXTRA_PACKAGE = "package";
    public static final String EXTRA_FINISHED = "finished";
    public static final String EXTRA_RESULT = "result";
    private static final String TAG = "SubstratumAmoled";
    private static final int NOTIFICATION_ID = 17589716;
    private static final int NO_ROOT_NOTIFICATION_ID = 17589717;
    private static final ExecutorService QUEUE = Executors.newSingleThreadExecutor(
            r -> new Thread(r, "AmoledJobs"));
    private static final AtomicInteger PENDING = new AtomicInteger();

    private final Handler main = new Handler(Looper.getMainLooper());
    private int lastStartId;
    private volatile String lastResult;
    private volatile Notification shown;

    /**
     * @deprecated Use {@link AmoledJobs#snapshot()}
     */
    @Deprecated
    public static boolean isRunning() {
        return AmoledJobs.isBusy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        lastStartId = startId;
        AmoledJobs.Job job = AmoledJobs.fromIntent(intent);
        // Every start has to be foreground; a running job keeps its notification
        Notification current = shown;
        startForeground(NOTIFICATION_ID, current != null && AmoledJobs.isBusy() ? current :
                ongoing(job == null ? getString(R.string.system_amoled_working) :
                        startText(job), 0, 0, true));
        if (job == null) {
            Log.w(TAG, "No job in " + intent);
            if (PENDING.get() == 0) stopIfIdle();
            return START_NOT_STICKY;
        }
        AmoledJobs.queued(job);
        PENDING.incrementAndGet();
        QUEUE.execute(() -> {
            try {
                run(job);
            } finally {
                if (PENDING.decrementAndGet() == 0) main.post(this::stopIfIdle);
            }
        });
        return START_NOT_STICKY;
    }

    /**
     * Main thread: nothing is queued (a start in between would have been counted), so stop.
     */
    private void stopIfIdle() {
        if (PENDING.get() != 0) return;
        AmoledJobs.idle();
        String result = lastResult;
        lastResult = null;
        shown = null;
        if (result != null) {
            stopForeground(STOP_FOREGROUND_DETACH);
            Notification done = new NotificationCompat.Builder(this,
                    References.DEFAULT_NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.notification_icon)
                    .setContentTitle(getString(R.string.system_amoled_title))
                    .setContentText(result)
                    .setAutoCancel(true)
                    .build();
            getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, done);
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE);
        }
        stopSelf(lastStartId);
    }

    // Jobs (worker thread)

    private void run(AmoledJobs.Job job) {
        AmoledJobs.started(job);
        show(ongoing(startText(job), 0, 0, true));
        boolean ok = true;
        String result;
        try {
            switch (job.kind) {
                case APPLY_ALL:
                    result = applyAll(job);
                    break;
                case APPLY_ONE:
                    result = applyOne(job);
                    break;
                case SET_ENABLED:
                    result = setEnabled(job);
                    break;
                case REMOVE:
                    SystemAmoled.removeAll(this);
                    // The module's System UI hooks (solid shade, dark tracks) unload with it
                    QsOptions.restartSystemUi();
                    result = getString(R.string.system_amoled_removed);
                    break;
                case RESTORE:
                    ok = restore();
                    result = ok ? null : getString(R.string.system_amoled_restore_no_root);
                    break;
                case APPLY_SYSTEMUI:
                    result = applySystemUi(job);
                    break;
                default:
                    result = null;
                    break;
            }
        } catch (Throwable t) {
            ok = false;
            result = getString(R.string.system_amoled_failed);
            Log.e(TAG, job + " failed", t);
        }
        // Long jobs leave their result as a notification; switches and restores do not
        if (result != null && job.kind != AmoledJobs.Kind.SET_ENABLED &&
                job.kind != AmoledJobs.Kind.RESTORE) {
            lastResult = result;
        }
        AmoledJobs.finished(job, ok, result);
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(PROGRESS)
                .putExtra(EXTRA_FINISHED, true).putExtra(EXTRA_RESULT, result));
    }

    private String applyAll(AmoledJobs.Job job) {
        AmoledRules rules = rules(job.theme);
        int[] done = SystemAmoled.applyAll(this, rules, (pkg, i, total) ->
                progress(job, pkg, i, total), null);
        SystemAmoled.markApplied(this, job.theme);
        // Everything is registered again: an old "not restored" notice no longer applies
        getSystemService(NotificationManager.class).cancel(NO_ROOT_NOTIFICATION_ID);
        // The theme's quick settings layout, unless one was chosen in Substratum
        if (rules.qsAspect > 0 && !projekt.substratum.Substratum.getPreferences()
                .getBoolean(AmoledRules.PREF_QS_CHOSEN, false)) {
            QsOptions options = QsOptions.read();
            options.aspect = rules.qsAspect;
            options.write(this);
        }
        Log.i(TAG, "applied to " + done[0] + " apps, " + done[1] + " colors");
        return getString(R.string.system_amoled_done, done[0], done[1]);
    }

    private String applyOne(AmoledJobs.Job job) {
        AmoledState state = AmoledState.read(this);
        if (!state.enabled) return getString(R.string.system_amoled_saved_off);
        AmoledRules rules = rules(job.theme != null ? job.theme : state.rulesTheme);
        int[] done = SystemAmoled.applyAll(this, rules, (pkg, i, total) ->
                progress(job, pkg, i, total), job.packages);
        Log.i(TAG, "applied to " + job.packages + ": " + done[1] + " colors");
        return getString(R.string.system_amoled_done, done[0], done[1]);
    }

    /**
     * Quick settings colors: System UI only, then a restart so every part reloads. Only while
     * AMOLED is applied (the tile choice is already saved and applies with AMOLED).
     */
    private String applySystemUi(AmoledJobs.Job job) {
        AmoledState state = AmoledState.read(this);
        if (!state.enabled) return getString(R.string.system_amoled_saved_off);
        AmoledRules rules = rules(job.theme != null ? job.theme : state.rulesTheme);
        int[] done = SystemAmoled.applyAll(this, rules, (pkg, i, total) ->
                progress(job, pkg, i, total), job.packages);
        if (job.restart) QsOptions.restartSystemUi();
        return getString(R.string.system_amoled_done, done[0], done[1]);
    }

    private String setEnabled(AmoledJobs.Job job) {
        String pkg = job.packageName();
        // Flipped back since (a later job of the queue carries the last choice): doing this one
        // would only restart the app twice and briefly undo the saved switch
        if (AmoledState.read(this).isUserEnabled(pkg) != job.on) {
            Log.i(TAG, job + " superseded by a later switch");
            return null;
        }
        String label = label(pkg);
        switch (SystemAmoled.setAppEnabled(this, pkg, job.on)) {
            case SAVED:
                return getString(R.string.system_amoled_saved_off);
            case RESTART_NEEDED:
                return getString(job.on ? R.string.amoled_app_switched_on_restart :
                        R.string.amoled_app_switched_off_restart, label);
            case REAPPLY:
                return getString(R.string.amoled_app_switched_on_reapply, label);
            case ON:
                return getString(R.string.amoled_app_switched_on, label);
            case OFF:
            default:
                return getString(R.string.amoled_app_switched_off, label);
        }
    }

    /**
     * Never shows UI: without root, a notification offers to try again.
     *
     * @return False if root was not available
     */
    private boolean restore() {
        NotificationManager notifications = getSystemService(NotificationManager.class);
        // Nothing applied: no root needed, and no "not restored" notification at every boot
        if (!SystemAmoled.hasRestorable(this)) {
            Log.i(TAG, "restore: nothing to restore");
            notifications.cancel(NO_ROOT_NOTIFICATION_ID);
            return true;
        }
        if (!Root.checkRootAccess() || !Root.isAlive()) {
            Log.w(TAG, "restore: root not available");
            PendingIntent retry = PendingIntent.getForegroundService(this, 0,
                    AmoledJobs.intent(this, AmoledJobs.Kind.RESTORE),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            notifications.notify(NO_ROOT_NOTIFICATION_ID, new NotificationCompat.Builder(this,
                    References.DEFAULT_NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.notification_icon)
                    .setContentTitle(getString(R.string.system_amoled_title))
                    .setContentText(getString(R.string.system_amoled_restore_no_root))
                    .addAction(0, getString(R.string.system_amoled_try_again), retry)
                    .setAutoCancel(true)
                    .build());
            return false;
        }
        notifications.cancel(NO_ROOT_NOTIFICATION_ID);
        SystemAmoled.restoreAll(this);
        return true;
    }

    private AmoledRules rules(String theme) {
        AmoledRules rules = AmoledRules.defaults();
        if (theme != null) {
            try {
                rules = AmoledRules.fromTheme(this, theme);
            } catch (Exception e) {
                Log.w(TAG, "Using default rules, " + theme + ": " + e);
            }
        }
        return rules.withUserOptions(projekt.substratum.Substratum.getPreferences());
    }

    private void progress(AmoledJobs.Job job, String pkg, int index, int total) {
        String label = label(pkg);
        String text = getString(R.string.system_amoled_progress, index + 1, total, label);
        show(ongoing(text, index + 1, total, false));
        AmoledJobs.progress(job, pkg, label, index + 1, total);
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(PROGRESS)
                .putExtra(EXTRA_DONE, index + 1).putExtra(EXTRA_TOTAL, total)
                .putExtra(EXTRA_PACKAGE, label));
    }

    private String label(String pkg) {
        if (pkg == null) return "";
        try {
            return getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    private String startText(AmoledJobs.Job job) {
        switch (job.kind) {
            case REMOVE:
                return getString(R.string.system_amoled_removing);
            case RESTORE:
                return getString(R.string.system_amoled_restoring);
            case SET_ENABLED:
                return getString(R.string.amoled_app_switching, label(job.packageName()));
            default:
                return getString(R.string.system_amoled_working);
        }
    }

    private void show(Notification notification) {
        shown = notification;
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification);
    }

    private Notification ongoing(String text, int done, int total, boolean indeterminate) {
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
