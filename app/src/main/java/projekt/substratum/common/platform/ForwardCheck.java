/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.common.platform;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Whether the LSPosed module may forward a theme's drawable by name. Pure: unit tested; the module
 * asks it once per name and process, on the first lookup.
 * <p>
 * Apps cast the drawables they load. One UI 9's System UI casts notification backgrounds to
 * {@code androidx.appcompat.graphics.drawable.SeslRecoilDrawable}; a classic theme forwarding a
 * plain {@code RippleDrawable} there crashed it in a loop. A drawable whose stock class is not a
 * plain framework class ({@code android.graphics.drawable.*}), or that the app animates, is
 * therefore only forwarded when the theme's one is of the same kind.
 */
public final class ForwardCheck {

    static final String FRAMEWORK = "android.graphics.drawable.";
    /**
     * Framework drawables apps cast to start or drive them
     */
    private static final Set<String> DRIVEN = new HashSet<>(Arrays.asList(
            FRAMEWORK + "AnimatedVectorDrawable",
            FRAMEWORK + "AnimationDrawable",
            FRAMEWORK + "AnimatedStateListDrawable",
            FRAMEWORK + "AnimatedImageDrawable",
            FRAMEWORK + "AnimatedRotateDrawable",
            FRAMEWORK + "TransitionDrawable",
            FRAMEWORK + "LevelListDrawable",
            FRAMEWORK + "ClipDrawable"));

    private ForwardCheck() {
    }

    /**
     * @param stockClass        The class of the app's own drawable, or null when it did not load
     * @param replacementClass  The class of the theme's drawable, or null when it did not load
     * @param replacementIsKind The theme's drawable is an instance of the stock class
     * @return null to forward it, else why it is left stock
     */
    public static String drawable(String stockClass, String replacementClass,
                                  boolean replacementIsKind) {
        if (replacementClass == null) return "does not load";
        if (stockClass == null || stockClass.equals(replacementClass) || replacementIsKind) {
            return null;
        }
        if (!isFramework(stockClass)) {
            return "the app expects " + simple(stockClass) + ", the theme has " +
                    simple(replacementClass);
        }
        if (DRIVEN.contains(stockClass)) {
            return "the app drives its " + simple(stockClass) + ", the theme has " +
                    simple(replacementClass);
        }
        return null;
    }

    static boolean isFramework(String className) {
        return className.startsWith(FRAMEWORK) &&
                className.indexOf('.', FRAMEWORK.length()) < 0;
    }

    static String simple(String className) {
        int dot = className.lastIndexOf('.');
        return dot < 0 ? className : className.substring(dot + 1);
    }
}
