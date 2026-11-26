/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.common.platform;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The looks of "AMOLED for every app" as one-tap presets, and the preferences behind them:
 * <pre>
 * amoled_preset        pure, elevated, dim, sprite or custom
 * amoled_level         pure, dim, elevated or tinted (see AmoledLevel)
 * amoled_accent        none, monet, oneui, sprite, white or a color (#RRGGBB)
 * amoled_accent_light  the accent in light mode too
 * </pre>
 * Choosing a preset writes its level, accent and active tile; choosing a level or an accent
 * alone makes the look "custom" unless it matches a preset. {@link AmoledRules#withUserOptions}
 * reads them.
 */
public final class AmoledPresets {

    public static final String PREF_PRESET = "amoled_preset";
    public static final String PREF_LEVEL = "amoled_level";
    public static final String PREF_ACCENT = "amoled_accent";
    public static final String PREF_ACCENT_LIGHT = "amoled_accent_light";

    public static final String PURE_BLACK = "pure";
    public static final String ELEVATED_CARDS = "elevated";
    public static final String DIM = "dim";
    public static final String SPRITE = "sprite";
    public static final String CUSTOM = "custom";

    public static final String ACCENT_NONE = "none";
    /**
     * The wallpaper's accent (system_accent1_500), read when AMOLED is applied: apply again to
     * follow a new wallpaper
     */
    public static final String ACCENT_MONET = "monet";
    public static final String ACCENT_ONEUI = "oneui";
    public static final String ACCENT_SPRITE = "sprite";
    public static final String ACCENT_WHITE = "white";

    public static final int ONEUI_BLUE = AmoledPalette.ONEUI_BLUE;
    public static final int SPRITE_TEAL = 0xff009587;
    public static final int WHITE = 0xffffffff;

    /**
     * The accents offered, in order (a custom color comes after them)
     */
    public static final List<String> ACCENTS = Collections.unmodifiableList(Arrays.asList(
            ACCENT_NONE, ACCENT_MONET, ACCENT_ONEUI, ACCENT_SPRITE, ACCENT_WHITE));

    private AmoledPresets() {
    }

    /**
     * A named look: its level, its accent and, if it sets one, its active tile.
     */
    public static final class Preset {
        public final String id;
        public final AmoledLevel level;
        public final String accent;
        /**
         * The active tile option it sets ({@link AmoledRules#TILE_ACCENT}), or null to keep the
         * user's
         */
        public final String tile;

        Preset(String id, AmoledLevel level, String accent, String tile) {
            this.id = id;
            this.level = level;
            this.accent = accent;
            this.tile = tile;
        }
    }

    public static final List<Preset> PRESETS = Collections.unmodifiableList(Arrays.asList(
            new Preset(PURE_BLACK, AmoledLevel.PURE, ACCENT_NONE, null),
            new Preset(ELEVATED_CARDS, AmoledLevel.ELEVATED, ACCENT_NONE, null),
            new Preset(DIM, AmoledLevel.DIM, ACCENT_NONE, null),
            new Preset(SPRITE, AmoledLevel.ELEVATED, ACCENT_SPRITE, AmoledRules.TILE_ACCENT)));

    /**
     * A preset by id, or null (custom, unknown)
     */
    public static Preset preset(String id) {
        for (Preset preset : PRESETS) if (preset.id.equals(id)) return preset;
        return null;
    }

    /**
     * The look the preferences describe. Each part is null when nothing was chosen, so the
     * theme's own choice stays.
     */
    public static final class Look {
        public final String preset;
        public final AmoledLevel level;
        public final String accent;
        public final Boolean accentLight;

        Look(String preset, AmoledLevel level, String accent, Boolean accentLight) {
            this.preset = preset;
            this.level = level;
            this.accent = accent;
            this.accentLight = accentLight;
        }
    }

    public static Look look(SharedPreferences prefs) {
        String id = prefs.getString(PREF_PRESET, null);
        Preset preset = preset(id);
        AmoledLevel level = preset != null ? preset.level :
                AmoledLevel.parse(prefs.getString(PREF_LEVEL, null), null);
        String accent = preset != null ? preset.accent : prefs.getString(PREF_ACCENT, null);
        Boolean light = prefs.contains(PREF_ACCENT_LIGHT) ?
                prefs.getBoolean(PREF_ACCENT_LIGHT, false) : null;
        return new Look(id, level, accent, light);
    }

    /**
     * Choose a preset: its level, accent and tile are written too.
     */
    public static void choose(SharedPreferences prefs, String id) {
        Preset preset = preset(id);
        SharedPreferences.Editor edit = prefs.edit();
        if (preset == null) {
            edit.putString(PREF_PRESET, CUSTOM).apply();
            return;
        }
        edit.putString(PREF_PRESET, preset.id)
                .putString(PREF_LEVEL, preset.level.key())
                .putString(PREF_ACCENT, preset.accent);
        String tile = prefs.getString(AmoledRules.PREF_ACTIVE_TILE, AmoledRules.TILE_WHITE);
        if (preset.tile != null) {
            edit.putString(AmoledRules.PREF_ACTIVE_TILE, preset.tile);
        } else if (AmoledRules.TILE_ACCENT.equals(tile) && ACCENT_NONE.equals(preset.accent)) {
            // An accent tile without an accent would be white anyway: say so
            edit.putString(AmoledRules.PREF_ACTIVE_TILE, AmoledRules.TILE_WHITE);
        }
        edit.apply();
    }

    public static void setLevel(SharedPreferences prefs, AmoledLevel level) {
        Look look = look(prefs);
        prefs.edit().putString(PREF_LEVEL, level.key())
                .putString(PREF_ACCENT, look.accent != null ? look.accent : ACCENT_NONE)
                .putString(PREF_PRESET, matching(level, look.accent, tile(prefs))).apply();
    }

    public static void setAccent(SharedPreferences prefs, String accent) {
        Look look = look(prefs);
        AmoledLevel level = look.level != null ? look.level : AmoledLevel.PURE;
        prefs.edit().putString(PREF_ACCENT, accent).putString(PREF_LEVEL, level.key())
                .putString(PREF_PRESET, matching(level, accent, tile(prefs))).apply();
    }

    public static void setAccentLight(SharedPreferences prefs, boolean on) {
        prefs.edit().putBoolean(PREF_ACCENT_LIGHT, on).apply();
    }

    /**
     * The preset that is exactly this look, or {@link #CUSTOM}
     */
    public static String matching(AmoledLevel level, String accent, String tile) {
        String a = accent != null ? accent : ACCENT_NONE;
        for (Preset preset : PRESETS) {
            if (preset.level != level || !preset.accent.equalsIgnoreCase(a)) continue;
            if (preset.tile != null && !preset.tile.equals(tile)) continue;
            return preset.id;
        }
        return CUSTOM;
    }

    private static String tile(SharedPreferences prefs) {
        return prefs.getString(AmoledRules.PREF_ACTIVE_TILE, AmoledRules.TILE_WHITE);
    }

    /**
     * The color of an accent choice: none (null), monet (the wallpaper's accent, One UI blue
     * when it cannot be read), oneui, sprite, white, or a color.
     */
    public static Integer resolveAccent(String choice, Integer monet) {
        if (choice == null) return null;
        switch (choice.trim().toLowerCase(Locale.ROOT)) {
            case "":
            case ACCENT_NONE:
            case "null":
                return null;
            case ACCENT_MONET:
                return monet != null ? monet | 0xff000000 : ONEUI_BLUE;
            case ACCENT_ONEUI:
                return ONEUI_BLUE;
            case ACCENT_SPRITE:
                return SPRITE_TEAL;
            case ACCENT_WHITE:
                return WHITE;
            default:
                try {
                    return AmoledPalette.parseColor(choice) | 0xff000000;
                } catch (IllegalArgumentException e) {
                    return null;
                }
        }
    }

    /**
     * Whether a choice is a color of its own rather than a named accent
     */
    public static boolean isCustom(String choice) {
        return choice != null && choice.trim().startsWith("#");
    }

    /**
     * The wallpaper's accent (system_accent1_500) on Android 12 and later, or null.
     */
    public static Integer monetAccent(Context context) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null;
        try {
            return context.getResources().getColor(android.R.color.system_accent1_500, null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * The accent is the wallpaper's and the wallpaper's accent changed since AMOLED was applied:
     * applying again follows it.
     */
    public static boolean monetChanged(Context context, SharedPreferences prefs) {
        Look look = look(prefs);
        if (!ACCENT_MONET.equals(look.accent)) return false;
        AmoledState state = AmoledState.read(context);
        Integer now = monetAccent(context);
        return state.enabled && state.accent != null && now != null &&
                (now | 0xff000000) != state.accent;
    }
}
