/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.common.platform;

import com.google.android.material.color.utilities.Hct;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The accent's tone mapping, the levels' ramps and the presets.
 */
public class AmoledAccentTest {

    private static final int SPRITE = AmoledPresets.SPRITE_TEAL;
    private static final int BLUE = AmoledPalette.ONEUI_BLUE;
    private static final String SETTINGS = "com.android.settings";
    private static final String SYSTEMUI = "com.android.systemui";

    @Test
    public void toneSwapKeepsEachShadesTone() {
        // One UI's primary ramp, light to dark
        int[] ramp = {0xfff5f8ff, 0xffd6e3ff, 0xff94b8ff, 0xff598fff, 0xff387aff, 0xff244ea3,
                0xff122752, 0xff091429};
        double hue = Hct.fromInt(SPRITE).getHue();
        double previousTone = 101;
        for (int stock : ramp) {
            int swapped = AmoledPalette.toneSwap(stock, SPRITE, BLUE);
            Hct before = Hct.fromInt(stock), after = Hct.fromInt(swapped);
            assertEquals("tone of " + hex(stock), before.getTone(), after.getTone(), 1.0);
            assertTrue("ramp order at " + hex(stock), after.getTone() < previousTone);
            previousTone = after.getTone();
            if (after.getChroma() > 6) {
                assertEquals("hue of " + hex(stock), hue, after.getHue(), 6.0);
            }
        }
        // The stock accent itself becomes the accent at the stock accent's tone
        Hct primary = Hct.fromInt(AmoledPalette.toneSwap(BLUE, SPRITE, BLUE));
        assertEquals(Hct.fromInt(BLUE).getTone(), primary.getTone(), 1.0);
        assertEquals(hue, primary.getHue(), 3.0);
    }

    @Test
    public void toneSwapKeepsAlphaAndGreys() {
        int translucent = AmoledPalette.toneSwap(0x80387aff, SPRITE, BLUE);
        assertEquals(0x80, translucent >>> 24);
        // A grey shade stays grey (chroma 0 scales the accent's chroma to 0)
        Hct grey = Hct.fromInt(AmoledPalette.toneSwap(0xff808080, SPRITE, BLUE));
        assertTrue(grey.getChroma() < 2);
        // A white accent lifts light shades towards white, keeps dark ones
        Hct light = Hct.fromInt(AmoledPalette.toneSwap(0xff387aff, 0xffffffff, BLUE));
        assertTrue(light.getTone() > 85 && light.getChroma() < 4);
        Hct dark = Hct.fromInt(AmoledPalette.toneSwap(0xff091429, 0xffffffff, BLUE));
        assertEquals(Hct.fromInt(0xff091429).getTone(), dark.getTone(), 1.0);
    }

    @Test
    public void accentRecolorsOnlyAccentNames() {
        AmoledRules rules = AmoledRules.defaults().setAccent(SPRITE);
        assertEquals(AmoledPalette.ACCENT, AmoledPalette.forColor(SETTINGS,
                "basic_token_primary_50", BLUE, rules, BLUE).rule);
        assertEquals(AmoledPalette.ACCENT, AmoledPalette.forColor(SETTINGS,
                "sesl_primary_color_dark", BLUE, rules, BLUE).rule);
        assertEquals(AmoledPalette.ACCENT, AmoledPalette.forColor(SETTINGS,
                "basic_token_primary_100", 0xff091429, rules, BLUE).rule);
        // Not an accent name: the generic rules
        assertNull(AmoledPalette.forColor(SETTINGS, "sesl_blue_color_light", BLUE, rules, BLUE)
                .color);
        // No accent: the stock accent stays, dark shades are surfaces as before
        AmoledRules plain = AmoledRules.defaults();
        assertNull(AmoledPalette.forColor(SETTINGS, "basic_token_primary_50", BLUE, plain, BLUE)
                .color);
        assertEquals(AmoledPalette.SURFACE, AmoledPalette.forColor(SETTINGS,
                "basic_token_primary_100", 0xff091429, plain, BLUE).rule);
    }

    @Test
    public void activeTileTakesTheAccentWithTheTileOption() {
        AmoledRules accentTile = AmoledRules.defaults().withOptions(AmoledLevel.ELEVATED,
                AmoledPresets.ACCENT_SPRITE, null, AmoledRules.TILE_ACCENT, null);
        AmoledPalette.Decision tile = AmoledPalette.forColor(SYSTEMUI,
                "qs_tile_round_background_on", 0xffd5d5d5, accentTile, BLUE);
        assertEquals(AmoledPalette.ACCENT, tile.rule);
        assertEquals(Integer.valueOf(SPRITE), tile.color);
        // The accent tile without an accent is white
        AmoledRules noAccent = AmoledRules.defaults().withOptions(null, AmoledPresets.ACCENT_NONE,
                null, AmoledRules.TILE_ACCENT, null);
        assertEquals(Integer.valueOf(0xffffffff), AmoledPalette.forColor(SYSTEMUI,
                "qs_tile_round_background_on", 0xffd5d5d5, noAccent, BLUE).color);
        // White tile option with an accent: the tile stays white
        AmoledRules white = AmoledRules.defaults().withOptions(null, AmoledPresets.ACCENT_SPRITE,
                null, AmoledRules.TILE_WHITE, null);
        assertEquals(AmoledPalette.EXPLICIT, AmoledPalette.forColor(SYSTEMUI,
                "qs_tile_round_background_on", 0xffd5d5d5, white, BLUE).rule);
    }

    @Test
    public void levelsBucketSurfacesIntoTheirRamp() {
        AmoledRules elevated = AmoledRules.defaults().setLevel(AmoledLevel.ELEVATED);
        assertArrayEquals(new int[]{0xff000000, 0xff0b0b0b, 0xff141414, 0xff1c1c1c},
                elevated.ramp());
        // One UI: page black, cards, raised surfaces, containers
        assertEquals(Integer.valueOf(0xff000000),
                AmoledPalette.decide(0xff010102, "sesl_round_and_bgcolor_dark", elevated).color);
        assertEquals(Integer.valueOf(0xff0b0b0b),
                AmoledPalette.decide(0xff17171a, "sesl_background_color_dark", elevated).color);
        assertEquals(Integer.valueOf(0xff141414),
                AmoledPalette.decide(0xff252528, "manage_apps_background_color", elevated).color);
        assertEquals(Integer.valueOf(0xff1c1c1c),
                AmoledPalette.decide(0xff3a3a3d, "sesl_btn_background_color_dark", elevated).color);
        // Other colors by their brightest channel
        assertEquals(0, AmoledPalette.tier(0xff121212));
        assertEquals(1, AmoledPalette.tier(0xff2a2a2a));
        assertEquals(2, AmoledPalette.tier(0xff303030));
        assertEquals(3, AmoledPalette.tier(0xff424242));
        // Floating surfaces take at least the raised step
        assertEquals(Integer.valueOf(0xff141414),
                AmoledPalette.decide(0xff010102, "popup_menu_background", elevated).color);
        AmoledRules dim = AmoledRules.defaults().setLevel(AmoledLevel.DIM);
        assertEquals(Integer.valueOf(0xff0e0e0e),
                AmoledPalette.decide(0xff010102, "window_background", dim).color);
        assertEquals(Integer.valueOf(0xff1a1a1a),
                AmoledPalette.decide(0xff17171a, "dialog_background", dim).color);
        // Pure black ignores the ramp
        AmoledRules pure = AmoledRules.defaults();
        assertEquals(Integer.valueOf(0xff000000),
                AmoledPalette.decide(0xff3a3a3d, "sesl_btn_background_color_dark", pure).color);
    }

    @Test
    public void tintedRampBlendsTheAccent() {
        AmoledRules tinted = AmoledRules.defaults().setLevel(AmoledLevel.TINTED).setAccent(SPRITE);
        int[] ramp = tinted.ramp();
        assertEquals(0xff000000, ramp[0]);
        int[] elevated = AmoledLevel.ELEVATED.defaultRamp();
        for (int i = 1; i < 4; i++) {
            assertNotEquals(elevated[i], ramp[i]);
            assertEquals(AmoledPalette.blend(elevated[i], SPRITE, 0.05f), ramp[i]);
            // teal: more green and blue than red
            assertTrue(((ramp[i] >> 8) & 0xff) > ((ramp[i] >> 16) & 0xff));
        }
        // A theme's own ramp is used, and dropped when the level changes
        AmoledRules theme = AmoledRules.defaults();
        theme.level = AmoledLevel.DIM;
        theme.ramp = new int[]{0xff050505, 0xff101010, 0xff202020, 0xff303030};
        assertEquals(0xff101010, theme.ramp()[1]);
        theme.setLevel(AmoledLevel.ELEVATED);
        assertEquals(0xff0b0b0b, theme.ramp()[1]);
    }

    @Test
    public void rulesJsonReadsTheLook() throws Exception {
        AmoledRules rules = AmoledRules.fromJson("{\"level\":\"tinted\",\"accent\":\"sprite\"," +
                "\"accentLight\":true,\"qsTrack\":\"#20ffffff\",\"ramp\":[\"#000000\"," +
                "\"#0a0a0a\",\"#151515\",\"#202020\"]}");
        assertEquals(AmoledLevel.TINTED, rules.level());
        assertEquals(Integer.valueOf(SPRITE), rules.accent());
        assertTrue(rules.accentLight());
        assertEquals(0x20ffffff, rules.qsTrack());
        assertEquals(AmoledPalette.blend(0xff0a0a0a, SPRITE, 0.05f), rules.ramp()[1]);
        AmoledRules none = AmoledRules.fromJson("{\"accent\":null}");
        assertNull(none.accent());
        assertEquals(QsOptions.DEFAULT_TRACK, AmoledRules.defaults().qsTrack());
        // A user choice overrides the theme's accent; the wallpaper accent when it can be read
        AmoledRules monet = AmoledRules.fromJson("{\"accent\":\"sprite\"}")
                .withOptions(null, AmoledPresets.ACCENT_MONET, null, null, 0xff6476a5);
        assertEquals(Integer.valueOf(0xff6476a5), monet.accent());
    }

    @Test
    public void accentChoicesResolve() {
        assertNull(AmoledPresets.resolveAccent(AmoledPresets.ACCENT_NONE, 0xff112233));
        assertNull(AmoledPresets.resolveAccent(null, null));
        assertEquals(Integer.valueOf(0xff112233),
                AmoledPresets.resolveAccent(AmoledPresets.ACCENT_MONET, 0xff112233));
        assertEquals(Integer.valueOf(BLUE),
                AmoledPresets.resolveAccent(AmoledPresets.ACCENT_MONET, null));
        assertEquals(Integer.valueOf(BLUE),
                AmoledPresets.resolveAccent(AmoledPresets.ACCENT_ONEUI, null));
        assertEquals(Integer.valueOf(SPRITE),
                AmoledPresets.resolveAccent(AmoledPresets.ACCENT_SPRITE, null));
        assertEquals(Integer.valueOf(0xffffffff),
                AmoledPresets.resolveAccent(AmoledPresets.ACCENT_WHITE, null));
        assertEquals(Integer.valueOf(0xffff8800), AmoledPresets.resolveAccent("#ff8800", null));
        assertNull(AmoledPresets.resolveAccent("#nope", null));
        assertTrue(AmoledPresets.isCustom("#ff8800"));
    }

    @Test
    public void presetsMatchTheirLooks() {
        assertEquals(AmoledPresets.PURE_BLACK, AmoledPresets.matching(AmoledLevel.PURE,
                AmoledPresets.ACCENT_NONE, AmoledRules.TILE_WHITE));
        assertEquals(AmoledPresets.ELEVATED_CARDS, AmoledPresets.matching(AmoledLevel.ELEVATED,
                null, AmoledRules.TILE_WHITE));
        assertEquals(AmoledPresets.DIM, AmoledPresets.matching(AmoledLevel.DIM,
                AmoledPresets.ACCENT_NONE, AmoledRules.TILE_BLUE));
        assertEquals(AmoledPresets.SPRITE, AmoledPresets.matching(AmoledLevel.ELEVATED,
                AmoledPresets.ACCENT_SPRITE, AmoledRules.TILE_ACCENT));
        // Sprite's accent with a white tile is a look of its own
        assertEquals(AmoledPresets.CUSTOM, AmoledPresets.matching(AmoledLevel.ELEVATED,
                AmoledPresets.ACCENT_SPRITE, AmoledRules.TILE_WHITE));
        assertEquals(AmoledPresets.CUSTOM, AmoledPresets.matching(AmoledLevel.TINTED,
                AmoledPresets.ACCENT_NONE, AmoledRules.TILE_WHITE));
        AmoledPresets.Preset sprite = AmoledPresets.preset(AmoledPresets.SPRITE);
        assertNotNull(sprite);
        assertEquals(AmoledLevel.ELEVATED, sprite.level);
        assertEquals(AmoledRules.TILE_ACCENT, sprite.tile);
        assertNull(AmoledPresets.preset(AmoledPresets.CUSTOM));
    }

    @Test
    public void monetRolesAreNeverTouched() {
        assertTrue(!AmoledRules.touches("android", "system_neutral1_900"));
        assertTrue(AmoledRules.touches("android", "background_device_default_dark"));
        assertTrue(AmoledRules.touches(SETTINGS, "system_neutral1_900"));
        assertNull(AmoledChange.night("android", "system_accent2_900", 0xff151b2c,
                AmoledRules.defaults(), BLUE));
    }

    @Test
    public void qsOptionsTrackColor() {
        assertEquals(Integer.valueOf(0x10ffffff), QsOptions.color("#10ffffff"));
        assertEquals(Integer.valueOf(0xff123456), QsOptions.color("#123456"));
        assertEquals(Integer.valueOf(0x20ffffff), QsOptions.color("0x20ffffff"));
        assertNull(QsOptions.color("1"));
        assertNull(QsOptions.color("0"));
        assertNull(QsOptions.color("#zz"));
    }

    private static String hex(int color) {
        return String.format("#%08x", color);
    }
}
