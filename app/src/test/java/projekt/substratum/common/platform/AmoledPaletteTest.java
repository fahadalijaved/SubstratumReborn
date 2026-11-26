/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.common.platform;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AmoledPaletteTest {

    @Test
    public void parsesEveryHexForm() {
        assertEquals(0xff000000, AmoledPalette.parseColor("#000"));
        assertEquals(0xffaabbcc, AmoledPalette.parseColor("#abc"));
        assertEquals(0x88000000, AmoledPalette.parseColor("#8000"));
        assertEquals(0xff123456, AmoledPalette.parseColor("#123456"));
        assertEquals(0x80123456, AmoledPalette.parseColor("#80123456"));
        assertEquals(0x00000000, AmoledPalette.parseColor("#00000000"));
        assertEquals(0xffffffff, AmoledPalette.parseColor("#FFFFFFFF"));
        assertEquals(0xffff0000, AmoledPalette.parseColor("red"));
        for (String bad : new String[]{"#12345", "#zzzzzz", "", "#", "000000", "nocolor"}) {
            try {
                AmoledPalette.parseColor(bad);
                fail("parsed " + bad);
            } catch (IllegalArgumentException expected) {
                // fine
            }
        }
    }

    @Test
    public void decidesWithRuleLabels() {
        AmoledRules rules = AmoledRules.defaults();
        check(AmoledPalette.decide(0xffffffff, "primary_text_color", rules),
                null, AmoledPalette.TEXT_KEPT);
        check(AmoledPalette.decide(0xff252528, "list_item_pressed_color", rules),
                null, AmoledPalette.STATE_KEPT);
        check(AmoledPalette.decide(0xff252528, "background_light", rules),
                null, AmoledPalette.LIGHT_VARIANT);
        check(AmoledPalette.decide(0xff333333, "popup_menu_divider_color", rules),
                rules.outline, AmoledPalette.OUTLINE);
        check(AmoledPalette.decide(0x1affffff, "card_bg_color", rules),
                0, AmoledPalette.CLEAR);
        check(AmoledPalette.decide(0x33ffffff, "panel_blur_bg", rules),
                rules.blur, AmoledPalette.BLUR);
        check(AmoledPalette.decide(0xff444444, "scrollbar_thumb", rules),
                rules.thumb, AmoledPalette.THUMB);
        check(AmoledPalette.decide(0xff3a3a3d, "seekbar_track_color", rules),
                rules.slider, AmoledPalette.SLIDER);
        check(AmoledPalette.decide(0xff17171a, "window_background", rules),
                rules.background, AmoledPalette.SURFACE);
        check(AmoledPalette.decide(0xff252528, "dialog_bg_color", rules),
                rules.floating, AmoledPalette.FLOATING);
        check(AmoledPalette.decide(0xff848487, "list_background", rules),
                null, AmoledPalette.KEPT);
        // SystemAmoled keeps answering through the palette
        assertEquals(Integer.valueOf(rules.background),
                SystemAmoled.amoled(0xff17171a, "window_background", rules));
        assertNull(SystemAmoled.amoled(0xffffffff, "primary_text_color", rules));
    }

    @Test
    public void linesNeverVanishIntoThePage() {
        AmoledRules rules = AmoledRules.defaults();
        check(AmoledPalette.decide(0xff3a3a3d, "list_divider_color", rules),
                rules.hairline, AmoledPalette.HAIRLINE);
        check(AmoledPalette.decide(0xff49454f, "m3_sys_color_dark_outline_variant", rules),
                rules.hairline, AmoledPalette.HAIRLINE);
        // The stroke of a background is a line; the background of a bordered thing a surface
        check(AmoledPalette.decide(0xff47474a, "dex_background_stroke_color", rules),
                rules.hairline, AmoledPalette.HAIRLINE);
        check(AmoledPalette.decide(0xff17171a, "rp_content_bordered_button_bg_color_color_dark",
                rules), rules.background, AmoledPalette.SURFACE);
        // A dim line keeps its color; a near-black one went with the stock page already
        check(AmoledPalette.decide(0xff2d2d2d, "more_info_divider_color", rules),
                null, AmoledPalette.LINE_KEPT);
        check(AmoledPalette.decide(0xff0f0f0f, "expand_divider_color", rules),
                rules.background, AmoledPalette.SURFACE);
        // Obfuscated names (Play Store): Material's outline variant by its value
        check(AmoledPalette.decide(0xff444746, "0_resource_name_obfuscated", rules),
                rules.hairline, AmoledPalette.HAIRLINE);
        check(AmoledPalette.decide(0xff46464f, "a", rules),
                rules.hairline, AmoledPalette.HAIRLINE);
        check(AmoledPalette.decide(0xff1e1f20, "0_resource_name_obfuscated", rules),
                rules.background, AmoledPalette.SURFACE);
        assertNull(AmoledPalette.decide(0xff8e918f, "0_resource_name_obfuscated", rules).color);
        // A named surface with that value stays a surface
        check(AmoledPalette.decide(0xff444746, "card_background", rules),
                rules.background, AmoledPalette.SURFACE);
        // Levels with a ramp: a line takes a step above the page, as before
        AmoledRules elevated = AmoledRules.defaults().setLevel(AmoledLevel.ELEVATED);
        check(AmoledPalette.decide(0xff3a3a3d, "list_divider_color", elevated),
                0xff1c1c1c, AmoledPalette.SURFACE);
    }

    @Test
    public void explicitColorsNameTheirRule() {
        AmoledRules rules = AmoledRules.defaults();
        AmoledPalette.Decision exact = AmoledPalette.forColor("com.android.systemui",
                "panel_bg_color", 0xff1b1b1b, rules);
        check(exact, 0xff000000, AmoledPalette.EXPLICIT);
        AmoledPalette.Decision pattern = AmoledPalette.forColor("com.android.settings",
                "sec_dashboard_wifi_color", 0xff3478f6, rules);
        assertEquals(Integer.valueOf(0xff262626), pattern.color);
        assertTrue(pattern.rule, pattern.rule.startsWith(AmoledPalette.PATTERN_PREFIX + "^sec"));
        // Other packages: the generic rules
        assertEquals(AmoledPalette.SURFACE, AmoledPalette.forColor("com.android.phone",
                "panel_bg_color", 0xff1b1b1b, rules).rule);
    }

    @Test
    public void themeRulesParseInTheJvm() throws Exception {
        File json = new File("../themes/amoled/src/main/assets/amoled/rules.json");
        if (!json.isFile()) json = new File("themes/amoled/src/main/assets/amoled/rules.json");
        AmoledRules theme = AmoledRules.fromJson(new String(Files.readAllBytes(json.toPath()),
                StandardCharsets.UTF_8));
        AmoledRules defaults = AmoledRules.defaults();
        assertEquals(defaults.background, theme.background);
        assertEquals(defaults.outline, theme.outline);
        assertEquals(defaults.slider, theme.slider);
        assertEquals(defaults.thumb, theme.thumb);
        assertEquals(0.65f, theme.qsAspect, 0.0001f);
        assertTrue(theme.covers("com.android.settings"));
        assertEquals(Integer.valueOf(0xffffffff),
                theme.explicit("com.android.systemui", "qs_tile_round_background_on"));

        AmoledRules custom = AmoledRules.fromJson(
                "{\"background\":\"#111\",\"exclude\":[\"^com\\\\.android\\\\.phone$\"]," +
                        "\"colors\":{\"com.android.phone\":{\"^call_.*\":\"#80ff0000\"}}}");
        assertEquals(0xff111111, custom.background);
        assertTrue(!custom.covers("com.android.phone"));
        check(custom.explicitDecision("com.android.phone", "call_bg"), 0x80ff0000,
                AmoledPalette.PATTERN_PREFIX + "^call_.*");
    }

    private static void check(AmoledPalette.Decision decision, Integer color, String rule) {
        assertEquals(rule, decision.rule);
        assertEquals(color, decision.color);
    }
}
