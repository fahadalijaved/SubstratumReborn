/*
 * Copyright (c) 2016-2019 Projekt Substratum
 * This file is part of Substratum.
 *
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.common.platform;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import projekt.substratum.common.References;
import projekt.substratum.util.helpers.Root;

/**
 * Theme engine for devices where no overlay route is open (OneUI 9 rejects overlay APKs that
 * are not preinstalled by Samsung, and rejects foreign-signed overlays installed to /data).
 * <p>
 * Compiled overlay APKs are kept in {@link #DIR}, which every app may read. Substratum's own
 * LSPosed module ({@link projekt.substratum.xposed.XposedEntry}) loads the enabled ones inside
 * each target app and redirects the matching resources to them by name.
 * <p>
 * Per overlay {@code <name>} the directory holds:
 * <ul>
 * <li>{@code <name>.apk}: the compiled overlay</li>
 * <li>{@code <name>.idx}: {@code target=<package>}, {@code package=<overlay package>}, then
 * one {@code type/name} per resource the overlay carries</li>
 * <li>{@code <name>.enabled}: present while the overlay is applied</li>
 * </ul>
 */
public class XposedBackend {

    public static final String DIR = "/data/resource-cache/substratum";
    public static final String APK = ".apk";
    public static final String INDEX = ".idx";
    public static final String ENABLED = ".enabled";
    public static final String FABRICATED = "fabricated=true";
    /**
     * Index header of an overlay whose theme applies in dark mode only (see
     * {@link ClassicDarkMode}): the module applies what it carries only while the configuration
     * is night
     */
    public static final String DARK_ONLY = "dark-only=true";
    /**
     * Marker of an overlay that is installed but held back: the module does not load it (a part
     * that crashed System UI, paused from the app)
     */
    public static final String HELD = ".enabled.held";
    /**
     * In the target's device-protected data: what the module attributed the last crash to
     * ({@code overlay=}, {@code names=}, {@code time=}, {@code error=}). Cleared with the guard.
     */
    public static final String CRASH_FILE = "reborn_guard_crash";
    /**
     * Prefix of the file in the target's device-protected data that lists what the module did not
     * forward for an overlay ({@code reborn_skipped_<overlay>}: {@code type/name reason} lines)
     */
    public static final String SKIPPED_PREFIX = "reborn_skipped_";
    /**
     * Prefix of an index line whose color, bool or integer the fabricated overlay could not
     * carry (an unresolvable reference): the module keeps applying it.
     */
    public static final String KEEP = "keep:";
    /**
     * Suffix of a file being stored, renamed into place once complete
     */
    private static final String PART = ".part";
    /**
     * AMOLED colors for one app, applied by resource id. The first line is
     * {@code # key=<versionKey>} of the app version the ids were read from; the module ignores
     * a file whose key does not match the installed app. Then one line per color:
     * {@code id light night [stockNight|csl]}.
     */
    public static final String AMOLED_IDS = ".amoled.ids";
    /**
     * First line of an AMOLED id list, followed by the {@link #versionKey}
     */
    public static final String KEY_HEADER = "# key=";
    /**
     * Present while "AMOLED for every app" is applied: turns on the module's SystemUI code hooks
     */
    public static final String AMOLED_FLAG = "amoled-hooks.on";
    /**
     * Kill switch: while {@code hooks.off} exists the module installs no hooks and forwards no
     * resources; {@code hooks.off.<package>} does the same for one app. Read as each process
     * starts.
     */
    public static final String HOOKS_OFF = "hooks.off";
    /**
     * Prefix of the module's crash-loop guard files in each guarded app's device-protected data
     * directory: {@code reborn_guard_starts} (recent start times) and {@code reborn_guard_tripped}
     * (present while the guard keeps the app stock).
     */
    public static final String GUARD_FILES = "reborn_guard_";
    /**
     * Settings.Global key System UI writes once the module has set up (or skipped) its hooks:
     * {@code v=<versionCode>;t=<millis>;qs=<0|1>;layout=<0|1>;battery=<0|1>;guard=<ok|tripped|off>}
     */
    public static final String STATUS_KEY = "reborn_module_status";
    private static final String TAG = "SubstratumXposed";

    /**
     * Replaced with {@code return true} by the module when LSPosed has it active for Substratum.
     */
    public static boolean isModuleActive() {
        return false;
    }

    /**
     * Identifies an installed app version: an update or reinstall changes the APK path or the
     * version code. Written by the app into the AMOLED id lists and compared by the module.
     */
    public static String versionKey(String sourceDir, long versionCode) {
        return sourceDir + "|" + versionCode;
    }

    /**
     * The version key in the first line of an AMOLED id list, or null if it has none.
     */
    public static String readKey(String firstLine) {
        if (firstLine == null) return null;
        String line = firstLine.trim();
        if (!line.startsWith(KEY_HEADER)) return null;
        String key = line.substring(KEY_HEADER.length()).trim();
        return key.isEmpty() ? null : key;
    }

    /**
     * Turn the module's kill switch on or off (root). Takes effect as processes start: restart
     * System UI and the themed apps.
     *
     * @return True, if the switch is now in the requested state
     */
    public static boolean setHooksPaused(boolean paused) {
        String file = DIR + '/' + HOOKS_OFF;
        if (paused) {
            Root.runCommand("mkdir -p " + DIR + " && chmod 755 " + DIR + " && touch " + file +
                    " && chmod 644 " + file + " && restorecon " + file);
        } else {
            Root.runCommand("rm -f " + file);
        }
        return areHooksPaused() == paused;
    }

    public static boolean areHooksPaused() {
        return new File(DIR, HOOKS_OFF).exists();
    }

    /**
     * Clear the crash-loop guard of one app (root), so the module hooks it again from its next
     * start.
     */
    public static void resetGuard(String pkg) {
        if (pkg == null || !pkg.matches("[A-Za-z0-9_.]+")) return;
        Root.runCommand("rm -f /data/user_de/0/" + pkg + "/" + GUARD_FILES + "*");
    }

    /**
     * Root shell command that forgets an app's recent starts, to run right before Substratum
     * restarts or force-stops it on purpose: such restarts are not a crash loop, however quickly
     * they follow each other. A tripped guard stays tripped.
     */
    public static String forgetStartsCommand(String pkg) {
        if (pkg == null || !pkg.matches("[A-Za-z0-9_.]+")) return "true";
        return "rm -f /data/user_de/0/" + pkg + "/" + GUARD_FILES + "starts";
    }

    /**
     * Store a freshly compiled overlay. Does not enable it.
     *
     * @return True, if stored
     */
    public static boolean install(Context context, String apkPath, String name) {
        return install(context, apkPath, name, null);
    }

    /**
     * @param workArea The overlay's compilation folder; its colors, bools and integers are also
     *                 registered as a fabricated overlay (see {@link FabricatedValues}). Null to
     *                 leave everything to the LSPosed module.
     */
    public static boolean install(Context context, String apkPath, String name, String workArea) {
        return install(context, apkPath, name, workArea, false, null);
    }

    /**
     * @param darkOnly Apply the overlay in dark mode only ({@link ClassicDarkMode})
     * @param report   Receives what was moved or left out, may be null
     */
    public static boolean install(Context context, String apkPath, String name, String workArea,
                                  boolean darkOnly, ClassicReport report) {
        String target = archiveMetadata(context, apkPath, References.metadataOverlayTarget);
        if (target == null) {
            Log.e(TAG, "Overlay has no target metadata: " + apkPath);
            return false;
        }
        PackageInfo archive = context.getPackageManager().getPackageArchiveInfo(apkPath, 0);
        if (archive == null) {
            Log.e(TAG, "Overlay cannot be parsed: " + apkPath);
            return false;
        }
        List<String> resources = listResources(context, apkPath);
        if (resources.isEmpty()) {
            Log.e(TAG, "Overlay carries no resources: " + apkPath);
            return false;
        }
        Set<String> leftToModule = new HashSet<>();
        boolean fabricated = workArea != null &&
                FabricatedValues.register(context, new File(workArea), target, name,
                        leftToModule, darkOnly, report) > 0;
        if (!fabricated) ClassicRoute.forgetDefinition(context, name);
        if (report != null && fabricated) {
            for (String resource : resources) {
                if (leftToModule.contains(resource)) report.toModule.add(resource);
            }
        }
        String index = ClassicRoute.index(target, archive.packageName, fabricated, darkOnly,
                resources, leftToModule);
        String base = DIR + '/' + name;
        if (!writeFiles(context, name, apkPath, index)) {
            Log.e(TAG, "Could not store " + name);
            return false;
        }
        return new File(base + APK).exists() && new File(base + INDEX).exists();
    }

    /**
     * Copies the overlay (when {@code apkPath} is given) and its index next to the old files,
     * then renames them over: an enabled overlay's APK is mapped by running processes (every
     * scoped app for the framework, System UI, the target), and rewriting it in place would pull
     * the pages from under them (SIGBUS). A rename leaves them on the old file until they
     * restart.
     */
    private static boolean writeFiles(Context context, String name, String apkPath,
                                      String index) {
        File local = new File(context.getCacheDir(), name + INDEX);
        try (FileWriter writer = new FileWriter(local)) {
            writer.write(index);
        } catch (IOException e) {
            Log.e(TAG, "Could not write overlay index", e);
            return false;
        }
        String base = DIR + '/' + name;
        String apkNew = base + APK + PART;
        String indexNew = base + INDEX + PART;
        StringBuilder command = new StringBuilder("mkdir -p " + DIR + " && chmod 755 " + DIR);
        if (apkPath != null) {
            command.append(" && cp -f '").append(apkPath).append("' '").append(apkNew)
                    .append("' && chmod 644 '").append(apkNew).append('\'');
        }
        command.append(" && cp -f '").append(local.getAbsolutePath()).append("' '")
                .append(indexNew).append("' && chmod 644 '").append(indexNew).append('\'');
        if (apkPath != null) {
            command.append(" && mv -f '").append(apkNew).append("' '").append(base).append(APK)
                    .append('\'');
        }
        command.append(" && mv -f '").append(indexNew).append("' '").append(base).append(INDEX)
                .append("' ; rm -f '").append(apkNew).append("' '").append(indexNew)
                .append("' ; restorecon -R ").append(DIR);
        Root.runCommand(command.toString());
        //noinspection ResultOfMethodCallIgnored
        local.delete();
        return true;
    }

    /**
     * The stored index of an overlay, or null
     */
    static String readIndex(String name) {
        File file = new File(DIR, name + INDEX);
        if (!file.isFile()) return null;
        try {
            return new String(java.nio.file.Files.readAllBytes(file.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Replace a stored overlay's index (root), keeping its APK
     */
    static void writeIndex(Context context, String name, String content) {
        writeFiles(context, name, null, content);
    }

    /**
     * Whether the index of a stored overlay says it has a fabricated half
     */
    public static boolean isFabricated(String name) {
        return headerHas(name, FABRICATED);
    }

    /**
     * Whether a stored overlay applies in dark mode only
     */
    public static boolean isDarkOnly(String name) {
        return headerHas(name, DARK_ONLY);
    }

    private static boolean headerHas(String name, String marker) {
        try (BufferedReader reader = new BufferedReader(
                new java.io.FileReader(DIR + '/' + name + INDEX))) {
            String line;
            while ((line = reader.readLine()) != null && line.contains("=")) {
                if (line.trim().equals(marker)) return true;
            }
        } catch (IOException ignored) {
            // not stored
        }
        return false;
    }

    public static void enable(Iterable<String> names) {
        StringBuilder command = new StringBuilder();
        for (String name : names) {
            command.append("[ -f '").append(DIR).append('/').append(name).append(APK)
                    .append("' ] && touch '").append(DIR).append('/').append(name)
                    .append(ENABLED).append("' && chmod 644 '").append(DIR).append('/')
                    .append(name).append(ENABLED).append("'; ");
        }
        if (command.length() == 0) return;
        Root.runCommand(command + "restorecon -R " + DIR);
        FabricatedValues.enable(names);
    }

    /**
     * {@link #enable}, then the overlays idmap2 refused as a whole are registered again without
     * what it rejected, which the module applies instead ({@link ClassicRoute#healRefused}).
     */
    public static void enable(Context context, Iterable<String> names) {
        enable(names);
        try {
            ClassicRoute.healRefused(context, names);
        } catch (RuntimeException e) {
            Log.e(TAG, "Could not check the refused overlays", e);
        }
    }

    public static void disable(Iterable<String> names) {
        StringBuilder command = new StringBuilder();
        for (String name : names) {
            command.append("rm -f '").append(DIR).append('/').append(name).append(ENABLED)
                    .append("'; ");
        }
        if (command.length() != 0) Root.runCommand(command.toString());
        FabricatedValues.disable(names);
    }

    public static void uninstall(Iterable<String> names) {
        StringBuilder command = new StringBuilder();
        for (String name : names) {
            String base = DIR + '/' + name;
            command.append("rm -f '").append(base).append(APK).append("' '").append(base)
                    .append(INDEX).append("' '").append(base).append(ENABLED).append("' '")
                    .append(base).append(HELD).append("' '")
                    .append(FabricatedValues.DIR).append('/').append(name).append(".xml'; ");
        }
        if (command.length() != 0) Root.runCommand(command.toString());
        FabricatedValues.disable(names);
    }

    /**
     * @return Names of all stored overlays
     */
    public static List<String> listAll() {
        List<String> list = new ArrayList<>();
        String[] files = new File(DIR).list();
        if (files == null) return list;
        for (String file : files) {
            if (file.endsWith(APK)) list.add(file.substring(0, file.length() - APK.length()));
        }
        return list;
    }

    public static boolean isEnabled(String name) {
        return new File(DIR + '/' + name + ENABLED).exists();
    }

    /**
     * Installed, but held back: the module does not load it until it is released
     */
    public static boolean isHeld(String name) {
        return new File(DIR + '/' + name + HELD).exists();
    }

    /**
     * Hold enabled overlays back (their marker becomes {@value #HELD}): the module stops loading
     * them from the next start of their targets, the rest of the theme stays. Their fabricated
     * halves are switched off as well. Root.
     *
     * @return The overlays that are held now
     */
    public static List<String> hold(Collection<String> names) {
        List<String[]> moves = ClassicRoute.markerMoves(listing(), names, true);
        List<String> held = new ArrayList<>();
        StringBuilder command = new StringBuilder();
        for (String[] move : moves) {
            command.append("mv -f '").append(DIR).append('/').append(move[0]).append("' '")
                    .append(DIR).append('/').append(move[1]).append("'; ");
            held.add(move[0].substring(0, move[0].length() - ENABLED.length()));
        }
        if (command.length() != 0) Root.runCommand(command.toString());
        FabricatedValues.disable(held);
        return held;
    }

    /**
     * Release held overlays: the module loads them again from the next start of their targets.
     * Root.
     */
    public static List<String> release(Context context, Collection<String> names) {
        List<String[]> moves = ClassicRoute.markerMoves(listing(), names, false);
        List<String> released = new ArrayList<>();
        StringBuilder command = new StringBuilder();
        for (String[] move : moves) {
            command.append("mv -f '").append(DIR).append('/').append(move[0]).append("' '")
                    .append(DIR).append('/').append(move[1]).append("'; ");
            released.add(move[0].substring(0, move[0].length() - HELD.length()));
        }
        if (command.length() != 0) Root.runCommand(command.toString());
        if (!released.isEmpty()) enable(context, released);
        return released;
    }

    /**
     * Drop the held marker of overlays (they are then simply off). Root.
     */
    public static void forgetHeld(Collection<String> names) {
        StringBuilder command = new StringBuilder();
        for (String name : names) {
            command.append("rm -f '").append(DIR).append('/').append(name).append(HELD)
                    .append("'; ");
        }
        if (command.length() != 0) Root.runCommand(command.toString());
    }

    private static List<String> listing() {
        String[] files = new File(DIR).list();
        return files == null ? new ArrayList<>() : java.util.Arrays.asList(files);
    }

    /**
     * What the module attributed the last crash of an app to, read from the app's
     * device-protected data (root), or null
     */
    public static CrashAttribution.Recorded readCrash(String pkg) {
        if (pkg == null || !pkg.matches("[A-Za-z0-9_.]+")) return null;
        String out = Root.runCommand("cat /data/user_de/0/" + pkg + '/' + CRASH_FILE +
                " 2>/dev/null");
        return CrashAttribution.Recorded.parse(out);
    }

    public static boolean isStored(String name) {
        return new File(DIR + '/' + name + APK).exists();
    }

    /**
     * @return Target package of a stored overlay, or null
     */
    public static String getTarget(String name) {
        try (BufferedReader reader = new BufferedReader(
                new java.io.FileReader(DIR + '/' + name + INDEX))) {
            String first = reader.readLine();
            if (first != null && first.startsWith("target=")) return first.substring(7);
        } catch (IOException ignored) {
        }
        return null;
    }

    /**
     * @return Targets that have more than one enabled overlay
     */
    public static List<String> targetsWithMultipleEnabled() {
        Map<String, Integer> counts = new HashMap<>();
        for (String name : listAll()) {
            if (!isEnabled(name)) continue;
            String target = getTarget(name);
            if (target != null) counts.merge(target, 1, Integer::sum);
        }
        List<String> list = new ArrayList<>();
        counts.forEach((target, count) -> {
            if (count > 1) list.add(target);
        });
        return list;
    }

    /**
     * Restart the apps the given overlays target so they reload their resources.
     */
    public static void restartTargets(Context context, Iterable<String> names) {
        Set<String> targets = new HashSet<>();
        for (String name : names) {
            String target = getTarget(name);
            if (target != null) targets.add(target);
        }
        StringBuilder command = new StringBuilder();
        // Framework overlays apply when each scoped app process starts; SystemUI is the one that
        // has to be restarted to see them, other apps pick them up on their next launch
        boolean systemUi = targets.contains("android");
        for (String target : targets) {
            if ("com.android.systemui".equals(target)) {
                systemUi = true;
            } else if (SystemAmoled.safeToRestart(context, target)) {
                command.append(forgetStartsCommand(target)).append("; am force-stop ")
                        .append(target).append("; ");
            } else if (!"android".equals(target)) {
                // telephony, the keyboard, the launcher...: from their next start
                Log.i(TAG, "skip restart " + target);
            }
        }
        if (command.length() != 0) Root.runCommand(command.toString());
        if (systemUi) ThemeManager.restartSystemUI(context);
    }

    /**
     * Metadata of a stored overlay, for overlays that are not installed packages.
     */
    public static String getMetadata(Context context, String name, String key) {
        PackageInfo info = getArchiveInfo(context, name, PackageManager.GET_META_DATA);
        if (info == null || info.applicationInfo == null) return null;
        Bundle metaData = info.applicationInfo.metaData;
        if (metaData == null) return null;
        // aapt stores numeric-looking values as numbers, so do not assume strings
        Object value = metaData.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    /**
     * Package info parsed from a stored overlay's APK, or null if it is not stored.
     */
    public static PackageInfo getArchiveInfo(Context context, String name, int flags) {
        String path = DIR + '/' + name + APK;
        if (!new File(path).exists()) return null;
        return context.getPackageManager().getPackageArchiveInfo(path, flags);
    }

    private static String archiveMetadata(Context context, String apkPath, String key) {
        PackageInfo info = context.getPackageManager()
                .getPackageArchiveInfo(apkPath, PackageManager.GET_META_DATA);
        if (info == null) return null;
        ApplicationInfo appInfo = info.applicationInfo;
        if (appInfo == null) return null;
        Bundle metaData = appInfo.metaData;
        return metaData != null ? metaData.getString(key) : null;
    }

    /**
     * List the resources an overlay carries as {@code type/name}.
     * <p>
     * Walks the overlay's own resource ids (package 0x7f, types from 1, entries from 0) through
     * the framework's resource APIs, so no dump tool is needed.
     */
    private static List<String> listResources(Context context, String apkPath) {
        List<String> list = new ArrayList<>();
        PackageManager pm = context.getPackageManager();
        PackageInfo info = pm.getPackageArchiveInfo(apkPath, 0);
        if (info == null || info.applicationInfo == null) return list;
        info.applicationInfo.sourceDir = apkPath;
        info.applicationInfo.publicSourceDir = apkPath;
        android.content.res.Resources res;
        try {
            res = pm.getResourcesForApplication(info.applicationInfo);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Could not open overlay resources", e);
            return list;
        }
        int emptyTypes = 0;
        for (int type = 1; type <= 0xff && emptyTypes < 4; type++) {
            int found = 0;
            for (int entry = 0; entry <= 0xffff; entry++) {
                int id = 0x7f000000 | (type << 16) | entry;
                try {
                    list.add(res.getResourceTypeName(id) + '/' + res.getResourceEntryName(id));
                    found++;
                } catch (android.content.res.Resources.NotFoundException e) {
                    break;
                }
            }
            emptyTypes = found == 0 ? emptyTypes + 1 : 0;
        }
        return list;
    }
}
