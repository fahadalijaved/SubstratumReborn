/*
 * Copyright (c) 2016-2019 Projekt Substratum
 * This file is part of Substratum.
 *
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.xposed;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.XModuleResources;
import android.content.res.XResources;
import android.util.DisplayMetrics;
import android.util.TypedValue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import projekt.substratum.common.platform.XposedBackend;

/**
 * LSPosed entry point. For every app in scope, loads the enabled Substratum overlays that target
 * it and redirects their resources by name, the way the Overlay Manager would.
 */
public class XposedEntry implements IXposedHookZygoteInit, IXposedHookLoadPackage,
        IXposedHookInitPackageResources {

    private static final String TAG = "SubstratumXposed: ";
    private static final String SELF = "projekt.substratum";
    private static final String FRAMEWORK = "android";
    private static final String SYSTEMUI = "com.android.systemui";
    // Plain values replaced directly, per target Resources, so they can follow light/dark changes
    private static final Map<Resources, List<ValueReplacement>> VALUES = new WeakHashMap<>();
    private static final Map<Resources, List<int[]>> IDS = new WeakHashMap<>();
    private static boolean configHooked;

    private static final class ValueReplacement {
        final String target, type, name;
        final XModuleResources modRes;
        final int id;

        ValueReplacement(String target, String type, String name, XModuleResources modRes, int id) {
            this.target = target;
            this.type = type;
            this.name = name;
            this.modRes = modRes;
            this.id = id;
        }
    }
    // Value types that can be forwarded; styles, attrs and ids cannot be replaced this way
    private static final Set<String> FORWARDABLE = new HashSet<>(Arrays.asList(
            "anim", "animator", "array", "bool", "color", "dimen", "drawable", "font",
            "fraction", "integer", "interpolator", "layout", "mipmap", "plurals", "raw",
            "string", "xml"));

    /**
     * Framework resources can only be replaced system-wide, which LSPosed allows here. It runs in
     * every process the module is scoped to, so framework overlays reach exactly those apps.
     */
    @Override
    public void initZygote(StartupParam startupParam) {
        hookConfigurationChanges();
        for (String name : enabledOverlays()) {
            try {
                apply(null, FRAMEWORK, name);
            } catch (Throwable t) {
                XposedBridge.log(TAG + "skipping " + name + " for framework: " + t);
            }
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (SYSTEMUI.equals(lpparam.packageName)) {
            try {
                BatteryHook.hook(lpparam.classLoader);
            } catch (Throwable t) {
                XposedBridge.log(TAG + "battery hook failed: " + t);
            }
            return;
        }
        if (!SELF.equals(lpparam.packageName)) return;
        XposedHelpers.findAndHookMethod(XposedBackend.class.getName(), lpparam.classLoader,
                "isModuleActive", XC_MethodReplacement.returnConstant(true));
    }

    @Override
    public void handleInitPackageResources(XC_InitPackageResources.InitPackageResourcesParam
                                                   resparam) {
        String target = resparam.packageName;
        if (FRAMEWORK.equals(target)) return; // handled in initZygote
        try {
            applyAmoledIds(resparam.res, target);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "skipping AMOLED colors for " + target + ": " + t);
        }
        for (String name : enabledOverlays()) {
            try {
                apply(resparam.res, target, name);
            } catch (Throwable t) {
                // Never take the target app down because of one overlay
                XposedBridge.log(TAG + "skipping " + name + " for " + target + ": " + t);
            }
        }
    }

    /**
     * AMOLED colors handed over by resource id (apps with obfuscated resource names, or apps
     * that refuse overlays): {id, light, night} per color, switched with night mode.
     */
    private static void applyAmoledIds(XResources res, String target) throws IOException {
        File file = new File(XposedBackend.DIR, target + XposedBackend.AMOLED_IDS);
        if (!file.exists()) return;
        List<int[]> colors = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split(" ");
                if (parts.length != 3) continue;
                colors.add(new int[]{(int) Long.parseLong(parts[0].substring(2), 16),
                        (int) Long.parseLong(parts[1].substring(2), 16),
                        (int) Long.parseLong(parts[2].substring(2), 16)});
            }
        }
        synchronized (IDS) {
            IDS.put(res, colors);
        }
        applyIds(res, colors);
        XposedBridge.log(TAG + "AMOLED: " + colors.size() + " colors by id in " + target);
    }

    private static void applyIds(XResources res, List<int[]> colors) {
        boolean night = (res.getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES;
        for (int[] c : colors) {
            try {
                res.setReplacement(c[0], Integer.valueOf(night ? c[2] : c[1]));
            } catch (Throwable ignored) {
                // not in this app's table
            }
        }
    }

    private static java.util.List<String> enabledOverlays() {
        java.util.List<String> names = new java.util.ArrayList<>();
        String[] files = new File(XposedBackend.DIR).list();
        if (files == null) return names;
        Arrays.sort(files);
        for (String file : files) {
            if (file.endsWith(XposedBackend.ENABLED)) {
                names.add(file.substring(0, file.length() - XposedBackend.ENABLED.length()));
            }
        }
        return names;
    }

    /**
     * @param res Target resources, or null for the framework (system-wide replacement)
     */
    private static void apply(XResources res, String target, String name) throws IOException {
        String base = XposedBackend.DIR + '/' + name;
        try (BufferedReader reader = new BufferedReader(
                new FileReader(base + XposedBackend.INDEX))) {
            String targetLine = reader.readLine();
            String packageLine = reader.readLine();
            if (targetLine == null || !targetLine.equals("target=" + target)) return;
            if (packageLine == null || !packageLine.startsWith("package=")) return;
            String overlayPackage = packageLine.substring(8);

            XModuleResources modRes = XModuleResources.createInstance(
                    base + XposedBackend.APK, res);
            if (SYSTEMUI.equals(target)) BatteryHook.offer(modRes, overlayPackage);
            int replaced = 0;
            boolean fabricated = false;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.equals(XposedBackend.FABRICATED)) {
                    fabricated = true;
                    continue;
                }
                int slash = line.indexOf('/');
                if (slash <= 0) continue;
                String type = line.substring(0, slash);
                String resName = line.substring(slash + 1);
                if (!FORWARDABLE.contains(type)) continue;
                // The system Overlay Manager already applies these natively
                if (fabricated && (type.equals("color") || type.equals("bool") ||
                        type.equals("integer"))) continue;
                int id = modRes.getIdentifier(resName, type, overlayPackage);
                if (id == 0) continue;
                try {
                    // Plain values are handed over as values: forwarding them leaks the
                    // overlay's resource ids into the target, which some code paths then look
                    // up in the target's own table and crash on
                    Object value = directValue(modRes, type, id);
                    Object replacement = value != null ? value : modRes.fwd(id);
                    if (res == null) {
                        XResources.setSystemWideReplacement(target, type, resName, replacement);
                    } else {
                        res.setReplacement(target, type, resName, replacement);
                        if (value != null) remember(res,
                                new ValueReplacement(target, type, resName, modRes, id));
                    }
                    replaced++;
                } catch (Throwable ignored) {
                    // The target does not have this resource
                }
            }
            XposedBridge.log(TAG + name + " replaced " + replaced + " resources in " + target);
        }
    }

    /**
     * The value of a plain resource (color, dimen, bool, integer) in the overlay's current
     * configuration, or null for anything that has to be forwarded (files, color state lists).
     */
    private static Object directValue(Resources modRes, String type, int id) {
        TypedValue tv = new TypedValue();
        try {
            modRes.getValue(id, tv, true);
        } catch (Resources.NotFoundException e) {
            return null;
        }
        switch (type) {
            case "color":
                return tv.type >= TypedValue.TYPE_FIRST_COLOR_INT &&
                        tv.type <= TypedValue.TYPE_LAST_COLOR_INT ? Integer.valueOf(tv.data) : null;
            case "dimen":
                return tv.type == TypedValue.TYPE_DIMENSION ? new XResources.DimensionReplacement(
                        TypedValue.complexToFloat(tv.data),
                        tv.data & TypedValue.COMPLEX_UNIT_MASK) : null;
            case "bool":
                return tv.type == TypedValue.TYPE_INT_BOOLEAN ? Boolean.valueOf(tv.data != 0) : null;
            case "integer":
                return tv.type >= TypedValue.TYPE_FIRST_INT && tv.type <= TypedValue.TYPE_LAST_INT
                        ? Integer.valueOf(tv.data) : null;
            default:
                return null;
        }
    }

    private static void remember(Resources res, ValueReplacement replacement) {
        synchronized (VALUES) {
            List<ValueReplacement> list = VALUES.get(res);
            if (list == null) VALUES.put(res, list = new ArrayList<>());
            list.add(replacement);
        }
    }

    /**
     * Direct values do not switch with the configuration by themselves, so re-read them from the
     * overlay whenever a themed Resources object changes night mode (or any other qualifier).
     */
    private static void hookConfigurationChanges() {
        if (configHooked) return;
        configHooked = true;
        try {
            XposedHelpers.findAndHookMethod(Resources.class, "updateConfiguration",
                    Configuration.class, DisplayMetrics.class,
                    "android.content.res.CompatibilityInfo", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (!(param.thisObject instanceof XResources)) return;
                            List<int[]> ids;
                            synchronized (IDS) {
                                ids = IDS.get(param.thisObject);
                            }
                            if (ids != null) applyIds((XResources) param.thisObject, ids);
                            List<ValueReplacement> list;
                            synchronized (VALUES) {
                                list = VALUES.get(param.thisObject);
                                if (list == null) return;
                                list = new ArrayList<>(list);
                            }
                            reapply((XResources) param.thisObject, list);
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + "could not follow configuration changes: " + t);
        }
    }

    private static void reapply(XResources res, List<ValueReplacement> list) {
        Configuration config = res.getConfiguration();
        DisplayMetrics metrics = res.getDisplayMetrics();
        Set<XModuleResources> updated = new HashSet<>();
        for (ValueReplacement r : list) {
            try {
                if (updated.add(r.modRes)) r.modRes.updateConfiguration(config, metrics);
                Object value = directValue(r.modRes, r.type, r.id);
                if (value != null) res.setReplacement(r.target, r.type, r.name, value);
            } catch (Throwable ignored) {
                // Keep the previous value
            }
        }
    }
}
