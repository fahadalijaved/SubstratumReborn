/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.common.platform;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The user's keep and pin rules, and the change records with their Monet-safe light entries.
 */
public class AmoledUserRulesTest {

    private static final String SETTINGS = "com.android.settings";
    private static final String SYSTEMUI = "com.android.systemui";
    private static final int BLUE = AmoledPalette.ONEUI_BLUE;

    @Test
    public void keepAndPinReplaceEachOther() {
        AmoledUserRules user = new AmoledUserRules();
        assertTrue(user.isEmpty());
        user.keep(SETTINGS, "sesl_background_color_dark");
        assertTrue(user.isKept(SETTINGS, "sesl_background_color_dark"));
        user.pin(SETTINGS, "sesl_background_color_dark", 0xff101010);
        assertFalse(user.isKept(SETTINGS, "sesl_background_color_dark"));
        assertEquals(Integer.valueOf(0xff101010),
                user.pinned(SETTINGS, "sesl_background_color_dark"));
        user.keep(SETTINGS, "sesl_background_color_dark");
        assertNull(user.pinned(SETTINGS, "sesl_background_color_dark"));
        assertTrue(user.clear(SETTINGS, "sesl_background_color_dark"));
        assertFalse(user.clear(SETTINGS, "sesl_background_color_dark"));
        assertTrue(user.isEmpty());
    }

    @Test
    public void jsonRoundTrip() throws Exception {
        AmoledUserRules user = new AmoledUserRules();
        user.keep(SETTINGS, "a");
        user.keep(SETTINGS, "b");
        user.pin(SYSTEMUI, "qs_tile_round_background_on", 0xff009587);
        String json = user.toJson();
        JSONObject o = new JSONObject(json);
        assertEquals(2, o.getJSONObject("keep").getJSONArray(SETTINGS).length());
        assertEquals("#FF009587", o.getJSONObject("pin").getJSONObject(SYSTEMUI)
                .getString("qs_tile_round_background_on"));
        AmoledUserRules back = AmoledUserRules.fromJson(json);
        assertTrue(back.isKept(SETTINGS, "b"));
        assertEquals(Integer.valueOf(0xff009587),
                back.pinned(SYSTEMUI, "qs_tile_round_background_on"));
        assertEquals(Arrays.asList(SYSTEMUI, SETTINGS).size(), back.packages().size());
        // Entries that are not colors are left out, the rest is kept
        AmoledUserRules partial = AmoledUserRules.fromJson(
                "{\"pin\":{\"p\":{\"x\":\"nope\",\"y\":\"#123\"}},\"keep\":{\"p\":[\"z\"]}}");
        assertNull(partial.pinned("p", "x"));
        assertEquals(Integer.valueOf(0xff112233), partial.pinned("p", "y"));
        assertTrue(partial.isKept("p", "z"));
    }

    @Test
    public void userRulesComeFirst() {
        AmoledUserRules user = new AmoledUserRules();
        user.keep(SETTINGS, "sesl_background_color_dark");
        user.pin(SYSTEMUI, "panel_bg_color", 0xff111111);
        user.keep(SETTINGS, "basic_token_primary_50");
        AmoledRules rules = AmoledRules.defaults().setAccent(AmoledPresets.SPRITE_TEAL)
                .withUserRules(user);
        // Keep: the sentinel, and compute() writes nothing
        assertSame(AmoledRules.KEEP, rules.explicitDecision(SETTINGS,
                "sesl_background_color_dark"));
        assertNull(AmoledChange.night(SETTINGS, "sesl_background_color_dark", 0xff17171a,
                rules, BLUE));
        // Keep beats the accent
        assertNull(AmoledChange.night(SETTINGS, "basic_token_primary_50", BLUE, rules, BLUE));
        // Pin beats the theme's own color for it
        AmoledPalette.Decision pinned = AmoledChange.night(SYSTEMUI, "panel_bg_color",
                0xcc101010, rules, BLUE);
        assertEquals(AmoledPalette.USER_PIN, pinned.rule);
        assertEquals(Integer.valueOf(0xff111111), pinned.color);
        // A pin reaches a color state list too
        assertEquals(AmoledPalette.USER_PIN, AmoledChange.night(SYSTEMUI, "panel_bg_color",
                null, rules, BLUE).rule);
    }

    @Test
    public void lightEntriesAreMonetSafe() {
        AmoledRules rules = AmoledRules.defaults().setAccent(AmoledPresets.SPRITE_TEAL);
        // A literal light value: paired with the night value as before
        AmoledPalette.Decision surface = AmoledChange.night(SETTINGS, "sesl_background_color_dark",
                0xff17171a, rules, BLUE);
        AmoledChange literal = AmoledChange.of(SETTINGS, "sesl_background_color_dark",
                0x7f060001, 0xff17171a, surface, 0xfffcfcff, 0xfffcfcff, rules, BLUE);
        assertEquals(Integer.valueOf(0xfffcfcff), literal.lightEntry);
        List<String[]> items = literal.toItems();
        assertEquals(2, items.size());
        assertEquals("", items.get(0)[3]);
        assertEquals("0xfffcfcff", items.get(0)[2]);
        assertEquals("night", items.get(1)[3]);
        assertEquals("0xff000000", items.get(1)[2]);
        assertEquals("0xff17171a", items.get(1)[5]);
        assertEquals(AmoledPalette.SURFACE, items.get(1)[6]);
        // A reference or attribute in light mode (dayLiteral null): night only
        AmoledChange reference = AmoledChange.of(SETTINGS, "sesl_background_color_dark",
                0x7f060001, 0xff17171a, surface, null, 0xfffcfcff, rules, BLUE);
        assertNull(reference.lightEntry);
        assertEquals(1, reference.toItems().size());
        // A color state list a rule names: night only, its light list kept
        AmoledRules csl = AmoledRules.defaults().withUserRules(pinned(SETTINGS, "x_selector"));
        AmoledPalette.Decision pinned = AmoledChange.night(SETTINGS, "x_selector", null, csl, BLUE);
        AmoledChange list = AmoledChange.of(SETTINGS, "x_selector", 0x7f060002, null, pinned,
                null, null, csl, BLUE);
        assertEquals(AmoledChange.CSL, list.type);
        assertNull(list.lightEntry);
        assertEquals(1, list.toItems().size());
        assertEquals("", list.toItems().get(0)[5]);
        // The accent in light mode only when chosen
        AmoledPalette.Decision accent = AmoledChange.night(SETTINGS, "basic_token_primary_50",
                BLUE, rules, BLUE);
        assertEquals(Integer.valueOf(BLUE), AmoledChange.of(SETTINGS, "basic_token_primary_50",
                1, BLUE, accent, BLUE, BLUE, rules, BLUE).lightEntry);
        rules.setAccentLight(true);
        assertEquals(accent.color, AmoledChange.of(SETTINGS, "basic_token_primary_50", 1, BLUE,
                accent, BLUE, BLUE, rules, BLUE).lightEntry);
    }

    @Test
    public void recordsRoundTrip() throws Exception {
        AmoledChange change = new AmoledChange("sesl_background_color_dark", 0x7f060001,
                AmoledChange.COLOR, 0xfffcfcff, 0xff17171a, 0xff0b0b0b, AmoledPalette.SURFACE,
                0xfffcfcff);
        AmoledChange csl = new AmoledChange("x_selector", 0x7f060002, AmoledChange.CSL, null,
                null, 0xff111111, AmoledPalette.USER_PIN, null);
        AmoledChange.Record record = new AmoledChange.Record(SETTINGS, "elevated", null, BLUE,
                "OVERLAY", "key", 42, Arrays.asList(change, csl));
        AmoledChange.Record back = AmoledChange.Record.fromJson(record.toJson());
        assertEquals(SETTINGS, back.packageName);
        assertEquals("elevated", back.level);
        assertNull(back.accent);
        assertEquals(BLUE, back.primary50);
        assertEquals(2, back.changes.size());
        AmoledChange first = back.change("sesl_background_color_dark");
        assertEquals(0x7f060001, first.id);
        assertEquals(Integer.valueOf(0xff17171a), first.stockDark);
        assertEquals(0xff0b0b0b, first.amoled);
        assertEquals(Integer.valueOf(0xfffcfcff), first.lightEntry);
        AmoledChange second = back.change("x_selector");
        assertEquals(AmoledChange.CSL, second.type);
        assertNull(second.stockDark);
        assertNull(second.lightEntry);
    }

    private static AmoledUserRules pinned(String pkg, String name) {
        AmoledUserRules user = new AmoledUserRules();
        user.pin(pkg, name, 0xff111111);
        return user;
    }
}
