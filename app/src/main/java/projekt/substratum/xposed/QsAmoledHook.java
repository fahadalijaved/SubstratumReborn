/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.xposed;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import projekt.substratum.common.platform.QsOptions;
import projekt.substratum.common.platform.XposedBackend;

/**
 * The parts of One UI 9's quick settings that are not resources, made AMOLED:
 * <ul>
 * <li>The shade background is faded to a maximum alpha below 1, so apps show through it:
 * {@code SecPanelBackground.mMaxAlpha} is kept at 1.</li>
 * <li>The brightness and volume bars draw their track as two layers of a fixed 15% white (grey
 * on black): the box behind the bar ({@code BoxWithOutline}, composed by
 * {@code BidirectionalBar}) and a rectangle in the bar's draw method. Both become 6% white.</li>
 * </ul>
 * Active only while "AMOLED for every app" is applied ({@link XposedBackend#AMOLED_FLAG}).
 * Every hook is independent and fails closed: a missing class or method leaves stock behaviour.
 */
final class QsAmoledHook {

    private static final String TAG = "SubstratumXposed: QS AMOLED: ";
    private static final String TILES = "com.android.systemui.samsung.quicksetting.ui.tiles.";
    private static final int STOCK_TRACK = 0x26ffffff;
    private static final int AMOLED_TRACK = 0x10ffffff;
    // Compose packs an sRGB color as the ARGB int in the upper 32 bits
    private static final long STOCK_TRACK_COLOR = ((long) STOCK_TRACK) << 32;
    private static final long AMOLED_TRACK_COLOR = ((long) AMOLED_TRACK) << 32;
    /**
     * Set while a bar is being composed or drawn (both run on the UI thread)
     */
    private static final ThreadLocal<Boolean> IN_BAR = new ThreadLocal<>();

    private QsAmoledHook() {
    }

    static void hook(ClassLoader loader) {
        if (!new File(XposedBackend.DIR, XposedBackend.AMOLED_FLAG).exists()) return;
        QsOptions options = QsOptions.read();
        if (options.shade) opaqueShade(loader);
        if (options.track) darkBarTrack(loader);
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
            Class<?> bar = XposedHelpers.findClass(TILES + "BidirectionalBarKt", loader);
            Method compose = method(bar, "BidirectionalBar-");
            Method draw = method(bar, "drawBidirectionalBarTrackProgressThumbContent");
            Method box = method(XposedHelpers.findClass(
                    "com.android.systemui.samsung.quicksetting.ui.common.uielement.BoxWithOutlineKt",
                    loader), "BoxWithOutline");
            if (compose == null || draw == null || box == null) {
                XposedBridge.log(TAG + "bar methods not found, track left stock");
                return;
            }
            XC_MethodHook scope = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    IN_BAR.set(Boolean.TRUE);
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    IN_BAR.remove();
                }
            };
            // The box behind the bar: its background is a color argument
            XposedBridge.hookMethod(box, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!Boolean.TRUE.equals(IN_BAR.get())) return;
                    for (int i = 0; i < param.args.length; i++) {
                        if (param.args[i] instanceof Long &&
                                (Long) param.args[i] == STOCK_TRACK_COLOR) {
                            param.args[i] = AMOLED_TRACK_COLOR;
                        }
                    }
                }
            });
            // The rectangle in the draw method: the constant becomes a color through Color(int)
            XposedHelpers.findAndHookMethod(XposedHelpers.findClass(
                    "androidx.compose.ui.graphics.ColorKt", loader), "Color", int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (Boolean.TRUE.equals(IN_BAR.get()) &&
                                    (int) param.args[0] == STOCK_TRACK) {
                                param.args[0] = AMOLED_TRACK;
                            }
                        }
                    });
            XposedBridge.hookMethod(compose, scope);
            XposedBridge.hookMethod(draw, scope);
            // Run the callers interpreted so the calls above reach their hooks even where the
            // compiler inlined them
            deoptimize(compose);
            deoptimize(draw);
            XposedBridge.log(TAG + "dark bar tracks");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "bar track left stock: " + t);
        }
    }

    private static Method method(Class<?> owner, String prefix) {
        for (Method m : owner.getDeclaredMethods()) {
            if (m.getName().startsWith(prefix) && !Modifier.isAbstract(m.getModifiers())) return m;
        }
        return null;
    }

    /**
     * LSPosed can run a method interpreted; plain Xposed cannot, which only costs the fallback.
     */
    private static void deoptimize(Method method) {
        try {
            XposedBridge.class.getMethod("deoptimizeMethod", java.lang.reflect.Member.class)
                    .invoke(null, method);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "could not deoptimize " + method.getName() + ": " + t);
        }
    }
}
