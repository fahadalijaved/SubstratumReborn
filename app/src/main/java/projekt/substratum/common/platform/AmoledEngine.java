/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.common.platform;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.SystemClock;
import android.util.Log;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import projekt.substratum.Substratum;
import projekt.substratum.common.platform.AmoledState.Route;

/**
 * The second AMOLED engine ("v2", the default; the classic one stays selectable with the
 * preference {@link #PREF_ENGINE}): the same colors as the classic engine, read and registered
 * faster and without flicker.
 * <ul>
 * <li>Stock colors are read without disabling the app's overlay ({@link AmoledStock}); only the
 * framework ({@code android}) is still read the classic way, first and on its own, so every app
 * reads the framework as it is applied.</li>
 * <li>Only apps with an activity are read, plus the framework, System UI, keyboards and widget
 * providers (and every app applied before, to keep it up to date or clean it up).</li>
 * <li>Each app's result is cached ({@link AmoledCache}): an app that did not change is not read
 * again, and not registered again when its overlay or module list is already in place.</li>
 * <li>Colors are read by {@value #THREADS} threads, each with its own resources; registering
 * stays serialized, in batches of up to {@value #BATCH} apps with one root call each
 * ({@link AmoledCommit}).</li>
 * <li>Colors a shell overlay may not change (no {@code <overlayable>} declaration in an app
 * signed differently from the shell: {@link ArscOverlayable}) are left out of the overlay up
 * front, so idmap2 no longer refuses it; the retry with idmap2's log only remains as a fallback
 * for overlays still refused.</li>
 * </ul>
 */
public final class AmoledEngine {

    public static final String TAG = "RebornAmoled";
    public static final String PREF_ENGINE = "amoled_engine";
    public static final String ENGINE_CLASSIC = "v1";
    public static final String ENGINE_V2 = "v2";
    static final int THREADS = 4;
    static final int BATCH = 16;
    private static final String SHELL = "com.android.shell";
    private static final String ANDROID = "android";
    private static final String SYSTEMUI = "com.android.systemui";
    /**
     * What people look at most goes first
     */
    private static final List<String> FIRST = Arrays.asList(ANDROID, SYSTEMUI,
            "com.android.settings", "com.samsung.android.honeyboard", "com.android.vending");

    private AmoledEngine() {
    }

    // The engine switch

    /**
     * Whether applies use this engine (true unless the classic one was chosen).
     */
    public static boolean isEnabled() {
        SharedPreferences prefs = Substratum.getPreferences();
        return prefs == null || !ENGINE_CLASSIC.equals(prefs.getString(PREF_ENGINE, ENGINE_V2));
    }

    public static void setClassic(boolean classic) {
        SharedPreferences prefs = Substratum.getPreferences();
        if (prefs != null) {
            prefs.edit().putString(PREF_ENGINE, classic ? ENGINE_CLASSIC : ENGINE_V2).apply();
        }
    }

    /**
     * The timing line of an apply:
     * {@code applied N apps (M colours) in X.Xs (cold|warm, K cached)}
     */
    public static String summary(int apps, int colors, long millis, int cached, int processed) {
        return String.format(Locale.ROOT, "applied %d apps (%d colours) in %.1fs (%s, %d cached)",
                apps, colors, millis / 1000.0, warm(cached, processed) ? "warm" : "cold", cached);
    }

    /**
     * Most apps came from the cache
     */
    public static boolean warm(int cached, int processed) {
        return cached > 0 && cached * 2 >= processed;
    }

    // Planning

    /**
     * The apps to read: covered apps with an activity, the framework, System UI, input methods and
     * widget providers, then every app applied before that is not among them; the most looked at
     * first.
     */
    static List<String> targets(Context context, AmoledRules rules) {
        PackageManager pm = context.getPackageManager();
        Set<String> list = new LinkedHashSet<>();
        List<PackageInfo> installed;
        try {
            installed = pm.getInstalledPackages(PackageManager.GET_ACTIVITIES);
        } catch (RuntimeException e) {
            Log.w(TAG, "Package list with activities failed, reading one by one: " + e);
            installed = new ArrayList<>();
            for (PackageInfo info : pm.getInstalledPackages(0)) {
                if (!rules.covers(info.packageName)) continue;
                try {
                    installed.add(pm.getPackageInfo(info.packageName,
                            PackageManager.GET_ACTIVITIES));
                } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
                    // gone, or too large: skipped
                }
            }
        }
        for (PackageInfo info : installed) {
            String pkg = info.packageName;
            if (!rules.covers(pkg) || SystemAmoled.isOverlay(info)) continue;
            if (info.activities != null && info.activities.length > 0) list.add(pkg);
        }
        Set<String> extra = new LinkedHashSet<>(Arrays.asList(ANDROID, SYSTEMUI));
        try {
            InputMethodManager imm = context.getSystemService(InputMethodManager.class);
            if (imm != null) {
                for (InputMethodInfo ime : imm.getInputMethodList()) {
                    extra.add(ime.getPackageName());
                }
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "No input methods: " + e);
        }
        try {
            for (AppWidgetProviderInfo widget :
                    AppWidgetManager.getInstance(context).getInstalledProviders()) {
                if (widget.provider != null) extra.add(widget.provider.getPackageName());
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "No widget providers: " + e);
        }
        for (String pkg : extra) {
            if (rules.covers(pkg) && SystemAmoled.installed(context, pkg)) list.add(pkg);
        }
        List<String> targets = new ArrayList<>(list);
        // Applied before: kept up to date, or cleaned up when gone or no longer covered
        for (Map.Entry<String, AmoledState.App> entry :
                AmoledState.read(context).perApp.entrySet()) {
            if (entry.getValue().route != Route.NONE && !list.contains(entry.getKey())) {
                targets.add(entry.getKey());
            }
        }
        targets.sort((a, b) -> {
            int ia = FIRST.indexOf(a), ib = FIRST.indexOf(b);
            return Integer.compare(ia < 0 ? FIRST.size() : ia, ib < 0 ? FIRST.size() : ib);
        });
        return targets;
    }

    /**
     * The apps an apply with these rules would change: not cached for their installed version and
     * these rules, or cached but not in place (their overlay is not enabled, their module list
     * differs), or gone. For "Re-apply changed apps only". Blocking (files, the package manager
     * and one overlay list on the status root shell).
     */
    public static List<String> changedPackages(Context context, String theme) {
        AmoledRules rules = AmoledRules.effective(context, theme, Substratum.getPreferences());
        String rulesHash = AmoledCache.rulesHash(rules);
        AmoledState state = AmoledState.read(context);
        Map<String, Character> overlays = SystemAmoled.overlayStates(true);
        PackageManager pm = context.getPackageManager();
        List<String> changed = new ArrayList<>();
        for (String pkg : targets(context, rules)) {
            if (!state.isUserEnabled(pkg)) continue;
            AmoledState.App app = state.perApp.get(pkg);
            boolean applied = app != null && app.route != Route.NONE;
            PackageInfo info;
            try {
                info = pm.getPackageInfo(pkg, 0);
            } catch (PackageManager.NameNotFoundException e) {
                if (applied) changed.add(pkg);
                continue;
            }
            if (!rules.covers(pkg)) {
                if (applied) changed.add(pkg);
                continue;
            }
            AmoledCache.Entry entry = AmoledCache.read(context, pkg);
            if (entry == null || entry.hash == null ||
                    !AmoledCache.key(AmoledCache.idsKey(info), rulesHash).equals(entry.key)) {
                changed.add(pkg);
                continue;
            }
            boolean overlayOn = Character.valueOf('x').equals(overlays.get(pkg));
            boolean inPlace;
            if (Route.OVERLAY.name().equals(entry.route)) {
                inPlace = overlayOn;
            } else if (Route.MODULE.name().equals(entry.route)) {
                inPlace = !overlayOn && entry.hash.equals(moduleHash(pkg));
            } else {
                inPlace = !overlayOn && !moduleFile(pkg).exists();
            }
            if (!inPlace) changed.add(pkg);
        }
        return changed;
    }

    // Applying

    /**
     * {@link SystemAmoled#applyAll(Context, AmoledRules, SystemAmoled.Progress, List)} with this
     * engine.
     *
     * @return {apps themed, colors changed, apps read from the cache, apps processed}
     */
    static int[] applyAll(Context context, AmoledRules rules, SystemAmoled.Progress progress,
                          List<String> only) {
        long start = SystemClock.elapsedRealtime();
        List<String> targets = new ArrayList<>(only != null ? only : targets(context, rules));
        Run run = new Run(context, rules, progress, targets.size());
        // The framework first, the classic way and on its own: everything else reads it applied
        if (targets.remove(ANDROID)) {
            Prepared framework = run.prepare(ANDROID, true);
            run.progress(framework.pkg);
            run.commit(Collections.singletonList(framework));
        }
        if (!targets.isEmpty()) run.parallel(targets);
        Log.i(TAG, summary(run.apps, run.colors, SystemClock.elapsedRealtime() - start,
                run.cached, run.processed));
        return new int[]{run.apps, run.colors, run.cached, run.processed};
    }

    private static File moduleFile(String pkg) {
        return new File(XposedBackend.DIR, pkg + XposedBackend.AMOLED_IDS);
    }

    /**
     * Hash of an app's module list in place, as {@link Prepared#hash} has it, or null
     */
    private static String moduleHash(String pkg) {
        File file = moduleFile(pkg);
        if (!file.isFile()) return null;
        try {
            return AmoledCache.sha1(Route.MODULE.name() + '\n' +
                    new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private enum Kind {
        /**
         * Switched off by the user: left as it is
         */
        SKIP,
        UNINSTALLED,
        NOT_COVERED,
        /**
         * Its colors could not be read: it keeps what it had
         */
        FAILED,
        APPLY
    }

    /**
     * One app, read (or taken from the cache) and planned, waiting to be registered
     */
    private static final class Prepared {
        final String pkg;
        Kind kind = Kind.FAILED;
        Throwable error;
        boolean overlayWasOn;
        boolean hadModule;
        /**
         * The classic read disabled the overlay
         */
        boolean overlayDisabled;
        boolean cached;
        String versionKey;
        String cacheKey;
        String idsKey;
        List<Integer> ids;
        AmoledCache.Entry previous;
        int primary50 = AmoledPalette.ONEUI_BLUE;
        List<AmoledChange> changes = Collections.emptyList();
        Set<String> rejected = new LinkedHashSet<>();
        List<String[]> items = Collections.emptyList();
        List<String[]> overlayItems = Collections.emptyList();
        Route route = Route.NONE;
        String content = "";
        String hash;
        int count;
        /**
         * Nothing to register: already in place
         */
        boolean upToDate;
        /**
         * Safe to restart, where a restart is needed
         */
        boolean safe;
        Boolean restartNeeded;

        Prepared(String pkg) {
            this.pkg = pkg;
        }
    }

    /**
     * One apply: its inputs and counters
     */
    private static final class Run {
        final Context context;
        final AmoledRules rules;
        final String rulesHash;
        final Map<String, Character> overlays;
        final SystemAmoled.Progress progress;
        final PackageManager pm;
        final int total;
        int done;
        int apps;
        int colors;
        int cached;
        int processed;

        Run(Context context, AmoledRules rules, SystemAmoled.Progress progress, int total) {
            this.context = context;
            this.rules = rules;
            this.rulesHash = AmoledCache.rulesHash(rules);
            // One overlay list for the whole run
            this.overlays = new ConcurrentHashMap<>(SystemAmoled.overlayStates());
            this.progress = progress;
            this.pm = context.getPackageManager();
            this.total = total;
        }

        void progress(String pkg) {
            if (progress != null) progress.onPackage(pkg, done, total);
            done++;
        }

        /**
         * Read every app on {@value #THREADS} threads; register them in batches on this one.
         */
        void parallel(List<String> targets) {
            ExecutorService pool = Executors.newFixedThreadPool(Math.min(THREADS, targets.size()),
                    runnable -> {
                        Thread thread = new Thread(runnable, "AmoledCompute");
                        thread.setPriority(Thread.NORM_PRIORITY - 1);
                        return thread;
                    });
            CompletionService<Prepared> reads = new ExecutorCompletionService<>(pool);
            try {
                for (String pkg : targets) reads.submit(() -> prepare(pkg, false));
                List<Prepared> batch = new ArrayList<>();
                for (int i = 0; i < targets.size(); i++) {
                    Prepared p;
                    try {
                        p = reads.take().get();
                    } catch (ExecutionException e) {
                        Log.w(TAG, "A read failed: " + e.getCause());
                        continue;
                    }
                    progress(p.pkg);
                    batch.add(p);
                    if (batch.size() >= BATCH) {
                        commit(batch);
                        batch = new ArrayList<>();
                    }
                }
                if (!batch.isEmpty()) commit(batch);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                pool.shutdownNow();
            }
        }

        // Reading (any thread)

        Prepared prepare(String pkg, boolean classicRead) {
            Prepared p = new Prepared(pkg);
            try {
                read(p, classicRead);
            } catch (Throwable t) {
                p.kind = Kind.FAILED;
                p.error = t;
            }
            return p;
        }

        private void read(Prepared p, boolean classicRead) throws Exception {
            String pkg = p.pkg;
            p.overlayWasOn = Character.valueOf('x').equals(overlays.get(pkg));
            p.hadModule = moduleFile(pkg).exists();
            if (!AmoledState.read(context).isUserEnabled(pkg)) {
                p.kind = Kind.SKIP;
                return;
            }
            PackageInfo info;
            try {
                info = pm.getPackageInfo(pkg, 0);
            } catch (PackageManager.NameNotFoundException e) {
                p.kind = Kind.UNINSTALLED;
                return;
            }
            if (!rules.covers(pkg)) {
                p.kind = Kind.NOT_COVERED;
                return;
            }
            p.versionKey = XposedBackend.versionKey(info.applicationInfo.sourceDir,
                    androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info));
            p.idsKey = AmoledCache.idsKey(info);
            p.cacheKey = AmoledCache.key(p.idsKey, rulesHash);
            AmoledCache.Entry entry = AmoledCache.read(context, pkg);
            p.previous = entry;
            if (entry != null && p.cacheKey.equals(entry.key) && entry.changes != null) {
                p.cached = true;
                p.changes = entry.changes;
                p.primary50 = entry.primary50;
                p.rejected = new LinkedHashSet<>(entry.rejected);
                p.ids = entry.ids;
            } else {
                List<Integer> known = entry != null && p.idsKey.equals(entry.idsKey) ?
                        entry.ids : null;
                Resources[] res = classicRead ? null : AmoledStock.read(context, pkg, false);
                if (res != null && p.overlayWasOn && !AmoledStock.isClean(context, pkg, res[0])) {
                    // Without any fabricated overlay, else the classic way
                    res = AmoledStock.read(context, pkg, true);
                    if (res != null && !AmoledStock.isClean(context, pkg, res[0])) res = null;
                    if (res == null) {
                        Log.w(TAG, pkg + ": the clean read still shows its AMOLED colours, " +
                                "reading it the classic way");
                    }
                }
                if (res == null) {
                    // The classic read: disable the overlay and wait until stock shows
                    FabricatedValues.disable(Collections.singletonList(pkg + SystemAmoled.SUFFIX));
                    p.overlayDisabled = true;
                    SystemAmoled.awaitStock(context, pkg);
                    res = SystemAmoled.packageResources(context, pkg);
                }
                int[] primary = {AmoledPalette.ONEUI_BLUE};
                List<Integer> found = new ArrayList<>();
                p.changes = SystemAmoled.compute(res[0], res[1], pkg, rules, primary, known,
                        found);
                p.primary50 = primary[0];
                p.ids = found;
                // Obfuscated names go to the module anyway: no table to read for them
                if (!SystemAmoled.ambiguous(AmoledChange.toItems(p.changes))) {
                    p.rejected = predict(pkg, info, p.changes);
                }
                // What idmap2 refused from this version before (other rules, same app)
                if (known != null) p.rejected.addAll(entry.rejected);
            }
            plan(p);
            p.kind = Kind.APPLY;
        }

        /**
         * Colors idmap2 refuses from a shell overlay (see {@link ArscOverlayable#accepted}), as
         * {@code color/<name>}; none when the app is signed like the shell or its table cannot be
         * read (idmap2 decides then).
         */
        private Set<String> predict(String pkg, PackageInfo info, List<AmoledChange> changes) {
            Set<String> rejected = new LinkedHashSet<>();
            if (changes.isEmpty()) return rejected;
            try {
                if (pm.checkSignatures(SHELL, pkg) == PackageManager.SIGNATURE_MATCH) {
                    return rejected;
                }
            } catch (RuntimeException e) {
                return rejected;
            }
            Map<Integer, Integer> declared = ArscOverlayable.read(
                    info.applicationInfo.sourceDir, info.applicationInfo.splitSourceDirs);
            if (declared == null) {
                Log.w(TAG, pkg + ": resource table unreadable, idmap2 decides");
                return rejected;
            }
            for (AmoledChange change : changes) {
                if (!ArscOverlayable.accepted(declared, change.id, false)) {
                    rejected.add("color/" + change.name);
                }
            }
            if (!rejected.isEmpty()) {
                Log.i(TAG, pkg + ": " + rejected.size() + " of " + changes.size() +
                        " colours are not overlayable by a shell overlay");
            }
            return rejected;
        }

        /**
         * The route and what to register, as the classic engine ends up with: no colors, the
         * module for obfuscated names or when no color is overlayable, else the overlay with the
         * overlayable colors.
         */
        private void plan(Prepared p) {
            p.items = AmoledChange.toItems(p.changes);
            p.overlayItems = Collections.emptyList();
            if (p.items.isEmpty()) {
                p.route = Route.NONE;
                p.content = "";
                p.count = 0;
            } else if (SystemAmoled.ambiguous(p.items)) {
                module(p);
            } else {
                List<String[]> kept = new ArrayList<>(p.items.size());
                for (String[] item : p.items) if (!p.rejected.contains(item[0])) kept.add(item);
                if (kept.isEmpty()) {
                    module(p);
                } else {
                    p.route = Route.OVERLAY;
                    p.overlayItems = kept;
                    p.content = FabricatedValues.definitionXml(kept);
                    p.count = SystemAmoled.colorCount(kept);
                }
            }
            p.hash = AmoledCache.sha1(p.route.name() + '\n' + p.content);
        }

        private void module(Prepared p) {
            if (p.versionKey == null) {
                p.route = Route.UNSUPPORTED;
                p.content = "";
                p.count = 0;
                return;
            }
            p.route = Route.MODULE;
            p.content = SystemAmoled.idsContent(p.items, p.versionKey);
            p.count = SystemAmoled.idsCount(p.content);
        }

        // Registering (this thread)

        /**
         * Whether what an app needs is in place already, so nothing is registered (and a module
         * app is not restarted): its module list is the same file, it has nothing and needs
         * nothing, or the cache says its overlay was registered with the same contents and the
         * overlay is enabled (definitions are not readable without root).
         */
        private boolean upToDate(Prepared p) {
            switch (p.route) {
                case OVERLAY: {
                    AmoledCache.Entry previous = p.previous;
                    return previous != null && p.hash.equals(previous.hash) &&
                            Route.OVERLAY.name().equals(previous.route) && p.overlayWasOn &&
                            !p.overlayDisabled && !p.hadModule;
                }
                case MODULE:
                    return !p.overlayWasOn && p.hash.equals(moduleHash(p.pkg));
                default:
                    return !p.overlayWasOn && !p.hadModule;
            }
        }

        void commit(List<Prepared> batch) {
            long since = System.currentTimeMillis() - 2000;
            List<AmoledCommit.Op> ops = new ArrayList<>();
            for (Prepared p : batch) {
                switch (p.kind) {
                    case UNINSTALLED:
                        ops.add(new AmoledCommit.Op(p.pkg, AmoledCommit.Type.CLEAN, null, false));
                        break;
                    case NOT_COVERED:
                        p.safe = p.hadModule && SystemAmoled.safeToRestart(context, p.pkg);
                        ops.add(new AmoledCommit.Op(p.pkg, AmoledCommit.Type.CLEAN, null, p.safe));
                        break;
                    case FAILED:
                        if (p.overlayDisabled && p.overlayWasOn) {
                            ops.add(new AmoledCommit.Op(p.pkg, AmoledCommit.Type.ENABLE, null,
                                    false));
                        }
                        break;
                    case APPLY: {
                        if (upToDate(p)) {
                            p.upToDate = true;
                            break;
                        }
                        // Module colors load when the app starts; overlays apply live
                        boolean restart = p.route == Route.MODULE ||
                                p.route != Route.OVERLAY && p.hadModule;
                        p.safe = restart && SystemAmoled.safeToRestart(context, p.pkg);
                        if (restart && !p.safe) Log.i(TAG, "skip restart " + p.pkg);
                        p.restartNeeded = restart && !p.safe;
                        if (p.route == Route.OVERLAY) {
                            ops.add(new AmoledCommit.Op(p.pkg, AmoledCommit.Type.OVERLAY,
                                    p.content, false));
                        } else if (p.route == Route.MODULE) {
                            ops.add(new AmoledCommit.Op(p.pkg, AmoledCommit.Type.MODULE,
                                    p.content, p.safe));
                        } else {
                            ops.add(new AmoledCommit.Op(p.pkg, AmoledCommit.Type.CLEAN, null,
                                    p.safe));
                        }
                        break;
                    }
                    default:
                        break;
                }
            }
            AmoledCommit.Outcome outcome = AmoledCommit.run(context, ops);
            if (!ops.isEmpty() && !outcome.complete) {
                // The root shell went away mid-batch: nothing is known, everything keeps what it
                // had (no fallbacks that would move apps to another route)
                for (Prepared p : batch) {
                    if (p.kind == Kind.APPLY && !p.upToDate) {
                        p.kind = Kind.FAILED;
                        p.error = new IllegalStateException("registering did not finish");
                    }
                }
            }
            List<Consumer<AmoledState>> updates = new ArrayList<>();
            for (Prepared p : batch) finish(p, outcome, since, updates);
            if (!updates.isEmpty()) {
                AmoledState.update(context, s -> {
                    for (Consumer<AmoledState> update : updates) update.accept(s);
                });
            }
        }

        private void finish(Prepared p, AmoledCommit.Outcome outcome, long since,
                            List<Consumer<AmoledState>> updates) {
            String pkg = p.pkg;
            switch (p.kind) {
                case SKIP:
                    return;
                case UNINSTALLED:
                    AmoledChange.delete(context, pkg);
                    AmoledCache.delete(context, pkg);
                    updates.add(s -> {
                        AmoledState.App app = s.perApp.get(pkg);
                        if (app != null && app.userEnabled) s.perApp.remove(pkg);
                        else if (app != null) SystemAmoled.none(app);
                    });
                    return;
                case NOT_COVERED: {
                    AmoledChange.delete(context, pkg);
                    boolean restartNeeded = p.hadModule && !p.safe;
                    updates.add(s -> SystemAmoled.none(s.app(pkg)).restartNeeded = restartNeeded);
                    return;
                }
                case FAILED: {
                    Log.w(TAG, "Skipping " + pkg + ", keeping its previous colours: " + p.error);
                    String error = String.valueOf(p.error);
                    updates.add(s -> s.app(pkg).error = error);
                    return;
                }
                default:
                    break;
            }
            processed++;
            if (p.cached) cached++;
            if (!p.upToDate) {
                if (p.route == Route.OVERLAY) {
                    boolean on = outcome.ok(pkg) &&
                            Character.valueOf('x').equals(outcome.overlays.get(pkg));
                    if (!on) refused(p, since);
                } else if (p.route == Route.MODULE && !outcome.ok(pkg)) {
                    Log.w(TAG, pkg + ": module list not installed");
                    p.route = Route.UNSUPPORTED;
                    p.count = 0;
                    p.hash = null;
                    p.restartNeeded = p.hadModule;
                }
            }
            int count = p.count;
            if (count > 0) {
                apps++;
                colors += count;
            }
            // The change record, for the inspector and the preview
            String changesFile = null;
            File record = AmoledChange.file(context, pkg);
            if (count > 0) {
                if (p.upToDate && p.cached && record.isFile()) {
                    changesFile = record.getAbsolutePath();
                } else {
                    List<AmoledChange> applied = new ArrayList<>(p.changes.size());
                    for (AmoledChange change : p.changes) {
                        if (p.route != Route.OVERLAY ||
                                !p.rejected.contains("color/" + change.name)) {
                            applied.add(change);
                        }
                    }
                    File file = AmoledChange.save(context, new AmoledChange.Record(pkg,
                            rules.level().key(), rules.accent(), p.primary50, p.route.name(),
                            p.versionKey, System.currentTimeMillis(), applied));
                    if (file != null) changesFile = file.getAbsolutePath();
                }
            } else {
                AmoledChange.delete(context, pkg);
            }
            // The cache, with what is now in place (a warm app in place is cached as it is)
            if (!p.cached || !p.upToDate) writeCache(p, count);
            Route route = p.route;
            String key = p.versionKey;
            String level = rules.level().key();
            Boolean restartNeeded = p.upToDate ? null : p.restartNeeded;
            String recorded = changesFile;
            updates.add(s -> {
                AmoledState.App app = s.app(pkg);
                app.route = route;
                app.colors = count;
                app.versionKey = key;
                app.error = null;
                if (restartNeeded != null) app.restartNeeded = restartNeeded;
                app.level = level;
                app.changesFile = recorded;
            });
        }

        private void writeCache(Prepared p, int count) {
            AmoledCache.Entry entry = new AmoledCache.Entry();
            entry.key = p.cacheKey;
            entry.idsKey = p.idsKey;
            entry.ids = p.ids;
            entry.primary50 = p.primary50;
            entry.changes = p.changes;
            entry.rejected = p.rejected;
            entry.route = p.route.name();
            entry.hash = p.hash;
            entry.count = count;
            AmoledCache.write(context, p.pkg, entry);
        }

        /**
         * The overlay was refused anyway (STATE_NO_IDMAP, or it did not register): the classic
         * retry with idmap2's own rejections, then the module by id.
         */
        private void refused(Prepared p, long since) {
            String pkg = p.pkg;
            Log.i(TAG, pkg + ": overlay refused, retrying with idmap2's rejections");
            Set<String> more = new HashSet<>();
            int count = SystemAmoled.register(context, pkg, p.overlayItems, more, since);
            if (count > 0) {
                p.rejected.addAll(more);
                List<String[]> kept = new ArrayList<>();
                for (String[] item : p.overlayItems) if (!more.contains(item[0])) kept.add(item);
                p.overlayItems = kept;
                p.content = FabricatedValues.definitionXml(kept);
                p.count = count;
                p.hash = AmoledCache.sha1(Route.OVERLAY.name() + '\n' + p.content);
                p.restartNeeded = false;
                return;
            }
            // Refused for good: the module by id, next time straight away
            FabricatedValues.disable(Collections.singletonList(pkg + SystemAmoled.SUFFIX));
            SystemAmoled.deleteDefinition(pkg);
            for (String[] item : p.items) p.rejected.add(item[0]);
            module(p);
            int handed = p.route == Route.MODULE ?
                    SystemAmoled.registerById(context, pkg, p.items, p.versionKey) : 0;
            if (handed > 0) {
                p.count = handed;
                p.hash = AmoledCache.sha1(Route.MODULE.name() + '\n' + p.content);
                p.restartNeeded = !SystemAmoled.restartIfSafe(context, pkg);
            } else {
                p.route = Route.UNSUPPORTED;
                p.count = 0;
                p.hash = null;
                p.restartNeeded = p.hadModule && !SystemAmoled.restartIfSafe(context, pkg);
            }
        }
    }
}
