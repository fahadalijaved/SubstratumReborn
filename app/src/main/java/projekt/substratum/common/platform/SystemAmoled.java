/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.common.platform;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.Log;
import android.util.TypedValue;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * "AMOLED for every app": turns the dark mode of every Samsung, Google and system app on the
 * device into true black, without a theme APK.
 * <p>
 * For each app it loads the app's own resources in dark mode, resolves what every color really
 * shows (following One UI's reference chains), applies the AMOLED rules and registers the result
 * as a night-only fabricated overlay (see {@link FabricatedValues}). Light mode is never touched,
 * and the overlay manager applies it live.
 */
public final class SystemAmoled {

    public static final String SUFFIX = ".RebornSystemAmoled";
    private static final String TAG = "SubstratumAmoled";
    private static final Pattern STATE = Pattern.compile(
            "ripple|press|focus|select|hover|drag|highlight|state|checked|activated");
    private static final Pattern TEXT = Pattern.compile(
            "text|title|label|hint|icon|summary|glyph|tint|foreground|_fg|(^|_)on_|inverse");
    // Light-theme variants keep their values: dark mode uses the _dark ones
    private static final Pattern LIGHT_VARIANT = Pattern.compile("_(light|day)$");
    private static final Pattern BG_STRICT = Pattern.compile(
            "bg|background|surface|container|fill|scrim|blur");
    private static final Pattern BG = Pattern.compile(
            "bg|background|surface|container|fill|panel|scrim|blur|window|keyboard|card|link");
    private static final Pattern LINE = Pattern.compile(
            "divider|outline|stroke|separator|_line|border|frame");
    private static final Pattern FLOAT = Pattern.compile(
            "float|popup|menu|dialog|search_?(bar|field|textfield|view)|navi|bottom_bar|toolbar|" +
                    "chip|fab|handle|toast|snackbar");
    private static final Pattern AREA = Pattern.compile("list|area|layout|screen|page|window");
    private static final Pattern SLIDER = Pattern.compile("slider|seekbar|seek_bar|progress");

    private SystemAmoled() {
    }

    public interface Progress {
        void onPackage(String packageName, int done, int total);
    }

    /**
     * Apply to every Samsung, Google and system app. Runs for a while: call off the main thread.
     *
     * @return {apps themed, colors changed}
     */
    public static int[] applyAll(Context context, AmoledRules rules, Progress progress) {
        return applyAll(context, rules, progress, null);
    }

    /**
     * @param only Packages to process, or null for every covered app
     */
    public static int[] applyAll(Context context, AmoledRules rules, Progress progress,
                                 List<String> only) {
        List<String> targets = only != null ? only : targets(context, rules);
        int apps = 0, colors = 0;
        for (int i = 0; i < targets.size(); i++) {
            String pkg = targets.get(i);
            if (progress != null) progress.onPackage(pkg, i, targets.size());
            try {
                // Read the stock colors: an active AMOLED overlay would hide them
                FabricatedValues.disable(java.util.Collections.singletonList(pkg + SUFFIX));
                awaitStock(context, pkg);
                List<String[]> items = compute(context, pkg, rules);
                removeById(pkg);
                if (items.isEmpty()) continue;
                int count;
                if (ambiguous(items)) {
                    // Obfuscated resource names (Play Store and other Google apps): an overlay
                    // names its targets, so only the LSPosed module, which works by id, can help
                    count = registerById(context, pkg, items);
                } else {
                    count = register(context, pkg, items);
                    // The app refuses overlays for these colors: the module by id instead
                    if (count == 0) count = registerById(context, pkg, items);
                }
                if (count > 0) {
                    apps++;
                    colors += count;
                }
            } catch (Throwable t) {
                Log.w(TAG, "Skipping " + pkg + ": " + t);
            }
        }
        // SystemUI code hooks of the LSPosed module (opaque shade, dark bar tracks)
        projekt.substratum.util.helpers.Root.runCommand("mkdir -p " + XposedBackend.DIR +
                " && chmod 755 " + XposedBackend.DIR + " && touch " + XposedBackend.DIR + "/" +
                XposedBackend.AMOLED_FLAG + " && chmod 644 " + XposedBackend.DIR + "/" +
                XposedBackend.AMOLED_FLAG + " && restorecon -R " + XposedBackend.DIR);
        return new int[]{apps, colors};
    }

    /**
     * Disabling an overlay reaches this process asynchronously (for the framework, through a
     * configuration change). Wait until a value of the previous definition reads stock again.
     */
    private static void awaitStock(Context context, String pkg) {
        String line = projekt.substratum.util.helpers.Root.runCommand(
                "grep -m1 'config=\"night\"' /data/system/substratum/" + pkg + SUFFIX + ".xml");
        if (line == null) return;
        java.util.regex.Matcher m = Pattern.compile(
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

    private static Resources nightResources(Context context, String pkg)
            throws PackageManager.NameNotFoundException {
        Context app = context.createPackageContext(pkg, Context.CONTEXT_IGNORE_SECURITY);
        Configuration night = new Configuration(app.getResources().getConfiguration());
        night.uiMode = (night.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) |
                Configuration.UI_MODE_NIGHT_YES;
        return app.createConfigurationContext(night).getResources();
    }

    /**
     * Register and enable an app's overlay. Apps that declare overlayable resources only accept
     * those: one other entry and the whole overlay is refused (STATE_NO_IDMAP). In that case the
     * entries idmap2 rejected are dropped and the rest is registered again.
     *
     * @return Number of colors applied
     */
    private static int register(Context context, String pkg, List<String[]> items) {
        String name = pkg + SUFFIX;
        List<String> names = new ArrayList<>();
        names.add(name);
        for (int attempt = 0; attempt < 3 && !items.isEmpty(); attempt++) {
            if (FabricatedValues.registerItems(context, pkg, name, items) == 0) return 0;
            FabricatedValues.enable(names);
            String state = projekt.substratum.util.helpers.Root.runCommand(
                    "cmd overlay dump com.android.shell:" + name + " | grep mState");
            if (state == null || !state.contains("NO_IDMAP")) return items.size() / 2;
            java.util.Set<String> rejected = rejectedByIdmap(name);
            if (rejected.isEmpty()) return 0;
            List<String[]> kept = new ArrayList<>();
            for (String[] item : items) if (!rejected.contains(item[0])) kept.add(item);
            if (kept.size() == items.size()) return 0;
            Log.i(TAG, pkg + ": " + rejected.size() + " colors are not overlayable, retrying");
            items = kept;
        }
        return 0;
    }

    private static java.util.Set<String> rejectedByIdmap(String overlayName) {
        java.util.Set<String> rejected = new java.util.HashSet<>();
        String log = projekt.substratum.util.helpers.Root.runCommand(
                "logcat -d -s idmap2d:* | grep 'shell-" + overlayName + "-' | " +
                        "grep -oE \"overlay resource '[^']+'\" | sort -u");
        if (log == null) return rejected;
        java.util.regex.Matcher m = Pattern.compile("overlay resource '([^']+)'").matcher(log);
        while (m.find()) rejected.add(m.group(1));
        return rejected;
    }

    /**
     * True when several resources share a name, which happens when names are obfuscated.
     */
    private static boolean ambiguous(List<String[]> items) {
        java.util.Map<String, String> seen = new java.util.HashMap<>();
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
     *
     * @return Number of colors handed over
     */
    private static int registerById(Context context, String pkg, List<String[]> items) {
        String key;
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(pkg, 0);
            key = XposedBackend.versionKey(info.applicationInfo.sourceDir,
                    androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info));
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            Log.w(TAG, pkg + ": no version to key the module's colors with: " + e);
            return 0;
        }
        java.util.Map<String, String[]> byId = new java.util.LinkedHashMap<>();
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
        StringBuilder content = new StringBuilder("# key=").append(key).append('\n');
        for (String[] line : byId.values()) {
            content.append(line[0]).append(' ').append(line[1]).append(' ').append(line[2]);
            if (!line[3].isEmpty()) content.append(' ').append(line[3]);
            content.append('\n');
        }
        java.io.File local = new java.io.File(context.getCacheDir(), pkg + XposedBackend.AMOLED_IDS);
        try (java.io.FileWriter writer = new java.io.FileWriter(local)) {
            writer.write(content.toString());
        } catch (java.io.IOException e) {
            return 0;
        }
        String file = XposedBackend.DIR + '/' + pkg + XposedBackend.AMOLED_IDS;
        String out = projekt.substratum.util.helpers.Root.runCommand(
                "mkdir -p " + XposedBackend.DIR + " && chmod 755 " + XposedBackend.DIR +
                        " && cp -f '" + local.getAbsolutePath() + "' '" + file + "'" +
                        " && chmod 644 '" + file + "' && restorecon '" + file + "'; echo rc=$?");
        //noinspection ResultOfMethodCallIgnored
        local.delete();
        if (out == null || !out.trim().endsWith("rc=0")) return 0;
        // The colors load when the app starts (a stop on purpose is no crash loop)
        projekt.substratum.util.helpers.Root.runCommand(
                XposedBackend.forgetStartsCommand(pkg) + "; am force-stop " + pkg);
        Log.i(TAG, pkg + ": " + byId.size() + " colors handed to the LSPosed module by id");
        return byId.size();
    }

    private static void removeById(String pkg) {
        String file = XposedBackend.DIR + '/' + pkg + XposedBackend.AMOLED_IDS;
        if (new java.io.File(file).exists()) {
            projekt.substratum.util.helpers.Root.runCommand("rm -f '" + file + "'");
        }
    }

    /**
     * Fabricated overlays do not survive a reboot on One UI: register again everything that was
     * applied, from the definitions kept in /data/system/substratum. AMOLED for every app comes
     * back while its flag is set (except apps switched off); Substratum overlays with fabricated
     * values come back while they are enabled.
     *
     * @return Number of overlays registered
     */
    public static int restoreAll() {
        String dir = FabricatedValues.DIR;
        String cache = XposedBackend.DIR;
        StringBuilder script = new StringBuilder("n=0; ");
        if (new java.io.File(cache, XposedBackend.AMOLED_FLAG).exists()) {
            String listing = projekt.substratum.util.helpers.Root.runCommand(
                    "ls " + dir + " | grep '" + SUFFIX + ".xml$'");
            java.util.Set<String> disabled = disabledApps();
            if (listing != null) {
                for (String file : listing.split("\\s+")) {
                    if (!file.endsWith(SUFFIX + ".xml")) continue;
                    String name = file.substring(0, file.length() - 4);
                    String target = name.substring(0, name.length() - SUFFIX.length());
                    if (disabled.contains(target)) continue;
                    script.append(fabricateAndEnable(target, name, dir + '/' + file));
                }
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
        String out = projekt.substratum.util.helpers.Root.runCommand(script.toString());
        int restored = 0;
        if (out != null) {
            java.util.regex.Matcher m = Pattern.compile("restored=(\\d+)").matcher(out);
            if (m.find()) restored = Integer.parseInt(m.group(1));
        }
        Log.i(TAG, "restored " + restored + " fabricated overlays after boot");
        return restored;
    }

    private static String fabricateAndEnable(String target, String name, String file) {
        return "cmd overlay fabricate --target '" + target + "' --name '" + name + "' --file '" +
                file + "' >/dev/null 2>&1 && cmd overlay enable --user 0 'com.android.shell:" +
                name + "' >/dev/null 2>&1 && n=$((n+1)); ";
    }

    /**
     * Apps the user switched off in Substratum: left out when applying and restoring.
     */
    public static java.util.Set<String> disabledApps() {
        return new java.util.HashSet<>(projekt.substratum.Substratum.getPreferences()
                .getStringSet(AmoledRules.PREF_DISABLED_APPS, new java.util.HashSet<>()));
    }

    /**
     * How an app gets its AMOLED colors
     */
    public enum Kind {OVERLAY, MODULE, UNSUPPORTED}

    public static final class AppState {
        public final String packageName;
        public final Kind kind;
        public final boolean enabled;

        AppState(String packageName, Kind kind, boolean enabled) {
            this.packageName = packageName;
            this.kind = kind;
            this.enabled = enabled;
        }
    }

    /**
     * Every app AMOLED for every app has colors for (registered overlays and module lists),
     * including apps switched off.
     */
    public static List<AppState> listApps() {
        java.util.Map<String, AppState> apps = new java.util.TreeMap<>();
        java.util.Set<String> disabled = disabledApps();
        String overlays = projekt.substratum.util.helpers.Root.runCommand(
                "cmd overlay list | grep '" + SUFFIX + "'");
        java.util.Set<String> byModule = new java.util.HashSet<>();
        String[] files = new java.io.File(XposedBackend.DIR).list();
        if (files != null) {
            for (String file : files) {
                if (file.endsWith(XposedBackend.AMOLED_IDS)) {
                    byModule.add(file.substring(0, file.length() - XposedBackend.AMOLED_IDS.length()));
                } else if (file.endsWith(XposedBackend.AMOLED_IDS + OFF)) {
                    byModule.add(file.substring(0, file.length() -
                            (XposedBackend.AMOLED_IDS + OFF).length()));
                }
            }
        }
        if (overlays != null) {
            java.util.regex.Matcher m = Pattern.compile(
                    "\\[(.)\\] com\\.android\\.shell:(\\S+)" + Pattern.quote(SUFFIX) + "|" +
                            "--- com\\.android\\.shell:(\\S+)" + Pattern.quote(SUFFIX))
                    .matcher(overlays);
            while (m.find()) {
                if (m.group(2) != null) {
                    String pkg = m.group(2);
                    if (byModule.contains(pkg)) continue;
                    apps.put(pkg, new AppState(pkg, Kind.OVERLAY, "x".equals(m.group(1))));
                } else {
                    String pkg = m.group(3);
                    if (!byModule.contains(pkg)) {
                        apps.put(pkg, new AppState(pkg, Kind.UNSUPPORTED, false));
                    }
                }
            }
        }
        for (String pkg : byModule) {
            apps.put(pkg, new AppState(pkg, Kind.MODULE, !disabled.contains(pkg)));
        }
        for (String pkg : disabled) {
            if (!apps.containsKey(pkg)) apps.put(pkg, new AppState(pkg, Kind.OVERLAY, false));
        }
        return new ArrayList<>(apps.values());
    }

    private static final String OFF = ".off";

    /**
     * Switch one app's AMOLED colors on or off, remembered for applying and restoring.
     */
    public static void setAppEnabled(String pkg, boolean enabled) {
        java.util.Set<String> disabled = disabledApps();
        if (enabled) disabled.remove(pkg);
        else disabled.add(pkg);
        projekt.substratum.Substratum.getPreferences().edit()
                .putStringSet(AmoledRules.PREF_DISABLED_APPS, disabled).apply();
        String ids = XposedBackend.DIR + '/' + pkg + XposedBackend.AMOLED_IDS;
        String name = pkg + SUFFIX;
        String definition = FabricatedValues.DIR + '/' + name + ".xml";
        if (enabled) {
            projekt.substratum.util.helpers.Root.runCommand(
                    "[ -f '" + ids + OFF + "' ] && mv -f '" + ids + OFF + "' '" + ids + "'; " +
                            "n=0; [ -f '" + definition + "' ] && { " +
                            fabricateAndEnable(pkg, name, definition) + "}; true");
        } else {
            projekt.substratum.util.helpers.Root.runCommand(
                    "[ -f '" + ids + "' ] && mv -f '" + ids + "' '" + ids + OFF + "'; " +
                            "cmd overlay disable --user 0 'com.android.shell:" + name +
                            "' >/dev/null 2>&1; true");
        }
        // Apps with module colors load them when they start (a stop on purpose is no crash loop)
        projekt.substratum.util.helpers.Root.runCommand(
                XposedBackend.forgetStartsCommand(pkg) + "; am force-stop " + pkg);
    }

    public static void removeAll(Context context) {
        projekt.substratum.util.helpers.Root.runCommand(
                "rm -f " + XposedBackend.DIR + "/*" + XposedBackend.AMOLED_IDS + " " +
                        XposedBackend.DIR + "/" + XposedBackend.AMOLED_FLAG);
        List<String> names = new ArrayList<>();
        for (PackageInfo info : context.getPackageManager().getInstalledPackages(0)) {
            names.add(info.packageName + SUFFIX);
        }
        FabricatedValues.disable(names);
    }

    private static List<String> targets(Context context, AmoledRules rules) {
        List<String> list = new ArrayList<>();
        java.util.Set<String> disabled = disabledApps();
        for (PackageInfo info : context.getPackageManager().getInstalledPackages(0)) {
            String pkg = info.packageName;
            if (!rules.covers(pkg) || disabled.contains(pkg)) continue;
            if (isOverlay(info)) continue; // overlays themselves
            list.add(pkg);
        }
        // What people look at most goes first
        List<String> first = java.util.Arrays.asList("android", "com.android.systemui",
                "com.android.settings", "com.samsung.android.honeyboard", "com.android.vending");
        list.sort((a, b) -> {
            int ia = first.indexOf(a), ib = first.indexOf(b);
            return Integer.compare(ia < 0 ? first.size() : ia, ib < 0 ? first.size() : ib);
        });
        return list;
    }

    private static boolean isOverlay(PackageInfo info) {
        try {
            return PackageInfo.class.getField("overlayTarget").get(info) != null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return info.packageName.contains(".overlay") ||
                    info.packageName.contains("auto_generated");
        }
    }

    /**
     * @return fabricated items {target, type, value, config} for the app's dark mode
     */
    static List<String[]> compute(Context context, String pkg, AmoledRules rules)
            throws PackageManager.NameNotFoundException {
        Context app = context.createPackageContext(pkg, Context.CONTEXT_IGNORE_SECURITY);
        Configuration night = new Configuration(app.getResources().getConfiguration());
        night.uiMode = (night.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) |
                Configuration.UI_MODE_NIGHT_YES;
        Resources res = app.createConfigurationContext(night).getResources();
        Configuration day = new Configuration(night);
        day.uiMode = (day.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) |
                Configuration.UI_MODE_NIGHT_NO;
        Resources dayRes = app.createConfigurationContext(day).getResources();
        TypedValue dayValue = new TypedValue();

        List<String[]> items = new ArrayList<>();
        TypedValue tv = new TypedValue();
        List<Integer> ids = colorIds(res, pkg);
        Log.d(TAG, pkg + ": " + ids.size() + " color resources");
        for (int id : ids) {
            String name;
            try {
                name = res.getResourceEntryName(id);
                res.getValue(id, tv, true);
            } catch (Resources.NotFoundException e) {
                continue;
            }
            Integer mapped = rules.explicit(pkg, name);
            boolean plain = tv.type >= TypedValue.TYPE_FIRST_COLOR_INT &&
                    tv.type <= TypedValue.TYPE_LAST_COLOR_INT;
            // Color state lists keep their states, unless the rules name a color for them
            if (!plain && mapped == null) continue;
            if (mapped == null) mapped = amoled(tv.data, name, rules);
            if (mapped == null || plain && mapped == tv.data) continue;
            // A night-only entry can fail to map (idmap2 confuses it with similarly named
            // resources), so every night value is paired with the stock light value
            int light = plain ? tv.data : res.getColorStateList(id, null).getDefaultColor();
            try {
                dayRes.getValue(id, dayValue, true);
                if (dayValue.type >= TypedValue.TYPE_FIRST_COLOR_INT &&
                        dayValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                    light = dayValue.data;
                } else if (!plain) {
                    light = dayRes.getColorStateList(id, null).getDefaultColor();
                } else {
                    continue; // a state list in light mode: leave the resource alone
                }
            } catch (Resources.NotFoundException ignored) {
                // no light value
            }
            String hexId = String.format("0x%08x", id);
            items.add(new String[]{"color/" + name, "28",
                    String.format("0x%08x", light), "", hexId});
            items.add(new String[]{"color/" + name, "28",
                    String.format("0x%08x", mapped), "night", hexId,
                    plain ? String.format("0x%08x", tv.data) : ""});
        }
        return items;
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
     * AMOLED replacement for a dark-mode color, or null to keep it. Same rules as
     * tools/oneui-reference/gen_amoled.py.
     */
    static Integer amoled(int color, String name, AmoledRules rules) {
        int a = color >>> 24, r = (color >> 16) & 0xff, g = (color >> 8) & 0xff, b = color & 0xff;
        int hi = Math.max(r, Math.max(g, b)), lo = Math.min(r, Math.min(g, b));
        boolean state = STATE.matcher(name).find();
        boolean text = TEXT.matcher(name).find() && !BG_STRICT.matcher(name).find();
        boolean bg = BG.matcher(name).find();
        boolean floating = FLOAT.matcher(name).find() && !AREA.matcher(name).find();
        if (text || state || LIGHT_VARIANT.matcher(name).find()) return null;
        boolean line = LINE.matcher(name).find();
        // Outlines of floating things (popups, menus, dialogs, bars) stay as a thin dark border so
        // black popups still separate from a black page
        if (line && floating) return rules.outline;
        if (a < 0xff) {
            // see-through white or tinted surfaces would show as grey or tint on black
            // (over blur that would leave the bare, untinted blur: the AMOLED blur instead)
            if (a <= 0x66 && hi > 0x60 && (bg || line))
                return name.contains("blur") ? rules.blur : 0;
            if (a <= 0x40 && lo >= 0xc0) return 0;
            // see-through dark tints over blur: an AMOLED blur
            if (hi <= 0x60 && a >= 0x20 && (bg || floating)) return rules.blur;
            return null;
        }
        if (name.contains("thumb")) return hi <= 0x80 ? rules.thumb : null;
        if (name.contains("track") || SLIDER.matcher(name).find() && bg)
            return hi <= 0x80 ? rules.slider : null;
        int surface = floating ? rules.floating : rules.background;
        if (hi <= 0x30) return surface;
        // dark greys (cards, containers, lines); mid greys are secondary text and stay
        if (hi - lo <= 0x18 && hi <= 0x52) return surface;
        return null;
    }
}
