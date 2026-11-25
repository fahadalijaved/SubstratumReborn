/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.xposed;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import projekt.substratum.common.platform.XposedBackend;

/**
 * One UI 9 quick settings layout: the panel is a grid whose rows are {@code cell width x aspect}
 * tall (0.917 stock), so the brightness and volume bars, which span one row, stay tall at high
 * densities. {@link XposedBackend#QS_CELL_ASPECT} holds a smaller aspect for the panel grid
 * ("QuickPanel"); without that file nothing changes.
 */
final class QsLayoutHook {

    private static final String TAG = "SubstratumXposed: QS layout: ";
    private static final String GRID =
            "com.android.systemui.samsung.quicksetting.ui.base.spannablegrid.SpannableGridKt";
    // SpannableGrid(scope, modifier, state, focus, menu, columns, rows, ?, cellAspect, ...)
    private static final int COLUMNS_ARG = 5;
    private static final int ASPECT_ARG = 8;

    private QsLayoutHook() {
    }

    static void hook(ClassLoader loader) {
        float aspect = readAspect();
        if (aspect <= 0) return;
        try {
            Method grid = null;
            for (Method m : XposedHelpers.findClass(GRID, loader).getDeclaredMethods()) {
                if (m.getName().startsWith("SpannableGrid-") &&
                        !Modifier.isAbstract(m.getModifiers())) {
                    grid = m;
                    break;
                }
            }
            Class<?>[] types = grid != null ? grid.getParameterTypes() : new Class<?>[0];
            if (types.length <= ASPECT_ARG || types[COLUMNS_ARG] != int.class ||
                    types[ASPECT_ARG] != float.class) {
                XposedBridge.log(TAG + "grid signature changed, layout left stock");
                return;
            }
            XposedBridge.hookMethod(grid, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    // Only the panel grid, not the tile box or the edit screen
                    boolean panel = false;
                    for (Object arg : param.args) {
                        if ("QuickPanel".equals(arg)) {
                            panel = true;
                            break;
                        }
                    }
                    if (panel) param.args[ASPECT_ARG] = aspect;
                }
            });
            XposedBridge.log(TAG + "panel cell aspect " + aspect);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "layout left stock: " + t);
        }
    }

    private static float readAspect() {
        File file = new File(XposedBackend.DIR, XposedBackend.QS_CELL_ASPECT);
        if (!file.exists()) return 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            float value = Float.parseFloat(reader.readLine().trim());
            return value >= 0.3f && value <= 1.5f ? value : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
