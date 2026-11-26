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
import android.os.PowerManager;
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
import projekt.substratum.common.platform.AmoledUserRules;
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
    private static final String PREF_SERVER = "reborn_amoled_server_instance";
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
    private static final String WAKE_LOCK_TAG = "Reborn:AmoledJob";
    /**
     * Safety limit of one acquire; every job acquires again, so only a job stuck this long loses it
     */
    private static final long WAKE_LOCK_TIMEOUT = 30L * 60L * 1000L;

    private final Handler main = new Handler(Looper.getMainLooper());
    private int lastStartId;
    private volatile String lastResult;
    private volatile Notification shown;
    /**
     * Held from the first queued job until the queue is idle: with the screen off the CPU would
     * otherwise suspend in the middle of an apply (and the job freezes until the screen wakes)
     */
    private PowerManager.WakeLock wakeLock;

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
        holdWakeLock();
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
        try {
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
        } finally {
            releaseWakeLock();
        }
    }

    @Override
    public void onDestroy() {
        releaseWakeLock();
        super.onDestroy();
    }

    /**
     * Keep the CPU running while jobs are queued. Not reference counted: every call (each queued
     * and each started job) renews the {@link #WAKE_LOCK_TIMEOUT}, one release ends it.
     */
    private synchronized void holdWakeLock() {
        try {
            if (wakeLock == null) {
                PowerManager power = getSystemService(PowerManager.class);
                if (power == null) return;
                wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG);
                wakeLock.setReferenceCounted(false);
            }
            wakeLock.acquire(WAKE_LOCK_TIMEOUT);
        } catch (RuntimeException e) {
            Log.w(TAG, "No wake lock: " + e);
        }
    }

    private synchronized void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (RuntimeException e) {
            Log.w(TAG, "Wake lock release failed: " + e);
        }
    }

    // Jobs (worker thread)

    private void run(AmoledJobs.Job job) {
        // A new safety period for this job
        holdWakeLock();
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
        if (ok) {
            // Whatever was registered now lives as long as this system server
            projekt.substratum.Substratum.getPreferences().edit()
                    .putString(PREF_SERVER, SystemAmoled.serverInstance()).apply();
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
        // A newer apply waits (the look was changed again): it would redo all of this
        if (AmoledJobs.hasNewerWaiting(job)) {
            Log.i(TAG, job + " superseded by a newer apply");
            return null;
        }
        AmoledRules rules = rules(job.theme);
        int[] done = SystemAmoled.applyAll(this, rules, (pkg, i, total) ->
                progress(job, pkg, i, total), null);
        SystemAmoled.markApplied(this, job.theme, rules);
        // Everything is registered again: an old "not restored" notice no longer applies
        getSystemService(NotificationManager.class).cancel(NO_ROOT_NOTIFICATION_ID);
        // The theme's quick settings layout, unless one was chosen in Substratum, and the
        // rules' track color (both from the next System UI start)
        QsOptions options = QsOptions.read();
        boolean write = options.trackColor != rules.qsTrack();
        options.trackColor = rules.qsTrack();
        if (rules.qsAspect > 0 && !projekt.substratum.Substratum.getPreferences()
                .getBoolean(AmoledRules.PREF_QS_CHOSEN, false)) {
            write |= options.aspect != rules.qsAspect;
            options.aspect = rules.qsAspect;
        }
        if (write) options.write(this);
        Log.i(TAG, "applied to " + done[0] + " apps, " + done[1] + " colors, look " +
                rules.level().key() + (rules.accent() != null ?
                String.format(" accent #%08x", rules.accent()) : ""));
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
        // The track color from the rules, before System UI starts again
        QsOptions options = QsOptions.read();
        if (options.trackColor != rules.qsTrack()) {
            options.trackColor = rules.qsTrack();
            options.write(this);
        }
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
        // Same system server as the last finished job: the overlays are still registered
        // (Android 15+ sends BOOT_COMPLETED again when the app is first opened after an update)
        String server = SystemAmoled.serverInstance();
        if (!server.isEmpty() && server.equals(
                projekt.substratum.Substratum.getPreferences().getString(PREF_SERVER, ""))) {
            Log.i(TAG, "restore: no reboot since the last job, nothing was lost");
            return true;
        }
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
        // The look chosen in Substratum (a wallpaper accent is read now), then the user's own
        // keep and pin rules
        return rules.withUserOptions(this, projekt.substratum.Substratum.getPreferences())
                .withUserRules(AmoledUserRules.read(this));
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
