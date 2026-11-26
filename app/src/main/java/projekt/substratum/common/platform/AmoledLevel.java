/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.common.platform;

import java.util.Locale;

/**
 * How dark the surfaces of "AMOLED for every app" are. {@link #PURE} paints every dark surface
 * {@link AmoledRules#background} (true black); the other levels keep One UI's elevation, with
 * each dark surface taking one of four steps of a ramp (see {@link AmoledPalette#tier}):
 * <pre>
 *            page      card      raised    container
 * ELEVATED   #000000   #0B0B0B   #141414   #1C1C1C
 * DIM        #0E0E0E   #141414   #1A1A1A   #222222
 * TINTED     the ELEVATED ramp, the accent blended into the last three at 5%
 * </pre>
 * Floating surfaces (popups, menus, dialogs) take at least the third step, so they stay visible.
 */
public enum AmoledLevel {
    PURE,
    DIM,
    ELEVATED,
    TINTED;

    private static final int[] ELEVATED_RAMP = {0xff000000, 0xff0b0b0b, 0xff141414, 0xff1c1c1c};
    private static final int[] DIM_RAMP = {0xff0e0e0e, 0xff141414, 0xff1a1a1a, 0xff222222};

    /**
     * The four surface steps of this level, before any tint (a copy). Pure black is all black.
     */
    public int[] defaultRamp() {
        switch (this) {
            case DIM:
                return DIM_RAMP.clone();
            case ELEVATED:
            case TINTED:
                return ELEVATED_RAMP.clone();
            case PURE:
            default:
                return new int[]{0xff000000, 0xff000000, 0xff000000, 0xff000000};
        }
    }

    /**
     * The name used in preferences, rules.json and the state file
     */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * A level by its key (any case), or the fallback.
     */
    public static AmoledLevel parse(String value, AmoledLevel fallback) {
        if (value == null) return fallback;
        for (AmoledLevel level : values()) {
            if (level.name().equalsIgnoreCase(value.trim())) return level;
        }
        return fallback;
    }
}
