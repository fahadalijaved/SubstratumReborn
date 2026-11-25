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
        boolean fabricated = workArea != null &&
                FabricatedValues.register(context, new File(workArea), target, name) > 0;
        File index = new File(context.getCacheDir(), name + INDEX);
        try (FileWriter writer = new FileWriter(index)) {
            writer.write("target=" + target + '\n');
            writer.write("package=" + archive.packageName + '\n');
            // Tells the module to leave colors, bools and integers to the fabricated overlay
            if (fabricated) writer.write(FABRICATED + '\n');
            for (String resource : resources) writer.write(resource + '\n');
        } catch (IOException e) {
            Log.e(TAG, "Could not write overlay index", e);
            return false;
        }
        String base = DIR + '/' + name;
        Root.runCommand("mkdir -p " + DIR + " && chmod 755 " + DIR +
                " && cp -f '" + apkPath + "' '" + base + APK + "'" +
                " && cp -f '" + index.getAbsolutePath() + "' '" + base + INDEX + "'" +
                " && chmod 644 '" + base + APK + "' '" + base + INDEX + "'" +
                " && restorecon -R " + DIR);
        //noinspection ResultOfMethodCallIgnored
        index.delete();
        return new File(base + APK).exists() && new File(base + INDEX).exists();
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
