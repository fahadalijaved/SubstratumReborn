/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.common.platform;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import projekt.substratum.common.platform.AmoledState.Route;
import projekt.substratum.util.helpers.Root;

/**
 * "AMOLED for every app": turns the dark mode of every Samsung, Google and system app on the
 * device into true black, without a theme APK.
 * <p>
 * For each app it loads the app's own resources in dark mode, resolves what every color really
 * shows (following One UI's reference chains), applies the AMOLED rules ({@link AmoledPalette})
 * and registers the result as a night-only fabricated overlay (see {@link FabricatedValues}), or
 * hands it to the LSPosed module by resource id when the app refuses overlays. Light mode keeps
 * its stock values (only the accent may follow there, when chosen), and the overlay manager
 * applies overlays live. What is applied is kept in {@link AmoledState}, and each app's changes
 * in {@link AmoledChange} records; the steps run as {@link AmoledJobs}.
 */
public final class SystemAmoled {

    public static final String SUFFIX = ".RebornSystemAmoled";
    private static final String TAG = "SubstratumAmoled";
    private static final String SNAPSHOTS = FabricatedValues.DIR + "/snapshots";
    /**
     * Never force-stopped: the phone, calls, Bluetooth, NFC, the keyboard, the launcher, Play
     * services, System UI and Substratum itself (see {@link #safeToRestart} for the rest)
     */
    private static final Set<String> NEVER_RESTART = new HashSet<>(Arrays.asList(
            "android",
            "com.android.systemui",
            "com.android.phone",
            "com.android.bluetooth",
            "com.android.server.telecom",
            "com.samsung.android.incallui",
            "com.samsung.android.app.telephonyui",
            "com.android.nfc",
            "com.samsung.android.honeyboard",
            "com.samsung.android.dialer",
            "com.google.android.gms",
            "com.sec.android.app.launcher",
            "projekt.substratum"));

    private SystemAmoled() {
    }

    public interface Progress {
        void onPackage(String packageName, int done, int total);
    }

    // Applying

    /**
     * Apply to every Samsung, Google and system app. Runs for a while: call off the main thread.
     *
     * @return {apps themed, colors changed, apps read from the cache, apps processed}
     */
    public static int[] applyAll(Context context, AmoledRules rules, Progress progress) {
        return applyAll(context, rules, progress, null);
    }

    /**
     * Read and register the colors of apps. Apps switched off are skipped (checked as each app's
     * turn comes, so a switch flipped during an apply sticks). Does not touch the AMOLED flag:
     * see {@link #markApplied}.
     *
     * @param only Packages to process, or null for every covered app (and every app applied
     *             before that the rules no longer cover, which is cleaned up)
     * @return {apps themed, colors changed, apps read from the cache, apps processed}
     */
    public static int[] applyAll(Context context, AmoledRules rules, Progress progress,
                                 List<String> only) {
        return applyAll(context, rules, progress, only, false);
    }

    /**
     * {@link #applyAll(Context, AmoledRules, Progress, List)}
     *
     * @param gentle Started on its own (updated apps): with the fast engine, a module app that is
     *               running is not force-stopped but marked "restart needed" (the classic engine
     *               restarts as always)
     */
    public static int[] applyAll(Context context, AmoledRules rules, Progress progress,
                                 List<String> only, boolean gentle) {
        if (AmoledEngine.isEnabled()) {
            return AmoledEngine.applyAll(context, rules, progress, only, gentle);
        }
        long start = android.os.SystemClock.elapsedRealtime();
        List<String> targets = only != null ? only : targets(context, rules);
        Map<String, Character> overlays = overlayStates();
        int apps = 0, colors = 0;
        for (int i = 0; i < targets.size(); i++) {
            String pkg = targets.get(i);
            if (progress != null) progress.onPackage(pkg, i, targets.size());
            if (!AmoledState.read(context).isUserEnabled(pkg)) continue;
            try {
                int count = process(context, pkg, rules, overlays);
                if (count > 0) {
                    apps++;
                    colors += count;
                }
            } catch (Throwable t) {
                Log.w(TAG, "Skipping " + pkg + ": " + t);
                AmoledState.update(context, s -> s.app(pkg).error = String.valueOf(t));
            }
        }
        Log.i(AmoledEngine.TAG, AmoledEngine.summary(apps, colors,
                android.os.SystemClock.elapsedRealtime() - start, 0, targets.size()) +
                ", classic engine");
        return new int[]{apps, colors, 0, targets.size()};
    }

    /**
     * AMOLED is applied: turn on the module's System UI code hooks (opaque shade, dark bar
     * tracks) and remember the apply.
     */
    public static void markApplied(Context context, String themePackage) {
        markApplied(context, themePackage, null);
    }

    /**
     * {@link #markApplied(Context, String)}, remembering the look the rules had (so a new
     * wallpaper accent can be noticed).
     */
    public static void markApplied(Context context, String themePackage, AmoledRules rules) {
        Root.runCommand("mkdir -p " + XposedBackend.DIR + " && chmod 755 " + XposedBackend.DIR +
                " && touch " + XposedBackend.DIR + "/" + XposedBackend.AMOLED_FLAG +
                " && chmod 644 " + XposedBackend.DIR + "/" + XposedBackend.AMOLED_FLAG +
                " && restorecon -R " + XposedBackend.DIR + " 2>/dev/null");
        int boot = AmoledState.currentBootCount(context);
        AmoledState.update(context, s -> {
            s.enabled = true;
            s.rulesTheme = themePackage;
            s.appliedAt = System.currentTimeMillis();
            s.bootCount = boot;
            s.appliedBootCount = boot;
            if (rules != null) {
                s.level = rules.level().key();
                s.accent = rules.accent();
            }
        });
    }

    /**
     * One app, with clean transitions: an app left without colors (none to change, excluded by
     * the rules, uninstalled) loses its overlay definition and module colors; an app that moves
     * to the module loses its definition; an app whose colors cannot be read keeps what it had.
     *
     * @return Number of colors applied
     */
    private static int process(Context context, String pkg, AmoledRules rules,
                               Map<String, Character> overlays) {
        String name = pkg + SUFFIX;
        boolean overlayWasOn = Character.valueOf('x').equals(overlays.get(pkg));
        boolean hadModule = new File(XposedBackend.DIR, pkg + XposedBackend.AMOLED_IDS).exists();
        // What the cached engine knows of this app no longer holds
        AmoledCache.delete(context, pkg);
        if (!installed(context, pkg)) {
            clean(pkg);
            AmoledChange.delete(context, pkg);
            AmoledState.update(context, s -> {
                AmoledState.App app = s.perApp.get(pkg);
                if (app != null && app.userEnabled) s.perApp.remove(pkg);
                else if (app != null) none(app);
            });
            return 0;
        }
        if (!rules.covers(pkg)) {
            clean(pkg);
            AmoledChange.delete(context, pkg);
            boolean restartNeeded = hadModule && !restartIfSafe(context, pkg);
            AmoledState.update(context, s -> {
                AmoledState.App app = none(s.app(pkg));
                app.restartNeeded = restartNeeded;
            });
            return 0;
        }
        // Read the stock colors: an active AMOLED overlay would hide them
        FabricatedValues.disable(Collections.singletonList(name));
        awaitStock(context, pkg);
        String key = versionKey(context, pkg);
        List<AmoledChange> changes;
        // The app's stock accent, read with the colors (once the overlay is enabled again it
        // would show the chosen accent instead)
        int[] stockPrimary = {AmoledPalette.ONEUI_BLUE};
        try {
            changes = compute(context, pkg, rules, stockPrimary);
        } catch (Throwable t) {
            // Keep what the app had
            Log.w(TAG, "Skipping " + pkg + ", keeping its previous colors: " + t);
            if (overlayWasOn) FabricatedValues.enable(Collections.singletonList(name));
            AmoledState.update(context, s -> s.app(pkg).error = String.valueOf(t));
            return 0;
        }
        removeById(pkg);
        List<String[]> items = AmoledChange.toItems(changes);
        Set<String> rejected = new HashSet<>();
        Route route;
        int count = 0;
        if (items.isEmpty()) {
            route = Route.NONE;
        } else if (ambiguous(items)) {
            // Obfuscated resource names (Play Store and other Google apps): an overlay names its
            // targets, so only the LSPosed module, which works by id, can help
            count = registerById(context, pkg, items, key);
            route = count > 0 ? Route.MODULE : Route.UNSUPPORTED;
        } else {
            count = register(context, pkg, items, rejected);
            if (count > 0) {
                route = Route.OVERLAY;
            } else {
                // The app refuses overlays for these colors: the module by id instead
                rejected.clear();
                FabricatedValues.disable(Collections.singletonList(name));
                count = registerById(context, pkg, items, key);
                route = count > 0 ? Route.MODULE : Route.UNSUPPORTED;
            }
        }
        if (route != Route.OVERLAY) deleteDefinition(pkg);
        // Module colors load (and unload) when the app starts; overlays apply live, so an app
        // that ends on the overlay route is never restarted
        boolean restartNeeded = (route == Route.MODULE || route != Route.OVERLAY && hadModule) &&
                !restartIfSafe(context, pkg);
        // What the app got, for the inspector and the preview
        String changesFile = null;
        if (count > 0) {
            List<AmoledChange> applied = new ArrayList<>(changes.size());
            for (AmoledChange change : changes) {
                if (!rejected.contains("color/" + change.name)) applied.add(change);
            }
            File file = AmoledChange.save(context, new AmoledChange.Record(pkg,
                    rules.level().key(), rules.accent(), stockPrimary[0], route.name(),
                    key, System.currentTimeMillis(), applied));
            if (file != null) changesFile = file.getAbsolutePath();
        } else {
            AmoledChange.delete(context, pkg);
        }
        int appliedCount = count;
        String recorded = changesFile;
        AmoledState.update(context, s -> {
            AmoledState.App app = s.app(pkg);
            app.route = route;
            app.colors = appliedCount;
            app.versionKey = key;
            app.error = null;
            app.restartNeeded = restartNeeded;
            app.level = rules.level().key();
            app.changesFile = recorded;
        });
        return count;
    }

    static AmoledState.App none(AmoledState.App app) {
        app.route = Route.NONE;
        app.colors = 0;
        app.error = null;
        app.changesFile = null;
        return app;
    }

    /**
     * Disable an app's overlay and delete its definition and module colors (root).
     */
    private static void clean(String pkg) {
        String name = pkg + SUFFIX;
        Root.runCommand("cmd overlay disable --user 0 'com.android.shell:" + name +
                "' >/dev/null 2>&1; rm -f '" + definition(pkg) + "' '" + XposedBackend.DIR + '/' +
                pkg + XposedBackend.AMOLED_IDS + "'");
    }

    static void deleteDefinition(String pkg) {
        Root.runCommand("rm -f '" + definition(pkg) + "'");
    }

    static String definition(String pkg) {
        return FabricatedValues.DIR + '/' + pkg + SUFFIX + ".xml";
    }

    static boolean installed(Context context, String pkg) {
        try {
            context.getPackageManager().getApplicationInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private static String versionKey(Context context, String pkg) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(pkg, 0);
            return XposedBackend.versionKey(info.applicationInfo.sourceDir,
                    androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info));
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            Log.w(TAG, pkg + ": no version to key the colors with: " + e);
            return null;
        }
    }

    /**
     * Disabling an overlay reaches this process asynchronously (for the framework, through a
     * configuration change). Wait until a value of the previous definition reads stock again.
     */
    static void awaitStock(Context context, String pkg) {
        String line = Root.runCommand(
                "grep -m1 'config=\"night\"' '" + definition(pkg) + "' 2>/dev/null");
        if (line == null) return;
        Matcher m = Pattern.compile(
                "target=\"color/([^\"]+)\".*value=\"0x([0-9a-fA-F]{8})\"").matcher(line);
        if (!m.find()) return;
        String name = m.group(1);
        int applied = (int) Long.parseLong(m.group(2), 16);
        TypedValue tv = new TypedValue();
        for (int i = 0; i < 40; i++) {
            try {
                Resources res = nightResources(context, pkg);
                int id = res.getIdentifier(name, "color", pkg);
                if (id == 0) return;
                res.getValue(id, tv, true);
                if (tv.data != applied) return;
                Thread.sleep(250);
            } catch (Exception e) {
                return;
            }
        }
        Log.w(TAG, pkg + ": previous overlay still visible, colors may be incomplete");
    }

    static Resources nightResources(Context context, String pkg)
            throws PackageManager.NameNotFoundException {
        Context app = context.createPackageContext(pkg, Context.CONTEXT_IGNORE_SECURITY);
        Configuration night = new Configuration(app.getResources().getConfiguration());
        night.uiMode = (night.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) |
                Configuration.UI_MODE_NIGHT_YES;
        return app.createConfigurationContext(night).getResources();
    }

    /**
     * An app's resources as it shows them (its enabled overlays included), {night, day}: the
     * classic read, after its AMOLED overlay was disabled.
     */
    static Resources[] packageResources(Context context, String pkg)
            throws PackageManager.NameNotFoundException {
        Context app = context.createPackageContext(pkg, Context.CONTEXT_IGNORE_SECURITY);
        Configuration night = new Configuration(app.getResources().getConfiguration());
        night.uiMode = (night.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) |
                Configuration.UI_MODE_NIGHT_YES;
        Resources res = app.createConfigurationContext(night).getResources();
        Configuration day = new Configuration(night);
        day.uiMode = (day.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) |
                Configuration.UI_MODE_NIGHT_NO;
        return new Resources[]{res, app.createConfigurationContext(day).getResources()};
    }

    /**
     * Register and enable an app's overlay. Apps that declare overlayable resources only accept
     * those: one other entry and the whole overlay is refused (STATE_NO_IDMAP). In that case the
     * entries idmap2 rejected are dropped and the rest is registered again.
     *
     * @param rejected Filled with the targets ({@code color/<name>}) idmap2 refused
     * @return Number of colors applied
     */
    private static int register(Context context, String pkg, List<String[]> items,
                                Set<String> rejected) {
        return register(context, pkg, items, rejected, System.currentTimeMillis() - 2000);
    }

    /**
     * {@link #register(Context, String, List, Set)}
     *
     * @param since Wall time before the first attempt: only idmap2d's log from then on is read
     */
    static int register(Context context, String pkg, List<String[]> items, Set<String> rejected,
                        long since) {
        String name = pkg + SUFFIX;
        List<String> names = new ArrayList<>();
        names.add(name);
        // idmap2d names at most 50 rejected resources per attempt, so keep trying while each
        // attempt reveals new ones. Only the log since this package started is read: scanning
        // the whole buffer took 20 s to many minutes per call on One UI 9.
        for (int attempt = 0; attempt < MAX_REGISTER_ATTEMPTS && !items.isEmpty(); attempt++) {
            if (FabricatedValues.registerItems(context, pkg, name, items) == 0) return 0;
            FabricatedValues.enable(names);
            String state = Root.runCommand(
                    "cmd overlay dump com.android.shell:" + name + " | grep mState");
            if (state == null || !state.contains("NO_IDMAP")) return colorCount(items);
            int known = rejected.size();
            rejected.addAll(rejectedByIdmap(name, since));
            if (rejected.size() == known) return 0;
            List<String[]> kept = new ArrayList<>();
            for (String[] item : items) if (!rejected.contains(item[0])) kept.add(item);
            if (kept.size() == items.size()) return 0;
            Log.i(TAG, pkg + ": " + rejected.size() + " colors are not overlayable, retrying");
            items = kept;
        }
        return 0;
    }

    private static final int MAX_REGISTER_ATTEMPTS = 12;

    /**
     * Colors in a list of items: one night item each (a light item is optional)
     */
    static int colorCount(List<String[]> items) {
        Set<String> targets = new HashSet<>();
        for (String[] item : items) if ("night".equals(item[3])) targets.add(item[0]);
        return targets.size();
    }

    private static Set<String> rejectedByIdmap(String overlayName, long sinceMillis) {
        Set<String> rejected = new HashSet<>();
        String since = (sinceMillis / 1000) + "." + String.format(java.util.Locale.US, "%03d",
                sinceMillis % 1000);
        String log = Root.runCommand(
                "timeout 20 logcat -d -T '" + since + "' -s idmap2d:* </dev/null | " +
                        "grep 'shell-" + overlayName + "-' | " +
                        "grep -oE \"overlay resource '[^']+'\" | sort -u");
        if (log == null) return rejected;
        Matcher m = Pattern.compile("overlay resource '([^']+)'").matcher(log);
        while (m.find()) rejected.add(m.group(1));
        return rejected;
    }

    /**
     * True when several resources share a name, which happens when names are obfuscated.
     */
    static boolean ambiguous(List<String[]> items) {
        Map<String, String> seen = new HashMap<>();
        for (String[] item : items) {
            String previous = seen.put(item[0], item[4]);
            if (previous != null && !previous.equals(item[4])) return true;
        }
        return false;
    }

    /**
     * Hand the colors to the LSPosed module, which replaces them by resource id inside the app
     * (the app has to be in the module's scope). The first line is {@code # key=} with the app
     * version the colors were read from (the module skips the file once the app is updated).
     * Then one line per color: id, light value, night value and, for plain colors, the stock dark
     * value (the module maps code-drawn surfaces with it), or {@code csl} for color state lists
     * the rules name a color for.
     * <p>
     * The colors load when the app starts: the caller restarts it if that is safe.
     *
     * @return Number of colors handed over
     */
    static int registerById(Context context, String pkg, List<String[]> items, String key) {
        if (key == null) return 0;
        String content = idsContent(items, key);
        File local = new File(context.getCacheDir(), pkg + XposedBackend.AMOLED_IDS);
        try (FileWriter writer = new FileWriter(local)) {
            writer.write(content);
        } catch (IOException e) {
            return 0;
        }
        boolean copied = installIds(local, pkg);
        //noinspection ResultOfMethodCallIgnored
        local.delete();
        if (!copied) return 0;
        int count = idsCount(content);
        Log.i(TAG, pkg + ": " + count + " colors handed to the LSPosed module by id");
        return count;
    }

    /**
     * Colors in a module list: one line per resource id after the key line
     */
    static int idsCount(String content) {
        int lines = 0;
        for (int i = 0; i < content.length(); i++) if (content.charAt(i) == '\n') lines++;
        return Math.max(0, lines - 1);
    }

    /**
     * The module list of an app's colors (see {@link #registerById}).
     */
    static String idsContent(List<String[]> items, String key) {
        Map<String, String[]> byId = new LinkedHashMap<>();
        for (String[] item : items) {
            String[] line = byId.get(item[4]);
            if (line == null) byId.put(item[4], line = new String[]{item[4], item[2], item[2], ""});
            if ("night".equals(item[3])) {
                line[2] = item[2];
                if (item.length > 5) {
                    // stock dark value; none for a color state list
                    line[3] = item[5].isEmpty() ? "csl" : item[5];
                }
            } else {
                line[1] = item[2];
            }
        }
        StringBuilder content = new StringBuilder(XposedBackend.KEY_HEADER).append(key)
                .append('\n');
        for (String[] line : byId.values()) {
            content.append(line[0]).append(' ').append(line[1]).append(' ').append(line[2]);
            if (!line[3].isEmpty()) content.append(' ').append(line[3]);
            content.append('\n');
        }
        return content.toString();
    }

    /**
     * Copy a color list into the module's directory as the app's live list (root).
     */
    private static boolean installIds(File source, String pkg) {
        String file = XposedBackend.DIR + '/' + pkg + XposedBackend.AMOLED_IDS;
        String out = Root.runCommand(
                "mkdir -p " + XposedBackend.DIR + " && chmod 755 " + XposedBackend.DIR +
                        " && cp -f '" + source.getAbsolutePath() + "' '" + file + "'" +
                        " && chmod 644 '" + file + "' && restorecon '" + file +
                        "' 2>/dev/null; echo rc=$?");
        return out != null && out.trim().endsWith("rc=0");
    }

    private static void removeById(String pkg) {
        String file = XposedBackend.DIR + '/' + pkg + XposedBackend.AMOLED_IDS;
        if (new File(file).exists()) Root.runCommand("rm -f '" + file + "'");
    }

    // Restarting apps

    /**
     * Whether an app may be force-stopped so it reloads module colors. Never persistent apps,
     * telephony, Bluetooth, NFC (and anything else running as a core identity or in one of their
     * processes), the keyboard in use, the home app, apps with an accessibility service on or
     * the live wallpaper in use, Play services, System UI or Substratum.
     */
    public static boolean safeToRestart(Context context, String pkg) {
        if (pkg == null || NEVER_RESTART.contains(pkg)) return false;
        PackageManager pm = context.getPackageManager();
        try {
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            if ((info.flags & ApplicationInfo.FLAG_PERSISTENT) != 0) return false;
            // Core identities other than system (phone, Bluetooth, NFC, secure element, network
            // stack...), whatever their package is called on this device
            int appId = info.uid % 100000;
            if (appId < android.os.Process.FIRST_APPLICATION_UID &&
                    appId != android.os.Process.SYSTEM_UID) {
                return false;
            }
            // Runs inside one of the processes above (telephony providers in com.android.phone)
            if (info.processName != null && !pkg.equals(info.processName) &&
                    (NEVER_RESTART.contains(info.processName) ||
                            "system".equals(info.processName))) {
                return false;
            }
            // Shares its user id, and so its processes, with one of them (Google Services
            // Framework with Play services)
            if (appId != android.os.Process.SYSTEM_UID) {
                String[] shared = pm.getPackagesForUid(info.uid);
                if (shared != null && shared.length > 1) {
                    for (String other : shared) if (NEVER_RESTART.contains(other)) return false;
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
        try {
            String ime = Settings.Secure.getString(context.getContentResolver(),
                    Settings.Secure.DEFAULT_INPUT_METHOD);
            ComponentName component = ime != null ? ComponentName.unflattenFromString(ime) : null;
            if (component != null && pkg.equals(component.getPackageName())) return false;
        } catch (RuntimeException ignored) {
            // no keyboard setting to read
        }
        // A force-stop turns the package's accessibility services off for good
        try {
            String services = Settings.Secure.getString(context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (services != null) {
                for (String service : services.split(":")) {
                    ComponentName component = ComponentName.unflattenFromString(service);
                    if (component != null && pkg.equals(component.getPackageName())) return false;
                }
            }
        } catch (RuntimeException ignored) {
            // no accessibility setting to read
        }
        // ...and resets a live wallpaper of the package to the default one
        try {
            android.app.WallpaperInfo wallpaper =
                    android.app.WallpaperManager.getInstance(context).getWallpaperInfo();
            if (wallpaper != null && pkg.equals(wallpaper.getPackageName())) return false;
        } catch (RuntimeException ignored) {
            // no wallpaper service
        }
        try {
            ResolveInfo home = pm.resolveActivity(new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY);
            if (home != null && home.activityInfo != null &&
                    pkg.equals(home.activityInfo.packageName)) {
                return false;
            }
        } catch (RuntimeException e) {
            return false;
        }
        return true;
    }

    /**
     * Force-stop an app so it reloads its module colors, if that is safe (root).
     *
     * @return True if it was restarted; false means a restart is still needed
     */
    static boolean restartIfSafe(Context context, String pkg) {
        if (!safeToRestart(context, pkg)) {
            Log.i(TAG, "skip restart " + pkg);
            return false;
        }
        // A stop on purpose is no crash loop
        Root.runCommand(XposedBackend.forgetStartsCommand(pkg) + "; am force-stop " + pkg);
        return true;
    }

    /**
     * Restart an app marked "restart needed", if that is safe now (root).
     *
     * @return True if it was restarted
     */
    public static boolean restartApp(Context context, String pkg) {
        if (!restartIfSafe(context, pkg)) return false;
        AmoledState.update(context, s -> {
            AmoledState.App app = s.perApp.get(pkg);
            if (app != null) app.restartNeeded = false;
        });
        return true;
    }

    // Restoring after a reboot

    /**
     * Fabricated overlays do not survive a reboot on One UI: register again what was applied,
     * from the definitions kept in /data/system/substratum (top level only, never the snapshots).
     * AMOLED for every app comes back for the overlay apps that are switched on, while AMOLED is
     * applied; Substratum overlays with fabricated values come back while they are enabled.
     * Call with root.
     *
     * @return Number of overlays registered
     */
    public static int restoreAll(Context context) {
        String dir = FabricatedValues.DIR;
        String cache = XposedBackend.DIR;
        boolean known = AmoledState.exists(context);
        AmoledState state = AmoledState.read(context);
        StringBuilder script = new StringBuilder("n=0; ");
        if (!known || state.appliedAt == 0) {
            // Applied by an older version: every definition, except apps switched off
            if (new File(cache, XposedBackend.AMOLED_FLAG).exists()) {
                String listing = Root.runCommand(
                        "ls " + dir + " 2>/dev/null | grep '" + SUFFIX + ".xml$'");
                if (listing != null) {
                    for (String file : listing.split("\\s+")) {
                        if (!file.endsWith(SUFFIX + ".xml")) continue;
                        String name = file.substring(0, file.length() - 4);
                        String target = name.substring(0, name.length() - SUFFIX.length());
                        if (!state.isUserEnabled(target)) continue;
                        script.append(fabricateAndEnable(target, name, dir + '/' + file));
                    }
                }
            }
        } else if (state.enabled) {
            for (Map.Entry<String, AmoledState.App> entry : state.perApp.entrySet()) {
                AmoledState.App app = entry.getValue();
                if (app.route != Route.OVERLAY || !app.userEnabled) continue;
                String target = entry.getKey();
                String file = definition(target);
                script.append("[ -f '").append(file).append("' ] && { ")
                        .append(fabricateAndEnable(target, target + SUFFIX, file)).append("}; ");
            }
        }
        // Enabled Substratum overlays with fabricated values: target from the index
        script.append("for e in ").append(cache).append("/*").append(XposedBackend.ENABLED)
                .append("; do [ -f \"$e\" ] || continue; o=$(basename \"$e\" ")
                .append(XposedBackend.ENABLED).append("); f=").append(dir)
                .append("/\"$o\".xml; [ -f \"$f\" ] || continue; t=$(sed -n 's/^target=//p' ")
                .append(cache).append("/\"$o\"").append(XposedBackend.INDEX)
                .append("); [ -n \"$t\" ] || continue; ")
                .append("cmd overlay fabricate --target \"$t\" --name \"$o\" --file \"$f\" ")
                .append(">/dev/null 2>&1 && cmd overlay enable --user 0 com.android.shell:\"$o\" ")
                .append(">/dev/null 2>&1 && n=$((n+1)); done; echo restored=$n");
        String out = Root.runCommand(script.toString());
        int restored = 0;
        if (out != null) {
            Matcher m = Pattern.compile("restored=(\\d+)").matcher(out);
            if (m.find()) restored = Integer.parseInt(m.group(1));
            int boot = AmoledState.currentBootCount(context);
            AmoledState.update(context, s -> {
                s.restoredAt = System.currentTimeMillis();
                s.bootCount = boot;
                // Every app started afresh
                for (AmoledState.App app : s.perApp.values()) app.restartNeeded = false;
            });
        }
        Log.i(TAG, "restored " + restored + " fabricated overlays after boot");
        return restored;
    }

    /**
     * Whether a restore after boot has anything to do: AMOLED is applied (or was, by an older
     * version), or an enabled Substratum overlay has fabricated values. Needs no root (the
     * module's folder is readable by apps); when that folder cannot be listed, assume there is.
     */
    /**
     * The running system server, as {@code start_count:start_elapsed}, or "" if unknown.
     * Fabricated overlays live exactly as long as it does: a soft reboot starts a new one.
     * Android 15+ also sends BOOT_COMPLETED when the app is first opened after an update or a
     * force stop, when nothing was lost.
     */
    public static String serverInstance() {
        String start = projekt.substratum.common.Systems.getProp(
                "sys.system_server.start_elapsed");
        if (start == null || start.trim().isEmpty()) return "";
        String count = projekt.substratum.common.Systems.getProp(
                "sys.system_server.start_count");
        return (count == null ? "" : count.trim()) + ':' + start.trim();
    }

    public static boolean hasRestorable(Context context) {
        File dir = new File(XposedBackend.DIR);
        String[] files = dir.list();
        if (files == null && dir.exists()) return true;
        if (files != null) {
            for (String file : files) {
                if (file.equals(XposedBackend.AMOLED_FLAG)) return true;
                if (file.endsWith(XposedBackend.ENABLED) && hasFabricatedValues(
                        file.substring(0, file.length() - XposedBackend.ENABLED.length()))) {
                    return true;
                }
            }
        }
        return AmoledState.exists(context) && AmoledState.read(context).enabled;
    }

    private static boolean hasFabricatedValues(String overlay) {
        try (BufferedReader reader = new BufferedReader(new FileReader(
                new File(XposedBackend.DIR, overlay + XposedBackend.INDEX)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().equals(XposedBackend.FABRICATED)) return true;
            }
            return false;
        } catch (IOException e) {
            return true; // cannot tell
        }
    }

    private static String fabricateAndEnable(String target, String name, String file) {
        return "cmd overlay fabricate --target '" + target + "' --name '" + name + "' --file '" +
                file + "' >/dev/null 2>&1 && cmd overlay enable --user 0 'com.android.shell:" +
                name + "' >/dev/null 2>&1 && n=$((n+1)); ";
    }

    // Listing and switching apps

    /**
     * One app of "AMOLED for every app", as listed on the settings screen
     */
    public static final class AppState {
        public final String packageName;
        public final Route route;
        /**
         * The user's switch
         */
        public final boolean enabled;
        /**
         * Colors applied right now: its overlay is enabled, or its module colors are in place
         */
        public final boolean active;
        public final boolean restartNeeded;
        public final int colors;
        public final String error;

        AppState(String packageName, Route route, boolean enabled, boolean active,
                 boolean restartNeeded, int colors, String error) {
            this.packageName = packageName;
            this.route = route;
            this.enabled = enabled;
            this.active = active;
            this.restartNeeded = restartNeeded;
            this.colors = colors;
            this.error = error;
        }
    }

    /**
     * Every app AMOLED has colors for, apps switched off and apps waiting for a restart: from
     * the state, checked against one {@code cmd overlay list} (root). Overlays and module lists
     * the state does not know (applied by an older version) are added to it.
     */
    public static List<AppState> listApps(Context context) {
        return listApps(context, false);
    }

    /**
     * {@link #listApps(Context)}
     *
     * @param quick Read the overlay list on the status root shell ({@link Root#runQuickCommand}),
     *              so a screen never waits behind a running job
     */
    public static List<AppState> listApps(Context context, boolean quick) {
        Map<String, Character> overlays = overlayStates(quick);
        Set<String> moduleLists = new HashSet<>();
        String[] files = new File(XposedBackend.DIR).list();
        if (files != null) {
            for (String file : files) {
                if (file.endsWith(XposedBackend.AMOLED_IDS)) {
                    moduleLists.add(file.substring(0,
                            file.length() - XposedBackend.AMOLED_IDS.length()));
                }
            }
        }
        AmoledState state = AmoledState.read(context);
        Map<String, AppState> apps = new TreeMap<>();
        for (Map.Entry<String, AmoledState.App> entry : state.perApp.entrySet()) {
            String pkg = entry.getKey();
            AmoledState.App app = entry.getValue();
            if (app.route == Route.NONE && app.userEnabled && !app.restartNeeded) continue;
            Character overlay = overlays.get(pkg);
            Route route = app.route;
            if (route == Route.OVERLAY && overlay != null && overlay == '-') {
                route = Route.UNSUPPORTED; // registered, but refused
            }
            boolean active = route == Route.OVERLAY ? overlay != null && overlay == 'x' :
                    route == Route.MODULE && moduleLists.contains(pkg);
            apps.put(pkg, new AppState(pkg, route, app.userEnabled, active, app.restartNeeded,
                    app.colors, app.error));
        }
        Map<String, AmoledState.App> found = new HashMap<>();
        for (String pkg : moduleLists) {
            if (state.perApp.containsKey(pkg)) continue;
            AmoledState.App app = new AmoledState.App();
            app.route = Route.MODULE;
            found.put(pkg, app);
        }
        for (Map.Entry<String, Character> overlay : overlays.entrySet()) {
            String pkg = overlay.getKey();
            char status = overlay.getValue();
            if (state.perApp.containsKey(pkg) || found.containsKey(pkg)) continue;
            if (status != 'x' && status != '-') continue; // disabled: removed or switched off
            AmoledState.App app = new AmoledState.App();
            app.route = status == 'x' ? Route.OVERLAY : Route.UNSUPPORTED;
            found.put(pkg, app);
        }
        for (Map.Entry<String, AmoledState.App> entry : found.entrySet()) {
            AmoledState.App app = entry.getValue();
            apps.put(entry.getKey(), new AppState(entry.getKey(), app.route, true,
                    app.route != Route.UNSUPPORTED, false, 0, null));
        }
        if (!found.isEmpty()) {
            AmoledState.update(context, s -> {
                for (Map.Entry<String, AmoledState.App> entry : found.entrySet()) {
                    if (!s.perApp.containsKey(entry.getKey())) {
                        s.perApp.put(entry.getKey(), entry.getValue());
                    }
                }
            });
        }
        return new ArrayList<>(apps.values());
    }

    /**
     * Status of every AMOLED overlay: 'x' enabled, ' ' disabled, '-' refused by the system
     */
    static Map<String, Character> overlayStates() {
        return overlayStates(false);
    }

    static Map<String, Character> overlayStates(boolean quick) {
        String command = "cmd overlay list 2>/dev/null | grep '" + SUFFIX + "'";
        String out = quick ? Root.runQuickCommand(command) : Root.runCommand(command);
        return parseOverlayStates(out);
    }

    /**
     * The AMOLED overlays in a {@code cmd overlay list} output (see {@link #overlayStates()})
     */
    static Map<String, Character> parseOverlayStates(String out) {
        Map<String, Character> states = new HashMap<>();
        if (out == null) return states;
        Matcher m = Pattern.compile(
                "\\[(.)\\] com\\.android\\.shell:(\\S+)" + Pattern.quote(SUFFIX) + "|" +
                        "--- com\\.android\\.shell:(\\S+)" + Pattern.quote(SUFFIX))
                .matcher(out);
        while (m.find()) {
            if (m.group(2) != null) states.put(m.group(2), m.group(1).charAt(0));
            else states.put(m.group(3), '-');
        }
        return states;
    }

    /**
     * What switching an app did
     */
    public enum Switched {
        /**
         * AMOLED is not applied: only the switch was saved
         */
        SAVED,
        ON,
        OFF,
        /**
         * Done, but the app was not safe to restart: it changes from its next start
         */
        RESTART_NEEDED,
        /**
         * The app's colors have to be read again: an APPLY_ONE job was queued
         */
        REAPPLY
    }

    /**
     * Switch one app's AMOLED colors on or off, remembered for applying and restoring (root).
     * Overlays switch live; module colors are taken away (a copy is kept in
     * {@link AmoledState#idsCopy}) or put back, and the app is restarted if that is safe.
     */
    public static Switched setAppEnabled(Context context, String pkg, boolean enabled) {
        AmoledState state = AmoledState.read(context);
        if (state.isUserEnabled(pkg) != enabled) {
            state = AmoledState.update(context, s -> s.app(pkg).userEnabled = enabled);
        }
        if (!state.enabled) return Switched.SAVED;
        AmoledState.App entry = state.perApp.get(pkg);
        Route route = entry != null ? entry.route : Route.NONE;
        String name = pkg + SUFFIX;
        File live = new File(XposedBackend.DIR, pkg + XposedBackend.AMOLED_IDS);
        File copy = AmoledState.idsCopy(context, pkg);
        if (!enabled) {
            FabricatedValues.disable(Collections.singletonList(name));
            if (route != Route.MODULE && !live.exists()) return Switched.OFF;
            if (live.exists()) {
                try {
                    //noinspection ResultOfMethodCallIgnored
                    copy.getParentFile().mkdirs();
                    Files.write(copy.toPath(), Files.readAllBytes(live.toPath()));
                } catch (IOException | RuntimeException e) {
                    Log.w(TAG, pkg + ": could not keep its module colors: " + e);
                }
                removeById(pkg);
            }
            return restarted(context, pkg) ? Switched.OFF : Switched.RESTART_NEEDED;
        }
        switch (route) {
            case OVERLAY: {
                String file = definition(pkg);
                String out = Root.runCommand("n=0; [ -f '" + file + "' ] && { " +
                        fabricateAndEnable(pkg, name, file) + "}; echo n=$n");
                if (out != null && out.trim().endsWith("n=1")) return Switched.ON;
                break;
            }
            case MODULE: {
                String key = versionKey(context, pkg);
                // Never taken away (switched off and on again before the queue got to it)
                if (key != null && live.exists() && key.equals(readKey(live))) {
                    return Switched.ON;
                }
                if (key != null && copy.exists() && key.equals(readKey(copy)) &&
                        installIds(copy, pkg)) {
                    //noinspection ResultOfMethodCallIgnored
                    copy.delete();
                    return restarted(context, pkg) ? Switched.ON : Switched.RESTART_NEEDED;
                }
                break;
            }
            default:
                break;
        }
        // No colors to put back, or they belong to another version of the app
        AmoledJobs.applyOne(context, pkg);
        return Switched.REAPPLY;
    }

    /**
     * Restart after module colors changed, or mark the app "restart needed"
     *
     * @return True if restarted
     */
    private static boolean restarted(Context context, String pkg) {
        boolean restarted = restartIfSafe(context, pkg);
        AmoledState.update(context, s -> s.app(pkg).restartNeeded = !restarted);
        return restarted;
    }

    private static String readKey(File ids) {
        try (BufferedReader reader = new BufferedReader(new FileReader(ids))) {
            return XposedBackend.readKey(reader.readLine());
        } catch (IOException e) {
            return null;
        }
    }

    // Removing

    /**
     * Take AMOLED away from every app (root): the definitions and module color lists move to
     * {@code /data/system/substratum/snapshots/<epoch>/} (the newest three are kept, to put back
     * by hand), every AMOLED overlay is disabled, the module flag goes, and apps with module
     * colors are restarted if that is safe. System UI is restarted by the caller.
     */
    public static void removeAll(Context context) {
        String cache = XposedBackend.DIR;
        String ids = cache + "/*" + XposedBackend.AMOLED_IDS;
        String parked = ids + AmoledState.OFF;
        Root.runCommand("s=" + SNAPSHOTS + "/$(date +%s); mkdir -p \"$s\"; " +
                "for f in " + FabricatedValues.DIR + "/*" + SUFFIX + ".xml " + ids + " " + parked +
                "; do [ -f \"$f\" ] && mv -f \"$f\" \"$s/\"; done; rmdir \"$s\" 2>/dev/null; " +
                "(cd " + SNAPSHOTS + " && ls -1t | tail -n +4 | xargs rm -rf) >/dev/null 2>&1; " +
                "for o in $(cmd overlay list 2>/dev/null | grep -oE 'com\\.android\\.shell:[^ ]+" +
                SUFFIX.replace(".", "\\.") + "' | sort -u); do " +
                "cmd overlay disable --user 0 \"$o\" >/dev/null 2>&1; done; " +
                "rm -f " + ids + " " + parked + " " + cache + "/" + XposedBackend.AMOLED_FLAG);
        // Apps with module colors keep them until they restart
        AmoledState state = AmoledState.read(context);
        Map<String, Boolean> restarted = new HashMap<>();
        for (Map.Entry<String, AmoledState.App> entry : state.perApp.entrySet()) {
            AmoledState.App app = entry.getValue();
            if (app.route == Route.MODULE && app.userEnabled) {
                restarted.put(entry.getKey(), restartIfSafe(context, entry.getKey()));
            }
        }
        File[] copies = AmoledState.idsDir(context).listFiles();
        if (copies != null) {
            //noinspection ResultOfMethodCallIgnored
            for (File copy : copies) copy.delete();
        }
        // Nothing is applied any more: no change records either
        File[] records = AmoledChange.dir(context).listFiles();
        if (records != null) {
            //noinspection ResultOfMethodCallIgnored
            for (File record : records) record.delete();
        }
        AmoledState.update(context, s -> {
            s.enabled = false;
            for (Map.Entry<String, AmoledState.App> entry : s.perApp.entrySet()) {
                AmoledState.App app = entry.getValue();
                Boolean done = restarted.get(entry.getKey());
                if (done != null) app.restartNeeded = !done;
                none(app);
            }
        });
    }

    private static List<String> targets(Context context, AmoledRules rules) {
        List<String> list = new ArrayList<>();
        for (PackageInfo info : context.getPackageManager().getInstalledPackages(0)) {
            String pkg = info.packageName;
            if (!rules.covers(pkg)) continue;
            if (isOverlay(info)) continue; // overlays themselves
            list.add(pkg);
        }
        // Apps applied before that the rules no longer cover, or that are gone: cleaned up
        for (Map.Entry<String, AmoledState.App> entry :
                AmoledState.read(context).perApp.entrySet()) {
            if (entry.getValue().route != Route.NONE && !list.contains(entry.getKey())) {
                list.add(entry.getKey());
            }
        }
        // What people look at most goes first
        List<String> first = Arrays.asList("android", "com.android.systemui",
                "com.android.settings", "com.samsung.android.honeyboard", "com.android.vending");
        list.sort((a, b) -> {
            int ia = first.indexOf(a), ib = first.indexOf(b);
            return Integer.compare(ia < 0 ? first.size() : ia, ib < 0 ? first.size() : ib);
        });
        return list;
    }

    static boolean isOverlay(PackageInfo info) {
        try {
            return PackageInfo.class.getField("overlayTarget").get(info) != null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return info.packageName.contains(".overlay") ||
                    info.packageName.contains("auto_generated");
        }
    }

    // Reading an app's colors

    /**
     * Read an app's colors and decide each one (see {@link AmoledChange#night}): the dark-mode
     * value as the app shows it, the decision, and the light entry by the Monet-safe rules: a
     * light value that can change at runtime (a theme attribute, or a reference that reaches the
     * framework's Monet roles) gets none, nor does a color state list, and the Monet roles
     * themselves are never touched.
     *
     * @return The colors that change
     */
    static List<AmoledChange> compute(Context context, String pkg, AmoledRules rules)
            throws PackageManager.NameNotFoundException {
        return compute(context, pkg, rules, null);
    }

    /**
     * {@link #compute(Context, String, AmoledRules)}
     *
     * @param stockPrimary If not null, [0] is set to the app's stock basic_token_primary_50 (dark
     *                     mode), read with the colors
     */
    static List<AmoledChange> compute(Context context, String pkg, AmoledRules rules,
                                      int[] stockPrimary)
            throws PackageManager.NameNotFoundException {
        Resources[] res = packageResources(context, pkg);
        return compute(res[0], res[1], pkg, rules, stockPrimary, null, null);
    }

    /**
     * {@link #compute(Context, String, AmoledRules, int[])} with the resources given (thread safe
     * for resources of its own).
     *
     * @param res    Dark mode
     * @param dayRes Light mode
     * @param known  The app's color resource ids when known, or null to walk its resource table
     * @param found  If not null, receives the ids that were read
     */
    static List<AmoledChange> compute(Resources res, Resources dayRes, String pkg,
                                      AmoledRules rules, int[] stockPrimary, List<Integer> known,
                                      List<Integer> found) {
        TypedValue dayRaw = new TypedValue();
        TypedValue dayValue = new TypedValue();
        int primary50 = primary50(res, pkg);
        if (stockPrimary != null && stockPrimary.length > 0) stockPrimary[0] = primary50;

        List<AmoledChange> changes = new ArrayList<>();
        TypedValue tv = new TypedValue();
        List<Integer> ids = known != null ? known : colorIds(res, pkg);
        if (found != null) found.addAll(ids);
        Log.d(TAG, pkg + ": " + ids.size() + " color resources" +
                (known != null ? " (known)" : ""));
        for (int id : ids) {
            String name;
            Integer stockDark;
            AmoledPalette.Decision decision;
            try {
                name = res.getResourceEntryName(id);
                res.getValue(id, tv, true);
                stockDark = isColor(tv.type) ? tv.data : null;
                decision = AmoledChange.night(pkg, name, stockDark, rules, primary50);
                if (decision == null) continue;
                // A state list that does not inflate is left alone
                if (stockDark == null) res.getColorStateList(id, null);
            } catch (RuntimeException e) {
                continue; // Resources.NotFoundException, or a state list that does not inflate
            }
            Integer dayLiteral = null, stockLight = null;
            try {
                dayRes.getValue(id, dayValue, true);
                if (isColor(dayValue.type)) {
                    stockLight = dayValue.data;
                } else if (stockDark != null) {
                    continue; // a state list in light mode: leave the resource alone
                }
                // A light value that follows the wallpaper or the theme keeps following it: no
                // light entry for it. A fixed one is paired with the night value as before (a
                // night-only entry can make idmap2 refuse the whole overlay)
                dayRes.getValue(id, dayRaw, false);
                if (stockLight != null && (isColor(dayRaw.type) || fixed(dayRes, dayRaw))) {
                    dayLiteral = stockLight;
                }
            } catch (Resources.NotFoundException e) {
                // No light value (a resource of dark mode only): paired with its own dark value
                if (stockDark != null) dayLiteral = stockDark;
            }
            changes.add(AmoledChange.of(pkg, name, id, stockDark, decision, dayLiteral,
                    stockLight, rules, primary50));
        }
        return changes;
    }

    /**
     * Whether a light value always shows the same color: a literal, or a chain of references
     * that ends in one without passing a theme attribute or a Monet role of the framework
     * ({@code android:color/system_*}, which follow the wallpaper).
     */
    private static boolean fixed(Resources res, TypedValue raw) {
        TypedValue value = new TypedValue();
        value.setTo(raw);
        for (int hop = 0; hop < 12; hop++) {
            if (isColor(value.type)) return true;
            if (value.type != TypedValue.TYPE_REFERENCE || value.data == 0) return false;
            int ref = value.data;
            try {
                if ("android".equals(res.getResourcePackageName(ref)) &&
                        res.getResourceEntryName(ref).startsWith("system_")) {
                    return false;
                }
                res.getValue(ref, value, false);
            } catch (Resources.NotFoundException e) {
                return false;
            }
        }
        return false;
    }

    private static boolean isColor(int type) {
        return type >= TypedValue.TYPE_FIRST_COLOR_INT && type <= TypedValue.TYPE_LAST_COLOR_INT;
    }

    /**
     * The app's stock accent (basic_token_primary_50 in dark mode), or One UI blue.
     */
    private static int primary50(Resources res, String pkg) {
        try {
            int id = res.getIdentifier("basic_token_primary_50", "color", pkg);
            if (id != 0) return res.getColor(id, null);
        } catch (RuntimeException ignored) {
            // none
        }
        return AmoledPalette.ONEUI_BLUE;
    }

    /**
     * All color resource ids of a package, found by walking its resource table.
     */
    private static List<Integer> colorIds(Resources res, String pkg) {
        List<Integer> ids = new ArrayList<>();
        int[] packageIds = "android".equals(pkg) ? new int[]{0x01} :
                new int[]{0x7f, 0x7e, 0x7d, 0x7c};
        for (int packageId : packageIds) {
            for (int type = 1; type <= 0x60; type++) {
                int base = (packageId << 24) | (type << 16);
                String typeName = typeName(res, base);
                if (typeName == null) continue;
                if (!typeName.equals("color")) continue;
                // A library inside the APK can own this package id: only the app's own table
                if (!ownedBy(res, base, pkg)) break;
                int misses = 0;
                // Samsung strips unused resources, leaving long gaps in the id range
                for (int entry = 0; entry <= 0xffff && misses < 1024; entry++) {
                    int id = base | entry;
                    try {
                        if (!pkg.equals(res.getResourcePackageName(id))) break;
                        res.getResourceEntryName(id);
                        ids.add(id);
                        misses = 0;
                    } catch (Resources.NotFoundException e) {
                        misses++;
                    }
                }
                if (!ids.isEmpty()) return ids;
            }
        }
        return ids;
    }

    private static boolean ownedBy(Resources res, int base, String pkg) {
        for (int entry = 0; entry < 8; entry++) {
            try {
                return pkg.equals(res.getResourcePackageName(base | entry));
            } catch (Resources.NotFoundException ignored) {
                // sparse table
            }
        }
        return false;
    }

    private static String typeName(Resources res, int base) {
        for (int entry = 0; entry < 8; entry++) {
            try {
                return res.getResourceTypeName(base | entry);
            } catch (Resources.NotFoundException ignored) {
                // sparse table, try the next entry
            }
        }
        return null;
    }

    /**
     * AMOLED replacement for a dark-mode color, or null to keep it (see
     * {@link AmoledPalette#decide}).
     */
    static Integer amoled(int color, String name, AmoledRules rules) {
        return AmoledPalette.decide(color, name, rules).color;
    }
}
