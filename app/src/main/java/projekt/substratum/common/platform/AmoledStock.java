/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.common.platform;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.util.Log;
import android.util.TypedValue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import projekt.substratum.util.helpers.Root;

/**
 * An app's stock colors, read without disabling anything: its resources are opened from a copy of
 * its ApplicationInfo that leaves out AMOLED's fabricated overlay, so the overlay stays on while
 * the colors are read and the app never flickers.
 * <p>
 * Android lists an app's overlays twice: {@code resourceDirs} holds the overlay APKs (the phone's
 * own static overlays, which stay: the app shows them in stock too) and {@code overlayPaths} holds
 * those and the fabricated ones ({@code .frro}). The copy keeps both without AMOLED's paths; when
 * that still reads AMOLED colors (another path naming), a second copy has no overlayPaths at all
 * (every fabricated overlay left out).
 * <p>
 * Known limitation: colors of the app that reference framework colors still see the framework
 * overlay, as they did before (android is always read the classic way, first, so every app reads
 * the framework as it is applied). The framework itself ({@code android}) is never read here.
 * <p>
 * Each read is checked against what AMOLED applied before ({@link #isClean}): when the copy still
 * shows an AMOLED value, the caller falls back to the classic read (disable, wait, read).
 */
final class AmoledStock {

    private static final String TAG = "RebornAmoled";
    private static volatile boolean broken;
    private static volatile Field resourceDirs;
    private static volatile Field overlayPaths;
    private static volatile boolean fieldsRead;

    private AmoledStock() {
    }

    /**
     * Whether clean reads can work on this device at all.
     */
    static boolean available() {
        return Build.VERSION.SDK_INT >= 33 && !broken;
    }

    /**
     * Night and day resources of an app without AMOLED's overlay.
     *
     * @param allFabricated Leave out every fabricated overlay, not only AMOLED's
     * @return {night, day}, or null when not possible (the framework, an old Android, a field
     * that cannot be reached): read the classic way then
     */
    static Resources[] read(Context context, String pkg, boolean allFabricated) {
        if (!available() || "android".equals(pkg)) return null;
        PackageManager pm = context.getPackageManager();
        try {
            ApplicationInfo real = pm.getApplicationInfo(pkg, 0);
            ApplicationInfo clean = withoutAmoled(real, allFabricated);
            if (clean == null) return null;
            Configuration base = context.getResources().getConfiguration();
            Configuration night = mode(base, Configuration.UI_MODE_NIGHT_YES);
            Configuration day = mode(base, Configuration.UI_MODE_NIGHT_NO);
            if (Build.VERSION.SDK_INT >= 33) {
                return new Resources[]{pm.getResourcesForApplication(clean, night),
                        pm.getResourcesForApplication(clean, day)};
            }
            Resources res = pm.getResourcesForApplication(clean);
            //noinspection deprecation
            return new Resources[]{
                    new Resources(res.getAssets(), res.getDisplayMetrics(), night),
                    new Resources(res.getAssets(), res.getDisplayMetrics(), day)};
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            Log.w(TAG, pkg + ": no clean read: " + e);
            return null;
        }
    }

    static Configuration mode(Configuration base, int night) {
        Configuration config = new Configuration(base);
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | night;
        return config;
    }

    /**
     * A copy of an ApplicationInfo with the overlay APKs and without AMOLED's overlay (or without
     * any fabricated overlay), or null when the hidden list of overlay APKs cannot be read
     * (leaving it out would drop the phone's own overlays and read other colors than stock).
     */
    private static ApplicationInfo withoutAmoled(ApplicationInfo real, boolean allFabricated) {
        readFields();
        if (resourceDirs == null) {
            broken = true;
            Log.w(TAG, "ApplicationInfo.resourceDirs is out of reach: classic reads only");
            return null;
        }
        String[] apks;
        try {
            apks = filter((String[]) resourceDirs.get(real), true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            return null;
        }
        ApplicationInfo copy = new ApplicationInfo(real);
        try {
            resourceDirs.set(copy, apks);
            if (overlayPaths != null) {
                overlayPaths.set(copy, allFabricated ? null :
                        filter((String[]) overlayPaths.get(real), false));
                return copy;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // below
        }
        // overlayPaths is out of reach: a fresh ApplicationInfo never had it
        ApplicationInfo fresh = new ApplicationInfo();
        fresh.packageName = real.packageName;
        fresh.uid = real.uid;
        fresh.flags = real.flags;
        fresh.enabled = real.enabled;
        fresh.processName = real.processName;
        fresh.targetSdkVersion = real.targetSdkVersion;
        fresh.minSdkVersion = real.minSdkVersion;
        fresh.sourceDir = real.sourceDir;
        fresh.publicSourceDir = real.publicSourceDir;
        fresh.splitNames = real.splitNames;
        fresh.splitSourceDirs = real.splitSourceDirs;
        fresh.splitPublicSourceDirs = real.splitPublicSourceDirs;
        fresh.sharedLibraryFiles = real.sharedLibraryFiles;
        fresh.nativeLibraryDir = real.nativeLibraryDir;
        fresh.dataDir = real.dataDir;
        try {
            resourceDirs.set(fresh, apks);
            return fresh;
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            return null;
        }
    }

    private static synchronized void readFields() {
        if (fieldsRead) return;
        fieldsRead = true;
        try {
            Field f = ApplicationInfo.class.getField("resourceDirs");
            f.setAccessible(true);
            resourceDirs = f;
        } catch (ReflectiveOperationException | RuntimeException e) {
            try {
                Field f = ApplicationInfo.class.getDeclaredField("resourceDirs");
                f.setAccessible(true);
                resourceDirs = f;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // stays null
            }
        }
        try {
            Field f = ApplicationInfo.class.getDeclaredField("overlayPaths");
            f.setAccessible(true);
            overlayPaths = f;
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.i(TAG, "ApplicationInfo.overlayPaths is out of reach, using fresh copies");
        }
    }

    /**
     * Paths without AMOLED's overlay
     *
     * @param apksOnly Also without any other fabricated overlay
     */
    private static String[] filter(String[] paths, boolean apksOnly) {
        if (paths == null) return null;
        List<String> kept = new ArrayList<>(paths.length);
        for (String path : paths) {
            if (path == null || path.contains(SystemAmoled.SUFFIX)) continue;
            if (apksOnly && path.endsWith(".frro")) continue;
            kept.add(path);
        }
        return kept.isEmpty() ? null : kept.toArray(new String[0]);
    }

    // The self-check

    /**
     * Whether a clean read really shows stock: up to three colors AMOLED applied before (from
     * the app's change record, else from its overlay definition, as the classic read's wait
     * does) must not read as their AMOLED value.
     *
     * @return False if the read still shows the AMOLED overlay
     */
    static boolean isClean(Context context, String pkg, Resources night) {
        List<String[]> applied = new ArrayList<>(); // {name, amoled, stock or null}
        AmoledChange.Record record = AmoledChange.read(context, pkg);
        if (record != null) {
            for (AmoledChange change : record.changes) {
                if (!AmoledChange.COLOR.equals(change.type) || change.stockDark == null) continue;
                if (change.stockDark == change.amoled) continue;
                applied.add(new String[]{change.name, String.valueOf(change.amoled)});
                if (applied.size() == 3) break;
            }
        }
        if (applied.isEmpty()) {
            // The quick root shell: never waits behind a commit
            String line = Root.runQuickCommand("grep -m1 'config=\"night\"' '" +
                    FabricatedValues.DIR + '/' + pkg + SystemAmoled.SUFFIX + ".xml' 2>/dev/null");
            if (line == null) return true;
            Matcher m = Pattern.compile(
                    "target=\"color/([^\"]+)\".*value=\"0x([0-9a-fA-F]{8})\"").matcher(line);
            if (!m.find()) return true;
            applied.add(new String[]{m.group(1),
                    String.valueOf((int) Long.parseLong(m.group(2), 16))});
        }
        TypedValue value = new TypedValue();
        for (String[] color : applied) {
            try {
                int id = night.getIdentifier(color[0], "color", pkg);
                if (id == 0) continue;
                night.getValue(id, value, true);
                if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT &&
                        value.type <= TypedValue.TYPE_LAST_COLOR_INT &&
                        value.data == Integer.parseInt(color[1])) {
                    return false;
                }
            } catch (RuntimeException ignored) {
                // gone in this version
            }
        }
        return true;
    }
}
