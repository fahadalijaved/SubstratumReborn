/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.xposed;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Paint;
import android.util.SparseArray;
import android.util.TypedValue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import projekt.substratum.common.platform.XposedBackend;

/**
 * AMOLED colors for apps an overlay cannot reach (obfuscated resource names such as Play Store,
 * or apps that refuse overlays), applied inside the app's own process. Only in dark mode.
 * <ul>
 * <li>By resource id, where Android turns a color resource into a value: plain lookups
 * ({@code ResourcesImpl.getValue}), styled attributes ({@code TypedArray.getColor}) and the
 * drawable / color state list loaders. (XResources' by-id value replacement does not reach
 * these paths.)</li>
 * <li>By color, where it is drawn ({@code Paint.setColor}): Compose screens hold their palette
 * as constants in code, which never pass through resources. The app's own dark surface colors
 * (opaque stock dark values of its color resources) are drawn as their AMOLED values.</li>
 * </ul>
 * The color list is written by "AMOLED for every app": one line per color resource,
 * {@code id light night [stockNight]}.
 */
final class AppAmoledHook {

    private static final String TAG = "SubstratumXposed: AMOLED: ";
    private static SparseArray<int[]> byId; // id -> {light, night}
    private static Map<Integer, Integer> byColor; // stock dark color -> AMOLED color

    private AppAmoledHook() {
    }

    static void hook(String packageName) throws Exception {
        if (byId != null) return; // once per process
        File file = new File(XposedBackend.DIR, packageName + XposedBackend.AMOLED_IDS);
        if (!file.exists()) return;
        SparseArray<int[]> ids = new SparseArray<>();
        Map<Integer, Integer> paints = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split(" ");
                if (parts.length < 3) continue;
                int night = hex(parts[2]);
                ids.put(hex(parts[0]), new int[]{hex(parts[1]), night});
                if (parts.length >= 4) {
                    int stock = hex(parts[3]);
                    // Only opaque surfaces: translucent layers and text keep their colors
                    if (stock >>> 24 == 0xff && stock != night && !paints.containsKey(stock)) {
                        paints.put(stock, night);
                    }
                }
            }
        }
        if (ids.size() == 0) return;
        byId = ids;
        byColor = paints;
        Class<?> impl = XposedHelpers.findClass("android.content.res.ResourcesImpl", null);

        // Resources.getColor / getColorStateList / getDrawable for plain colors
        XposedHelpers.findAndHookMethod(impl, "getValue", int.class, TypedValue.class,
                boolean.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        swap((int) param.args[0], (TypedValue) param.args[1],
                                (Configuration) XposedHelpers.callMethod(param.thisObject,
                                        "getConfiguration"));
                    }
                });
        // Colors set through styles and layout attributes
        XposedHelpers.findAndHookMethod(TypedArray.class, "getColor", int.class, int.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        TypedArray array = (TypedArray) param.thisObject;
                        int[] c = byId.get(array.getResourceId((int) param.args[0], 0));
                        if (c != null && night(array.getResources().getConfiguration())) {
                            param.setResult(c[1]);
                        }
                    }
                });
        // android:background="@color/..." and color state lists read from attributes
        XC_MethodHook loader = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                for (Object arg : param.args) {
                    if (arg instanceof TypedValue) {
                        TypedValue value = (TypedValue) arg;
                        swap(value.resourceId, value, (Configuration) XposedHelpers.callMethod(
                                param.thisObject, "getConfiguration"));
                        return;
                    }
                }
            }
        };
        hookAll(impl, "loadDrawable", loader);
        hookAll(impl, "loadColorStateList", loader);
        // Compose and other code-defined colors, where they are drawn
        if (!paints.isEmpty()) {
            XposedHelpers.findAndHookMethod(Paint.class, "setColor", int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Integer mapped = byColor.get((Integer) param.args[0]);
                            if (mapped != null && systemNight()) param.args[0] = mapped;
                        }
                    });
            try {
                XposedHelpers.findAndHookMethod(Paint.class, "setColor", long.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                long color = (long) param.args[0];
                                if ((color & 0x3fL) != 0) return; // not sRGB
                                Integer mapped = byColor.get((int) (color >>> 32));
                                if (mapped != null && systemNight()) {
                                    param.args[0] = ((long) mapped) << 32;
                                }
                            }
                        });
            } catch (Throwable ignored) {
                // Before Android 10
            }
        }
        XposedBridge.log(TAG + ids.size() + " colors by id, " + paints.size() +
                " surface colors in " + packageName);
    }

    private static int hex(String value) {
        return (int) Long.parseLong(value.substring(2), 16);
    }

    private static void swap(int id, TypedValue value, Configuration config) {
        if (value == null || value.type < TypedValue.TYPE_FIRST_COLOR_INT ||
                value.type > TypedValue.TYPE_LAST_COLOR_INT) return;
        int[] c = byId.get(id);
        if (c != null && night(config)) value.data = c[1];
    }

    private static boolean systemNight() {
        return night(Resources.getSystem().getConfiguration());
    }

    private static boolean night(Configuration config) {
        return config != null && (config.uiMode & Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES;
    }

    private static void hookAll(Class<?> owner, String name, XC_MethodHook hook) {
        try {
            XposedBridge.hookAllMethods(owner, name, hook);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "could not hook " + name + ": " + t);
        }
    }
}
