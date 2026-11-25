/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.xposed;

import java.io.File;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import projekt.substratum.common.platform.XposedBackend;

/**
 * The parts of One UI 9's quick settings that are not resources, made AMOLED:
 * <ul>
 * <li>The shade background is faded to a maximum alpha below 1, so apps show through it:
 * {@code SecPanelBackground.mMaxAlpha} is kept at 1.</li>
 * <li>The brightness and volume bar tracks are a fixed 15% white drawn twice (grey on black):
 * inside the bar's draw method that color becomes a faint 6% white.</li>
 * </ul>
 * Active only while "AMOLED for every app" is applied ({@link XposedBackend#AMOLED_FLAG}).
 * Every hook is independent and fails closed: a missing class or method leaves stock behaviour.
 */
final class QsAmoledHook {

    private static final String TAG = "SubstratumXposed: QS AMOLED: ";
    private static final int STOCK_TRACK = 0x26ffffff;
    private static final int AMOLED_TRACK = 0x10ffffff;
    private static final ThreadLocal<Boolean> IN_BAR = new ThreadLocal<>();

    private QsAmoledHook() {
    }

    static void hook(ClassLoader loader) {
        if (!new File(XposedBackend.DIR, XposedBackend.AMOLED_FLAG).exists()) return;
        opaqueShade(loader);
        darkBarTrack(loader);
    }

    private static void opaqueShade(ClassLoader loader) {
        try {
            XposedHelpers.findAndHookMethod("com.android.systemui.statusbar.phone.SecPanelBackground",
                    loader, "setAlpha", float.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            XposedHelpers.setFloatField(param.thisObject, "mMaxAlpha", 1f);
                        }
                    });
            XposedBridge.log(TAG + "opaque shade");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "shade left stock: " + t);
        }
    }

    private static void darkBarTrack(ClassLoader loader) {
        try {
            Class<?> bar = XposedHelpers.findClass(
                    "com.android.systemui.samsung.quicksetting.ui.tiles.BidirectionalBarKt", loader);
            Method draw = null;
            for (Method m : bar.getDeclaredMethods()) {
                if (m.getName().startsWith("drawBidirectionalBarTrackProgressThumbContent")) {
                    draw = m;
                    break;
                }
            }
            if (draw == null) {
                XposedBridge.log(TAG + "bar draw method not found, track left stock");
                return;
            }
            Class<?> colorKt = XposedHelpers.findClass("androidx.compose.ui.graphics.ColorKt", loader);
            XposedHelpers.findAndHookMethod(colorKt, "Color", int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (Boolean.TRUE.equals(IN_BAR.get()) && (int) param.args[0] == STOCK_TRACK) {
                        param.args[0] = AMOLED_TRACK;
                    }
                }
            });
            XposedBridge.hookMethod(draw, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    IN_BAR.set(Boolean.TRUE);
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    IN_BAR.remove();
                }
            });
            XposedBridge.log(TAG + "dark bar tracks");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "bar track left stock: " + t);
        }
    }
}
