/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.services.system;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ChangedPackages;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.pm.PackageInfoCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import projekt.substratum.R;
import projekt.substratum.Substratum;
import projekt.substratum.common.References;
import projekt.substratum.common.platform.AmoledEngine;
import projekt.substratum.common.platform.AmoledJobs;
import projekt.substratum.common.platform.AmoledRules;
import projekt.substratum.common.platform.AmoledState;
import projekt.substratum.common.platform.XposedBackend;
import projekt.substratum.util.helpers.Root;

/**
 * Re-themes apps that were updated, without hooks (a manifest receiver for PACKAGE_REPLACED never
 * fires for an app targeting API 28 in the background). A periodic job (about every
 * {@value #PERIOD_MINUTES} minutes, kept across reboots) runs {@link #check}, which also runs when
 * the app opens and after the restore at boot:
 * <ul>
 * <li>apps with AMOLED colors read for another version than the installed one, and covered apps
 * with an activity installed since the last check ({@link PackageManager#getChangedPackages}),
 * are read again with one silent APPLY_ONE job, which leaves one notification: "Re-themed Play
 * Store and 2 more after updates";</li>
 * <li>after a system update (another {@link Build#FINGERPRINT} than at the last apply) nothing is
 * read on its own: one notification offers to re-apply everything.</li>
 * </ul>
 * The Studio's "Auto-theme updated apps" switch ({@link #PREF_AUTO}, on by default) turns it off.
 */
public class AmoledWatchJob extends JobService {

    public static final int JOB_ID = 17589720;
    public static final String PREF_AUTO = "amoled_auto_theme";
    static final int UPDATED_NOTIFICATION_ID = 17589721;
    static final int OTA_NOTIFICATION_ID = 17589722;
    private static final String PREF_SEQUENCE = "amoled_watch_sequence";
    /**
     * The boot the sequence belongs to: package change sequences start again at every boot
     */
    private static final String PREF_SEQUENCE_BOOT = "amoled_watch_sequence_boot";
    private static final String PREF_FINGERPRINT = "amoled_applied_fingerprint";
    private static final String PREF_OTA_NOTIFIED = "amoled_ota_notified";
    private static final String PREF_TRIED = "amoled_watch_tried";
    private static final String TAG = AmoledEngine.TAG;
    private static final long PERIOD_MINUTES = 60;
    private static final int MAX_TRIED = 300;

    @Override
    public boolean onStartJob(JobParameters params) {
        Thread thread = new Thread(() -> {
            try {
                check(getApplicationContext(), "job");
            } catch (Throwable t) {
                Log.e(TAG, "update check failed", t);
            } finally {
                jobFinished(params, false);
            }
        }, "AmoledWatch");
        thread.start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }

    // Scheduling

    public static boolean isOn() {
        SharedPreferences prefs = Substratum.getPreferences();
        return prefs == null || prefs.getBoolean(PREF_AUTO, true);
    }

    /**
     * Turn the automatic re-theme on or off (the Studio's switch).
     */
    public static void setOn(Context context, boolean on) {
        SharedPreferences prefs = Substratum.getPreferences();
        if (prefs != null) prefs.edit().putBoolean(PREF_AUTO, on).apply();
        schedule(context);
    }

    /**
     * Schedule the periodic check while AMOLED is applied and the switch is on; cancel it
     * otherwise. Reads the state file.
     */
    public static void schedule(Context context) {
        JobScheduler jobs = context.getSystemService(JobScheduler.class);
        if (jobs == null) return;
        try {
            if (!isOn() || !AmoledState.exists(context) || !AmoledState.read(context).enabled) {
                jobs.cancel(JOB_ID);
                return;
            }
            if (jobs.getPendingJob(JOB_ID) != null) return;
            jobs.schedule(new JobInfo.Builder(JOB_ID,
                    new ComponentName(context, AmoledWatchJob.class))
                    .setPeriodic(PERIOD_MINUTES * 60_000L, 15 * 60_000L)
                    .setPersisted(true)
                    .build());
            Log.i(TAG, "update check scheduled every " + PERIOD_MINUTES + " min (job " + JOB_ID +
                    ")");
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not schedule the update check: " + e);
        }
    }

    /**
     * After an apply of every app: remember the system build and where the package changes
     * stand, and schedule the check.
     */
    public static void applied(Context context) {
        SharedPreferences prefs = Substratum.getPreferences();
        if (prefs != null) {
            SharedPreferences.Editor edit = prefs.edit()
                    .putString(PREF_FINGERPRINT, Build.FINGERPRINT)
                    .remove(PREF_OTA_NOTIFIED)
                    .remove(PREF_TRIED);
            try {
                ChangedPackages changes = context.getPackageManager().getChangedPackages(0);
                edit.putInt(PREF_SEQUENCE, changes != null ? changes.getSequenceNumber() : 0)
                        .putInt(PREF_SEQUENCE_BOOT, AmoledState.currentBootCount(context));
            } catch (RuntimeException ignored) {
                // read again at the next check
            }
            edit.apply();
        }
        context.getSystemService(NotificationManager.class).cancel(OTA_NOTIFICATION_ID);
        schedule(context);
    }

    // The check

    /**
     * Queue a silent re-theme of the apps updated or installed since their colors were read, or
     * offer a re-apply after a system update. Blocking (files, the package manager).
     *
     * @param why For the log: job, open, boot
     * @return The apps queued
     */
    public static synchronized List<String> check(Context context, String why) {
        SharedPreferences prefs = Substratum.getPreferences();
        if (prefs == null || !prefs.getBoolean(PREF_AUTO, true)) return Collections.emptyList();
        if (!AmoledState.exists(context)) return Collections.emptyList();
        AmoledState state = AmoledState.read(context);
        if (!state.enabled) return Collections.emptyList();
        // A job running now may be reading these apps already: the next check sees its result
        // (the restore at boot calls this from its own job)
        if (!"boot".equals(why) && AmoledJobs.isBusy()) return Collections.emptyList();
        String applied = prefs.getString(PREF_FINGERPRINT, null);
        if (applied == null) {
            // Applied by a version that did not remember the build: from now on
            prefs.edit().putString(PREF_FINGERPRINT, Build.FINGERPRINT).apply();
        } else if (!applied.equals(Build.FINGERPRINT)) {
            if (!Build.FINGERPRINT.equals(prefs.getString(PREF_OTA_NOTIFIED, null))) {
                notifySystemUpdate(context, state.rulesTheme);
                prefs.edit().putString(PREF_OTA_NOTIFIED, Build.FINGERPRINT).apply();
            }
            Log.i(TAG, "system updated since the last apply: nothing re-themed on its own");
            return Collections.emptyList();
        }
        PackageManager pm = context.getPackageManager();
        // Re-themes already queued, as "pkg|read version>installed version": one that fails is
        // not queued again every hour for the same versions
        Set<String> tried = new LinkedHashSet<>(prefs.getStringSet(PREF_TRIED,
                Collections.emptySet()));
        Set<String> triedBefore = new HashSet<>(tried);
        List<String> queue = new ArrayList<>();
        // Updated: AMOLED colors read for another version
        for (Map.Entry<String, AmoledState.App> entry : state.perApp.entrySet()) {
            String pkg = entry.getKey();
            AmoledState.App app = entry.getValue();
            if (!app.userEnabled || app.versionKey == null) continue;
            if (app.route != AmoledState.Route.OVERLAY && app.route != AmoledState.Route.MODULE) {
                continue;
            }
            String key = versionKey(pm, pkg, 0);
            if (key == null) continue; // removed: the next apply cleans it up
            if (key.equals(app.versionKey)) {
                // Up to date (re-themed): nothing to remember
                tried.removeIf(t -> t.startsWith(pkg + '|'));
            } else if (tried.add(pkg + '|' + app.versionKey + '>' + key)) {
                queue.add(pkg);
            }
        }
        // New: covered apps with an activity AMOLED has never read
        int boot = AmoledState.currentBootCount(context);
        int sequence = prefs.getInt(PREF_SEQUENCE_BOOT, -1) == boot ?
                prefs.getInt(PREF_SEQUENCE, 0) : 0;
        ChangedPackages changes = null;
        try {
            changes = pm.getChangedPackages(sequence);
        } catch (RuntimeException e) {
            Log.w(TAG, "No package changes: " + e);
        }
        if (changes != null) {
            AmoledRules rules = null;
            for (String pkg : changes.getPackageNames()) {
                if (state.perApp.containsKey(pkg) || queue.contains(pkg)) continue;
                if (rules == null) rules = AmoledRules.effective(context, state.rulesTheme, prefs);
                if (!rules.covers(pkg)) continue;
                PackageInfo info;
                try {
                    info = pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES);
                } catch (PackageManager.NameNotFoundException | RuntimeException e) {
                    continue; // removed
                }
                if (info.activities == null || info.activities.length == 0) continue;
                String key = key(info);
                if (key != null && tried.add(pkg + "|new>" + key)) queue.add(pkg);
            }
        }
        if (!queue.isEmpty() && !Root.checkRootAccess()) {
            // Nothing remembered: queued once root is back
            Log.i(TAG, "updated apps " + queue + " wait for root");
            return Collections.emptyList();
        }
        SharedPreferences.Editor edit = prefs.edit().putInt(PREF_SEQUENCE_BOOT, boot);
        if (changes != null) edit.putInt(PREF_SEQUENCE, changes.getSequenceNumber());
        else if (sequence == 0) edit.putInt(PREF_SEQUENCE, 0);
        if (!tried.equals(triedBefore)) {
            List<String> kept = new ArrayList<>(tried);
            if (kept.size() > MAX_TRIED) kept = kept.subList(kept.size() - MAX_TRIED, kept.size());
            edit.putStringSet(PREF_TRIED, new HashSet<>(kept));
        }
        edit.apply();
        if (!queue.isEmpty()) {
            Log.i(TAG, "re-theming after updates (" + why + "): " + queue);
            AmoledJobs.applyUpdated(context, queue);
        }
        return queue;
    }

    private static String versionKey(PackageManager pm, String pkg, int flags) {
        try {
            return key(pm.getPackageInfo(pkg, flags));
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return null;
        }
    }

    private static String key(PackageInfo info) {
        if (info.applicationInfo == null) return null;
        return XposedBackend.versionKey(info.applicationInfo.sourceDir,
                PackageInfoCompat.getLongVersionCode(info));
    }

    // Notifications

    /**
     * "Re-themed Play Store and 2 more after updates", for the apps a silent re-theme gave colors.
     */
    static void notifyRethemed(Context context, List<String> labels) {
        if (labels.isEmpty()) return;
        String text = labels.size() == 1 ?
                context.getString(R.string.amoled_auto_rethemed_one, labels.get(0)) :
                context.getString(R.string.amoled_auto_rethemed_more, labels.get(0),
                        labels.size() - 1);
        context.getSystemService(NotificationManager.class).notify(UPDATED_NOTIFICATION_ID,
                new NotificationCompat.Builder(context, References.DEFAULT_NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(R.drawable.notification_icon)
                        .setContentTitle(context.getString(R.string.system_amoled_title))
                        .setContentText(text)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                        .setContentIntent(openStudio(context))
                        .setAutoCancel(true)
                        .build());
    }

    private static void notifySystemUpdate(Context context, String theme) {
        PendingIntent reapply = PendingIntent.getForegroundService(context, 1,
                AmoledJobs.intent(context, AmoledJobs.Kind.APPLY_ALL)
                        .putExtra(AmoledJobs.EXTRA_THEME, theme),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String text = context.getString(R.string.amoled_ota_text);
        context.getSystemService(NotificationManager.class).notify(OTA_NOTIFICATION_ID,
                new NotificationCompat.Builder(context, References.DEFAULT_NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(R.drawable.notification_icon)
                        .setContentTitle(context.getString(R.string.amoled_ota_title))
                        .setContentText(text)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                        .setContentIntent(openStudio(context))
                        .addAction(0, context.getString(R.string.amoled_ota_reapply), reapply)
                        .setAutoCancel(true)
                        .build());
    }

    private static PendingIntent openStudio(Context context) {
        Intent open = new Intent(context, projekt.substratum.reborn.RebornActivity.class)
                .putExtra(projekt.substratum.reborn.RebornActivity.EXTRA_TAB, "amoled")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, 2, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
