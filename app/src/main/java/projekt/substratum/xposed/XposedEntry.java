/*
 * Copyright (c) 2016-2019 Projekt Substratum
 * This file is part of Substratum.
 *
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.xposed;

import android.content.res.XModuleResources;
import android.content.res.XResources;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
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
        for (String name : enabledOverlays()) {
            try {
                apply(resparam.res, target, name);
            } catch (Throwable t) {
                // Never take the target app down because of one overlay
                XposedBridge.log(TAG + "skipping " + name + " for " + target + ": " + t);
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
            String line;
            while ((line = reader.readLine()) != null) {
                int slash = line.indexOf('/');
                if (slash <= 0) continue;
                String type = line.substring(0, slash);
                String resName = line.substring(slash + 1);
                if (!FORWARDABLE.contains(type)) continue;
                int id = modRes.getIdentifier(resName, type, overlayPackage);
                if (id == 0) continue;
                try {
                    if (res == null) {
                        XResources.setSystemWideReplacement(target, type, resName,
                                modRes.fwd(id));
                    } else {
                        res.setReplacement(target, type, resName, modRes.fwd(id));
                    }
                    replaced++;
                } catch (Throwable ignored) {
                    // The target does not have this resource
                }
            }
            XposedBridge.log(TAG + name + " replaced " + replaced + " resources in " + target);
        }
    }
}
