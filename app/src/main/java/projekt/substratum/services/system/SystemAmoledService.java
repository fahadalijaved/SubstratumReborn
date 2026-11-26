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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import projekt.substratum.R;
import projekt.substratum.common.References;
import projekt.substratum.common.platform.AmoledEngine;
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
    /**
     * Doze disables wake locks: while jobs run, Substratum is on the temporary allowlist, for this
     * long, renewed every {@link #ALLOWLIST_RENEW} while the queue runs. The system caps a
     * temporary allowlist entry at max_temp_app_allowlist_duration_ms (5 minutes by default), so
     * a longer request lapses after 5 minutes: the renewal comes well before that.
     */
    private static final long ALLOWLIST_MS = 5L * 60L * 1000L;
    private static final long ALLOWLIST_RENEW = 3L * 60L * 1000L;
    /**
     * Progress notifications at most this often (a warm apply reports hundreds of apps a second)
     */
    private static final long PROGRESS_EVERY_MS = 250;

    private final Handler main = new Handler(Looper.getMainLooper());
    private int lastStartId;
    private volatile String lastResult;
    private volatile Notification shown;
    /**
     * Held from the first queued job until the queue is idle: with the screen off the CPU would
     * otherwise suspend in the middle of an apply (and the job freezes until the screen wakes)
     */
    private PowerManager.WakeLock wakeLock;
    private volatile long lastProgress;
    private final Runnable renewAllowlist = new Runnable() {
        @Override
        public void run() {
            if (PENDING.get() == 0) return;
            // The status root shell: it never waits behind the job
            new Thread(SystemAmoledService::allowWhileIdle, "AmoledAllowlist").start();
            // Uptime: it only advances while the allowlist keeps the wake lock working, so
            // each renewal lands before the entry lapses
            main.postDelayed(this, ALLOWLIST_RENEW);
        }
    };

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
        if (PENDING.incrementAndGet() == 1) {
            main.removeCallbacks(renewAllowlist);
            main.postDelayed(renewAllowlist, ALLOWLIST_RENEW);
        }
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
        main.removeCallbacks(renewAllowlist);
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
        main.removeCallbacks(renewAllowlist);
        releaseWakeLock();
        super.onDestroy();
    }

    /**
     * Doze turns off every wake lock, and an apply with the screen off froze until the screen
     * came on: Substratum goes on the temporary Doze allowlist (root, nothing permanent in the
     * battery settings). On the status root shell, so it never waits behind the job itself.
     */
    private static void allowWhileIdle() {
        try {
            if (!Root.checkRootAccess()) return;
            String out = Root.runQuickCommand("cmd deviceidle tempwhitelist -u 0 -d " +
                    ALLOWLIST_MS + " " + References.SUBSTRATUM_PACKAGE + " 2>&1");
            Log.i(TAG, "Doze allowlist for " + (ALLOWLIST_MS / 60000) + " min" +
                    (out != null && !out.trim().isEmpty() ? ": " + out.trim() : ""));
        } catch (RuntimeException e) {
            Log.w(TAG, "No Doze allowlist: " + e);
        }
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
        if (job.kind != AmoledJobs.Kind.RESTORE) allowWhileIdle();
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
                    // Nothing applied: no update check
                    AmoledWatchJob.schedule(this);
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
        // Long jobs leave their result as a notification; switches, restores and silent
        // re-themes after updates do not
        if (result != null && job.kind != AmoledJobs.Kind.SET_ENABLED &&
                job.kind != AmoledJobs.Kind.RESTORE && !job.auto) {
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
        long start = android.os.SystemClock.elapsedRealtime();
        int[] done = SystemAmoled.applyAll(this, rules, (pkg, i, total) ->
                progress(job, pkg, i, total), null);
        long elapsed = android.os.SystemClock.elapsedRealtime() - start;
        SystemAmoled.markApplied(this, job.theme, rules);
        // Updated apps are re-themed on their own from now on
        AmoledWatchJob.applied(this);
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
        return done(done, elapsed);
    }

    /**
     * "AMOLED applied to N apps (M colors) in X.X s"
     */
    private String done(int[] done, long elapsed) {
        return getString(R.string.system_amoled_done_timed, done[0], done[1],
                String.format(Locale.getDefault(), "%.1f", elapsed / 1000.0));
    }

    private String applyOne(AmoledJobs.Job job) {
        AmoledState state = AmoledState.read(this);
        if (!state.enabled) return getString(R.string.system_amoled_saved_off);
        AmoledRules rules = rules(job.theme != null ? job.theme : state.rulesTheme);
        long start = android.os.SystemClock.elapsedRealtime();
        // A silent re-theme after updates never closes the app in use (gentle)
        int[] done = SystemAmoled.applyAll(this, rules, (pkg, i, total) ->
                progress(job, pkg, i, total), job.packages, job.auto);
        long elapsed = android.os.SystemClock.elapsedRealtime() - start;
        Log.i(TAG, "applied to " + job.packages + ": " + done[1] + " colors");
        if (job.auto) {
            // "Re-themed Play Store and 2 more after updates", for the apps that got colors
            AmoledState after = AmoledState.read(this);
            List<String> labels = new ArrayList<>();
            for (String pkg : job.packages) {
                AmoledState.App app = after.perApp.get(pkg);
                if (app != null && app.colors > 0 && (app.route == AmoledState.Route.OVERLAY ||
                        app.route == AmoledState.Route.MODULE)) {
                    labels.add(label(pkg));
                }
            }
            Log.i(AmoledEngine.TAG, "re-themed after updates: " + labels);
            AmoledWatchJob.notifyRethemed(this, labels);
        }
        return done(done, elapsed);
    }

    /**
     * Quick settings colors: System UI only, then a restart so every part reloads. Only while
     * AMOLED is applied (the tile choice is already saved and applies with AMOLED).
     */
    private String applySystemUi(AmoledJobs.Job job) {
        AmoledState state = AmoledState.read(this);
        if (!state.enabled) return getString(R.string.system_amoled_saved_off);
        AmoledRules rules = rules(job.theme != null ? job.theme : state.rulesTheme);
        long start = android.os.SystemClock.elapsedRealtime();
        int[] done = SystemAmoled.applyAll(this, rules, (pkg, i, total) ->
                progress(job, pkg, i, total), job.packages);
        long elapsed = android.os.SystemClock.elapsedRealtime() - start;
        // The track color from the rules, before System UI starts again
        QsOptions options = QsOptions.read();
        if (options.trackColor != rules.qsTrack()) {
            options.trackColor = rules.qsTrack();
            options.write(this);
        }
        if (job.restart) QsOptions.restartSystemUi();
        return done(done, elapsed);
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
            checkUpdated();
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
        checkUpdated();
        return true;
    }

    /**
     * After a restore: apps updated meanwhile (or while the phone was off) are read again, and the
     * periodic check is scheduled.
     */
    private void checkUpdated() {
        try {
            AmoledWatchJob.schedule(this);
            AmoledWatchJob.check(this, "boot");
        } catch (RuntimeException e) {
            Log.w(TAG, "update check failed: " + e);
        }
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
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastProgress >= PROGRESS_EVERY_MS || index + 1 >= total) {
            lastProgress = now;
            String text = getString(R.string.system_amoled_progress, index + 1, total, label);
            show(ongoing(text, index + 1, total, false));
        }
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
