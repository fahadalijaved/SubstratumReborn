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
import projekt.substratum.common.platform.QsOptions;
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
    // Night or not as last applied, per themed Resources object (guarded by VALUES)
    private static final Map<Resources, Boolean> APPLIED_NIGHT = new WeakHashMap<>();
    private static final ThreadLocal<Boolean> REAPPLYING = new ThreadLocal<>();
    private static boolean configHooked;

    /**
     * What follows a themed Resources object's configuration: a plain value (re-read), a
     * forwarded file of a dark mode only overlay (on at night, stock by day), or only the
     * overlay's resources (kept in the same configuration, for the drawables they hand out)
     */
    private static final class ValueReplacement {
        static final int VALUE = 0, FILE = 1, FOLLOW = 2;
        final int kind;
        final String target, type, name;
        final XModuleResources modRes;
        final int id;
        final boolean darkOnly;

        ValueReplacement(int kind, String target, String type, String name,
                         XModuleResources modRes, int id, boolean darkOnly) {
            this.kind = kind;
            this.target = target;
            this.type = type;
            this.name = name;
            this.modRes = modRes;
            this.id = id;
            this.darkOnly = darkOnly;
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
        // Kill switch (hooks.off, hooks.off.android)
        if (!HookGuard.allowFramework()) return;
        SafeForward.inZygote(true);
        try {
            for (String name : enabledOverlays()) {
                try {
                    apply(null, FRAMEWORK, name, true);
                } catch (Throwable t) {
                    XposedBridge.log(TAG + "skipping " + name + " for framework: " + t);
                }
            }
        } finally {
            // Apps forked from here find their own folders
            SafeForward.inZygote(false);
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        String pkg = lpparam.packageName;
        if (SELF.equals(pkg)) {
            // Optional: when Reborn is in its own scope it sees the module at once, also while
            // the kill switch is on. Without it, RebornStatus.moduleEnabled reads System UI's
            // heartbeat and LSPosed's database
            try {
                XposedHelpers.findAndHookMethod(XposedBackend.class.getName(),
                        lpparam.classLoader, "isModuleActive",
                        XC_MethodReplacement.returnConstant(true));
            } catch (Throwable t) {
                XposedBridge.log(TAG + "module status for Substratum failed: " + t);
            }
        }
        boolean systemUi = SYSTEMUI.equals(pkg);
        // Only the app the process belongs to: packages loaded into another app's process (Play
        // services modules, WebView) must not install their color table there
        if (!systemUi && !lpparam.isFirstApplication) return;
        boolean mainSystemUi = systemUi && SYSTEMUI.equals(lpparam.processName);
        // Kill switch and crash loop guard, for this package and for the app the process
        // belongs to
        String guard = HookGuard.state(pkg,
                lpparam.appInfo != null ? lpparam.appInfo.deviceProtectedDataDir : null,
                lpparam.processName, lpparam.isFirstApplication);
        if (!HookGuard.OK.equals(guard)) {
            if (mainSystemUi) ModuleStatus.reportSkipped(guard);
            return;
        }
        if (!HookGuard.processAllowed()) return;
        // Which theme part a crash of this app points at, for the app's "pause that part"
        if (lpparam.isFirstApplication || mainSystemUi) {
            SafeForward.installRecorder(lpparam.appInfo != null ?
                    lpparam.appInfo.deviceProtectedDataDir : null);
        }
        if (systemUi) {
            boolean battery = false, qs = false, layout = false;
            QsOptions options;
            try {
                options = QsOptions.read();
            } catch (Throwable t) {
                options = new QsOptions();
            }
            try {
                BatteryHook.hook(lpparam.classLoader);
                battery = true;
            } catch (Throwable t) {
                XposedBridge.log(TAG + "battery hook failed: " + t);
            }
            try {
                QsAmoledHook.hook(lpparam.classLoader);
                qs = (options.shade || options.track) &&
                        new File(XposedBackend.DIR, XposedBackend.AMOLED_FLAG).exists();
            } catch (Throwable t) {
                XposedBridge.log(TAG + "QS AMOLED hook failed: " + t);
            }
            try {
                QsLayoutHook.hook(lpparam.classLoader);
                layout = options.aspect > 0;
            } catch (Throwable t) {
                XposedBridge.log(TAG + "QS layout hook failed: " + t);
            }
            if (mainSystemUi) ModuleStatus.reportWhenCreated(qs, layout, battery);
            return;
        }
        try {
            AppAmoledHook.hook(lpparam);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "AMOLED colors for " + pkg + " failed: " + t);
        }
    }

    @Override
    public void handleInitPackageResources(XC_InitPackageResources.InitPackageResourcesParam
                                                   resparam) {
        String target = resparam.packageName;
        if (FRAMEWORK.equals(target)) return; // handled in initZygote
        // Kill switch and crash loop guard, for the target and for the app the process belongs
        // to (System UI loads many apps' resources)
        if (!HookGuard.OK.equals(HookGuard.state(target)) || !HookGuard.processAllowed()) return;
        List<String> enabled = enabledOverlays();
        // Only in processes the guard lets through (framework values are replaced system-wide
        // and do not need it)
        if (!enabled.isEmpty()) hookConfigurationChanges();
        if (!enabled.isEmpty()) SafeForward.installRecorder(null);
        boolean framework = frameworkAllowed();
        for (String name : enabled) {
            try {
                // Framework overlays of dark mode only themes come here too, per app: a
                // system-wide replacement cannot follow each app's night mode
                apply(resparam.res, target, name, framework);
            } catch (Throwable t) {
                // Never take the target app down because of one overlay
                XposedBridge.log(TAG + "skipping " + name + " for " + target + ": " + t);
            }
        }
    }

    // The framework's kill switch, read once per process (here for every package whose
    // resources the process loads); null until then, and never set in the zygote
    private static volatile Boolean frameworkAllowed;

    private static boolean frameworkAllowed() {
        Boolean allowed = frameworkAllowed;
        if (allowed == null) frameworkAllowed = allowed = HookGuard.allowFramework();
        return allowed;
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
     * @param res       Target resources, or null for the framework (system-wide replacement)
     * @param framework Framework overlays of dark mode only themes may be applied to these
     *                  resources (the kill switch for the framework is off)
     */
    private static void apply(XResources res, String target, String name, boolean framework)
            throws IOException {
        String base = XposedBackend.DIR + '/' + name;
        try (BufferedReader reader = new BufferedReader(
                new FileReader(base + XposedBackend.INDEX))) {
            String targetLine = reader.readLine();
            String packageLine = reader.readLine();
            if (targetLine == null || !targetLine.startsWith("target=")) return;
            if (packageLine == null || !packageLine.startsWith("package=")) return;
            String overlayTarget = targetLine.substring(7);
            boolean frameworkHere = res != null && framework && FRAMEWORK.equals(overlayTarget);
            if (!overlayTarget.equals(target) && !frameworkHere) return;
            String overlayPackage = packageLine.substring(8);

            // The header (key=value lines), then one type/name per resource
            boolean fabricated = false;
            boolean darkOnly = false;
            String line = reader.readLine();
            while (line != null && line.indexOf('=') > 0) {
                if (line.equals(XposedBackend.FABRICATED)) fabricated = true;
                if (line.equals(XposedBackend.DARK_ONLY)) darkOnly = true;
                line = reader.readLine();
            }
            // A framework overlay: system-wide from the zygote, unless it is dark mode only,
            // which goes to each app's resources instead
            if (frameworkHere && !overlayTarget.equals(target) && !darkOnly) return;
            if (res == null && darkOnly) return;
            target = overlayTarget;
            List<String> lines = new ArrayList<>();
            for (; line != null; line = reader.readLine()) lines.add(line);

            XModuleResources modRes = XModuleResources.createInstance(
                    base + XposedBackend.APK, res);
            if (SYSTEMUI.equals(target)) BatteryHook.offer(modRes, overlayPackage);
            boolean night = res == null || SafeForward.isNight(res);
            if (res != null) {
                remember(res, new ValueReplacement(ValueReplacement.FOLLOW, target, null, null,
                        modRes, 0, darkOnly));
                synchronized (VALUES) {
                    APPLIED_NIGHT.put(res, night);
                }
            }
            int replaced = 0;
            for (String entry : lines) {
                // Values the fabricated overlay could not carry stay with the module
                boolean keep = entry.startsWith(XposedBackend.KEEP);
                if (keep) entry = entry.substring(XposedBackend.KEEP.length());
                int slash = entry.indexOf('/');
                if (slash <= 0) continue;
                String type = entry.substring(0, slash);
                String resName = entry.substring(slash + 1);
                if (!FORWARDABLE.contains(type)) continue;
                // The system Overlay Manager already applies these natively
                if (fabricated && !keep && (type.equals("color") || type.equals("bool") ||
                        type.equals("integer"))) continue;
                int id = modRes.getIdentifier(resName, type, overlayPackage);
                if (id == 0) continue;
                try {
                    if (type.equals("drawable") || type.equals("mipmap")) {
                        // Checked on its first lookup: one the app would cast to another class,
                        // or that does not load, stays stock
                        set(res, target, type, resName, new SafeForward.Drawables(modRes, id, name,
                                overlayPackage, entry, darkOnly, res == null));
                        SafeForward.forwarded(name, overlayPackage, entry, null, null);
                        replaced++;
                        continue;
                    }
                    // Plain values are handed over as values: forwarding them leaks the
                    // overlay's resource ids into the target, which some code paths then look
                    // up in the target's own table and crash on
                    Object value = directValue(modRes, type, id);
                    if (value != null) {
                        if (res == null) {
                            XResources.setSystemWideReplacement(target, type, resName, value);
                        } else {
                            if (!darkOnly || night) res.setReplacement(target, type, resName, value);
                            remember(res, new ValueReplacement(ValueReplacement.VALUE, target,
                                    type, resName, modRes, id, darkOnly));
                        }
                        replaced++;
                        continue;
                    }
                    if (!SafeForward.fileOpens(modRes, name, type, resName, id)) continue;
                    if (res == null) {
                        XResources.setSystemWideReplacement(target, type, resName, modRes.fwd(id));
                    } else {
                        if (!darkOnly || night) {
                            res.setReplacement(target, type, resName, modRes.fwd(id));
                        }
                        if (darkOnly) remember(res, new ValueReplacement(ValueReplacement.FILE,
                                target, type, resName, modRes, id, true));
                    }
                    SafeForward.forwarded(name, overlayPackage, entry, null, null);
                    replaced++;
                } catch (Throwable ignored) {
                    // The target does not have this resource
                }
            }
            XposedBridge.log(TAG + name + " replaced " + replaced + " resources in " + target +
                    (frameworkHere ? " (this app)" : "") + (darkOnly ? ", dark mode only" : ""));
        }
    }

    private static void set(XResources res, String target, String type, String name,
                            Object replacement) {
        if (res == null) {
            XResources.setSystemWideReplacement(target, type, name, replacement);
        } else {
            res.setReplacement(target, type, name, replacement);
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
    private static synchronized void hookConfigurationChanges() {
        if (configHooked) return;
        configHooked = true;
        try {
            XposedHelpers.findAndHookMethod(Resources.class, "updateConfiguration",
                    Configuration.class, DisplayMetrics.class,
                    "android.content.res.CompatibilityInfo", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (!(param.thisObject instanceof XResources)) return;
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
        // An app's configuration changes reach its ResourcesImpl (or a new one is set), not
        // Resources.updateConfiguration: follow night mode there as well, or dark mode only
        // parts would keep the mode the process started in (System UI does not restart when
        // the phone switches between light and dark)
        XC_MethodHook follow = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    followNight();
                } catch (Throwable ignored) {
                    // the next change tries again
                }
            }
        };
        try {
            XposedHelpers.findAndHookMethod("android.content.res.ResourcesImpl", null,
                    "updateConfiguration", Configuration.class, DisplayMetrics.class,
                    "android.content.res.CompatibilityInfo", follow);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "could not follow night mode (impl): " + t);
        }
        try {
            XposedHelpers.findAndHookMethod(Resources.class, "setImpl",
                    "android.content.res.ResourcesImpl", follow);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "could not follow night mode (setImpl): " + t);
        }
    }

    /**
     * Re-apply the themed Resources objects whose night mode differs from what was applied
     */
    private static void followNight() {
        if (Boolean.TRUE.equals(REAPPLYING.get())) return;
        List<XResources> changed = new ArrayList<>();
        List<List<ValueReplacement>> lists = new ArrayList<>();
        synchronized (VALUES) {
            if (VALUES.isEmpty()) return;
            for (Map.Entry<Resources, List<ValueReplacement>> e : VALUES.entrySet()) {
                Resources res = e.getKey();
                if (!(res instanceof XResources)) continue;
                boolean now = SafeForward.isNight(res);
                Boolean was = APPLIED_NIGHT.get(res);
                if (was != null && was == now) continue;
                changed.add((XResources) res);
                lists.add(new ArrayList<>(e.getValue()));
            }
        }
        for (int i = 0; i < changed.size(); i++) reapply(changed.get(i), lists.get(i));
    }

    private static void reapply(XResources res, List<ValueReplacement> list) {
        // Updating the overlay's own resources below comes back through the hooks
        if (Boolean.TRUE.equals(REAPPLYING.get())) return;
        REAPPLYING.set(true);
        try {
            reapplyNow(res, list);
        } finally {
            REAPPLYING.set(false);
        }
    }

    private static void reapplyNow(XResources res, List<ValueReplacement> list) {
        Configuration config = res.getConfiguration();
        DisplayMetrics metrics = res.getDisplayMetrics();
        boolean night = SafeForward.isNight(res);
        synchronized (VALUES) {
            APPLIED_NIGHT.put(res, night);
        }
        Set<XModuleResources> updated = new HashSet<>();
        for (ValueReplacement r : list) {
            try {
                if (updated.add(r.modRes)) r.modRes.updateConfiguration(config, metrics);
                if (r.kind == ValueReplacement.FOLLOW) continue;
                // Dark mode only: the theme at night, the app's own resource by day
                boolean on = !r.darkOnly || night;
                if (r.kind == ValueReplacement.FILE) {
                    res.setReplacement(r.target, r.type, r.name,
                            on ? r.modRes.fwd(r.id) : SafeForward.STOCK);
                    continue;
                }
                if (!on) {
                    res.setReplacement(r.target, r.type, r.name, SafeForward.STOCK);
                    continue;
                }
                Object value = directValue(r.modRes, r.type, r.id);
                if (value != null) {
                    res.setReplacement(r.target, r.type, r.name, value);
                } else if (r.darkOnly) {
                    res.setReplacement(r.target, r.type, r.name, SafeForward.STOCK);
                }
            } catch (Throwable ignored) {
                // Keep the previous value
            }
        }
    }
}
