/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.common.platform;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import projekt.substratum.services.system.SystemAmoledService;

/**
 * Every change of "AMOLED for every app" is a job, run one after the other by
 * {@link SystemAmoledService} in the foreground. Nothing is dropped: a job started while another
 * runs waits for it.
 * <p>
 * Screens follow the queue with a {@link Listener} (called on the main thread) and
 * {@link #snapshot()}.
 */
public final class AmoledJobs {

    public static final String EXTRA_JOB = "job";
    public static final String EXTRA_THEME = "theme";
    public static final String EXTRA_PACKAGES = "packages";
    public static final String EXTRA_ON = "on";
    public static final String EXTRA_RESTART = "restart";
    public static final String EXTRA_AUTO = "auto";
    // Extras of older versions (SystemAmoledActivity passes its intent through)
    private static final String LEGACY_APPLY = "apply";
    private static final String LEGACY_ONLY = "only";
    private static final String LEGACY_RESTORE = "restore";
    private static final String LEGACY_RESTART_SYSTEMUI = "restart_systemui";
    private static final String SYSTEMUI = "com.android.systemui";

    private static final AtomicInteger IDS = new AtomicInteger();
    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final ConcurrentLinkedDeque<Job> WAITING = new ConcurrentLinkedDeque<>();
    private static final Map<String, String> LABELS = new ConcurrentHashMap<>();
    private static volatile Job current;
    private static Handler main;

    private AmoledJobs() {
    }

    public enum Kind {APPLY_ALL, APPLY_ONE, SET_ENABLED, REMOVE, RESTORE, APPLY_SYSTEMUI}

    public static final class Job {
        public final int id;
        public final Kind kind;
        /**
         * Rules theme (APPLY_ALL, APPLY_SYSTEMUI), or null for the built-in rules
         */
        public final String theme;
        /**
         * Packages (APPLY_ONE; SET_ENABLED has exactly one)
         */
        public final List<String> packages;
        /**
         * SET_ENABLED: on or off
         */
        public final boolean on;
        /**
         * APPLY_SYSTEMUI: restart System UI afterwards
         */
        public final boolean restart;
        /**
         * APPLY_ONE started on its own for apps updated since their colors were read
         * (AmoledWatchJob): it leaves its own notification instead of the usual result
         */
        public final boolean auto;

        Job(Kind kind, String theme, List<String> packages, boolean on, boolean restart) {
            this(kind, theme, packages, on, restart, false);
        }

        Job(Kind kind, String theme, List<String> packages, boolean on, boolean restart,
            boolean auto) {
            this.id = IDS.incrementAndGet();
            this.kind = kind;
            this.theme = theme;
            this.packages = Collections.unmodifiableList(new ArrayList<>(packages));
            this.on = on;
            this.restart = restart;
            this.auto = auto;
        }

        public String packageName() {
            return packages.isEmpty() ? null : packages.get(0);
        }

        @Override
        public String toString() {
            return kind + "#" + id + (packages.isEmpty() ? "" : " " + packages) +
                    (kind == Kind.SET_ENABLED ? (on ? " on" : " off") : "");
        }
    }

    /**
     * Called on the main thread. Every method has an empty default.
     */
    public interface Listener {
        default void onJobStarted(Job job) {
        }

        default void onProgress(Job job, String pkg, int done, int total) {
        }

        default void onJobFinished(Job job, boolean ok, String message) {
        }

        default void onQueueIdle() {
        }
    }

    public static final class Snapshot {
        /**
         * Running job, or null
         */
        public final Job current;
        /**
         * Jobs waiting behind it
         */
        public final int queued;

        Snapshot(Job current, int queued) {
            this.current = current;
            this.queued = queued;
        }

        public boolean isIdle() {
            return current == null && queued == 0;
        }
    }

    // Starting jobs

    /**
     * Apply to every covered app with a theme's rules (null: the built-in rules).
     */
    public static void applyAll(Context context, String theme) {
        start(context, Kind.APPLY_ALL, theme, Collections.emptyList(), false, false);
    }

    /**
     * Read the colors of some apps again, with the rules of the last apply. Only acts while AMOLED
     * is applied.
     */
    public static void applyOne(Context context, String... packages) {
        applyOne(context, Arrays.asList(packages));
    }

    public static void applyOne(Context context, Collection<String> packages) {
        start(context, Kind.APPLY_ONE, null, new ArrayList<>(packages), false, false);
    }

    /**
     * {@link #applyOne(Context, Collection)} for apps updated since their colors were read,
     * started on its own: a "Re-themed" notification afterwards, no usual result.
     */
    public static void applyUpdated(Context context, Collection<String> packages) {
        Intent intent = intent(context, Kind.APPLY_ONE)
                .putExtra(EXTRA_PACKAGES, packages.toArray(new String[0]))
                .putExtra(EXTRA_AUTO, true);
        context.getApplicationContext().startForegroundService(intent);
    }

    /**
     * Switch one app on or off. The switch is saved at once (an apply that is running already
     * respects it); the rest runs in the queue.
     */
    public static void setEnabled(Context context, String pkg, boolean on) {
        AmoledState.update(context, state -> state.app(pkg).userEnabled = on);
        start(context, Kind.SET_ENABLED, null, Collections.singletonList(pkg), on, false);
    }

    public static void remove(Context context) {
        start(context, Kind.REMOVE, null, Collections.emptyList(), false, false);
    }

    /**
     * Whether a {@link #remove} has something to take away: AMOLED is applied as the state file
     * says, or the module flag it leaves is still there. Blocking (files).
     */
    public static boolean isApplied(Context context) {
        if (new java.io.File(XposedBackend.DIR, XposedBackend.AMOLED_FLAG).exists()) return true;
        return AmoledState.exists(context) && AmoledState.read(context).enabled;
    }

    /**
     * Register the fabricated overlays again after a reboot (needs root, shows no UI).
     */
    public static void restore(Context context) {
        start(context, Kind.RESTORE, null, Collections.emptyList(), false, false);
    }

    /**
     * Read System UI's colors again (quick settings) and restart it.
     */
    public static void applySystemUi(Context context, String theme) {
        applySystemUi(context, theme, true);
    }

    public static void applySystemUi(Context context, String theme, boolean restart) {
        start(context, Kind.APPLY_SYSTEMUI, theme, Collections.singletonList(SYSTEMUI), false,
                restart);
    }

    /**
     * The intent that runs a job (for a PendingIntent.getForegroundService).
     */
    public static Intent intent(Context context, Kind kind) {
        return new Intent(context, SystemAmoledService.class).putExtra(EXTRA_JOB, kind.name());
    }

    private static void start(Context context, Kind kind, String theme, List<String> packages,
                              boolean on, boolean restart) {
        Intent intent = intent(context, kind)
                .putExtra(EXTRA_THEME, theme)
                .putExtra(EXTRA_PACKAGES, packages.toArray(new String[0]))
                .putExtra(EXTRA_ON, on)
                .putExtra(EXTRA_RESTART, restart);
        context.getApplicationContext().startForegroundService(intent);
    }

    /**
     * The job an intent asks for, including the extras of older versions, or null.
     */
    public static Job fromIntent(Intent intent) {
        if (intent == null) return null;
        String theme = intent.getStringExtra(EXTRA_THEME);
        String name = intent.getStringExtra(EXTRA_JOB);
        if (name != null) {
            Kind kind;
            try {
                kind = Kind.valueOf(name);
            } catch (IllegalArgumentException e) {
                return null;
            }
            String[] packages = intent.getStringArrayExtra(EXTRA_PACKAGES);
            List<String> list = packages == null ? Collections.emptyList() :
                    Arrays.asList(packages);
            if ((kind == Kind.APPLY_ONE || kind == Kind.SET_ENABLED) && list.isEmpty()) {
                return null;
            }
            if (kind == Kind.APPLY_SYSTEMUI) list = Collections.singletonList(SYSTEMUI);
            return new Job(kind, theme, list, intent.getBooleanExtra(EXTRA_ON, false),
                    intent.getBooleanExtra(EXTRA_RESTART, false),
                    kind == Kind.APPLY_ONE && intent.getBooleanExtra(EXTRA_AUTO, false));
        }
        if (intent.getBooleanExtra(LEGACY_RESTORE, false)) {
            return new Job(Kind.RESTORE, null, Collections.emptyList(), false, false);
        }
        if (!intent.getBooleanExtra(LEGACY_APPLY, true)) {
            return new Job(Kind.REMOVE, null, Collections.emptyList(), false, false);
        }
        String only = intent.getStringExtra(LEGACY_ONLY);
        if (only == null) {
            return new Job(Kind.APPLY_ALL, theme, Collections.emptyList(), false, false);
        }
        List<String> packages = new ArrayList<>();
        for (String pkg : only.split(",")) if (!pkg.trim().isEmpty()) packages.add(pkg.trim());
        boolean restart = intent.getBooleanExtra(LEGACY_RESTART_SYSTEMUI, false);
        if (packages.equals(Collections.singletonList(SYSTEMUI))) {
            return new Job(Kind.APPLY_SYSTEMUI, theme, packages, false, restart);
        }
        return packages.isEmpty() ? null :
                new Job(Kind.APPLY_ONE, theme, packages, false, false);
    }

    // Following the queue

    public static void addListener(Listener listener) {
        if (listener != null && !LISTENERS.contains(listener)) LISTENERS.add(listener);
    }

    public static void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }

    public static Snapshot snapshot() {
        return new Snapshot(current, WAITING.size());
    }

    public static boolean isBusy() {
        return !snapshot().isIdle();
    }

    /**
     * A newer job of the same kind and rules theme waits in the queue: for an APPLY_ALL, running
     * this one would only be redone by it.
     */
    public static boolean hasNewerWaiting(Job job) {
        for (Job other : WAITING) {
            if (other.kind == job.kind && other.id > job.id &&
                    java.util.Objects.equals(other.theme, job.theme)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The name of an app the queue reported progress for, or the package name.
     */
    public static String labelOf(String pkg) {
        String label = pkg != null ? LABELS.get(pkg) : null;
        return label != null ? label : pkg;
    }

    // Reported by SystemAmoledService

    /**
     * A job entered the queue.
     */
    public static void queued(Job job) {
        WAITING.add(job);
    }

    /**
     * A job left the queue and runs now (worker thread).
     */
    public static void started(Job job) {
        // Current first: a snapshot in between never reads as idle
        current = job;
        WAITING.remove(job);
        post(() -> {
            for (Listener l : LISTENERS) l.onJobStarted(job);
        });
    }

    /**
     * @param label The app's name, read on the worker thread: listeners get it from
     *              {@link #labelOf} without a PackageManager call on the main thread
     */
    public static void progress(Job job, String pkg, String label, int done, int total) {
        if (pkg != null && label != null) LABELS.put(pkg, label);
        post(() -> {
            for (Listener l : LISTENERS) l.onProgress(job, pkg, done, total);
        });
    }

    public static void finished(Job job, boolean ok, String message) {
        if (current == job) current = null;
        post(() -> {
            for (Listener l : LISTENERS) l.onJobFinished(job, ok, message);
        });
    }

    /**
     * The queue is empty (main thread).
     */
    public static void idle() {
        for (Listener l : LISTENERS) l.onQueueIdle();
    }

    private static synchronized void post(Runnable runnable) {
        if (main == null) main = new Handler(Looper.getMainLooper());
        main.post(runnable);
    }
}
