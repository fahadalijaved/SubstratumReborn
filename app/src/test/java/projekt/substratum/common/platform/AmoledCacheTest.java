/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.common.platform;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

/**
 * The engine's cache: the rules part of its key, and entries read back exactly as written (the
 * overlay items of cached changes must be the ones computed).
 */
public class AmoledCacheTest {

    @Test
    public void rulesHashFollowsEverythingThatDecidesColors() {
        String pure = AmoledCache.rulesHash(AmoledRules.defaults());
        assertEquals(pure, AmoledCache.rulesHash(AmoledRules.defaults()));
        assertNotEquals(pure, AmoledCache.rulesHash(
                AmoledRules.defaults().setLevel(AmoledLevel.DIM)));
        assertNotEquals(pure, AmoledCache.rulesHash(
                AmoledRules.defaults().setAccent(0xff009587)));
        assertNotEquals(pure, AmoledCache.rulesHash(
                AmoledRules.defaults().setAccent(0xff009587).setAccentLight(true)));
        AmoledUserRules user = new AmoledUserRules();
        user.keep("com.android.settings", "divider_color");
        assertNotEquals(pure, AmoledCache.rulesHash(AmoledRules.defaults().withUserRules(user)));
        AmoledUserRules same = new AmoledUserRules();
        same.keep("com.android.settings", "divider_color");
        assertEquals(AmoledCache.rulesHash(AmoledRules.defaults().withUserRules(user)),
                AmoledCache.rulesHash(AmoledRules.defaults().withUserRules(same)));
        // What every app's stock colors follow (wallpaper palette, framework overlays)
        assertEquals(pure, AmoledCache.rulesHash(AmoledRules.defaults(), ""));
        String palette = AmoledCache.rulesHash(AmoledRules.defaults(), "a|b");
        assertNotEquals(pure, palette);
        assertEquals(palette, AmoledCache.rulesHash(AmoledRules.defaults(), "a|b"));
        assertNotEquals(palette, AmoledCache.rulesHash(AmoledRules.defaults(), "c|b"));
    }

    @Test
    public void entriesReadBackExactly() throws Exception {
        AmoledRules rules = AmoledRules.defaults();
        AmoledPalette.Decision bg = AmoledChange.night("com.example", "window_bg", 0xff17171a,
                rules, AmoledPalette.ONEUI_BLUE);
        AmoledPalette.Decision line = AmoledChange.night("com.example", "list_divider_color",
                0xff3a3a3d, rules, AmoledPalette.ONEUI_BLUE);
        List<AmoledChange> changes = Arrays.asList(
                AmoledChange.of("com.example", "window_bg", 0x7f060001, 0xff17171a, bg,
                        0xfff6f6f6, 0xfff6f6f6, rules, AmoledPalette.ONEUI_BLUE),
                AmoledChange.of("com.example", "list_divider_color", 0x7f060002, 0xff3a3a3d,
                        line, null, null, rules, AmoledPalette.ONEUI_BLUE));
        AmoledCache.Entry entry = new AmoledCache.Entry();
        entry.key = "12|34|/data/app/base.apk|fp|1|abc";
        entry.idsKey = "12|34|/data/app/base.apk|fp|1";
        entry.ids = Arrays.asList(0x7f060001, 0x7f060002, 0x7f060003);
        entry.primary50 = 0xff3e91ff;
        entry.changes = changes;
        entry.rejected.add("color/list_divider_color");
        entry.route = AmoledState.Route.OVERLAY.name();
        entry.hash = "deadbeef";
        entry.count = 1;
        AmoledCache.Entry back = AmoledCache.fromJson(AmoledCache.toJson(entry));
        assertEquals(entry.key, back.key);
        assertEquals(entry.idsKey, back.idsKey);
        assertEquals(entry.ids, back.ids);
        assertEquals(entry.primary50, back.primary50);
        assertEquals(entry.rejected, back.rejected);
        assertEquals(entry.route, back.route);
        assertEquals(entry.hash, back.hash);
        assertEquals(entry.count, back.count);
        List<String[]> items = AmoledChange.toItems(changes);
        List<String[]> itemsBack = AmoledChange.toItems(back.changes);
        assertEquals(items.size(), itemsBack.size());
        for (int i = 0; i < items.size(); i++) {
            assertEquals(Arrays.asList(items.get(i)), Arrays.asList(itemsBack.get(i)));
        }
        assertEquals(FabricatedValues.definitionXml(items),
                FabricatedValues.definitionXml(itemsBack));

        AmoledCache.Entry bare = new AmoledCache.Entry();
        bare.key = "k";
        bare.changes = java.util.Collections.emptyList();
        AmoledCache.Entry bareBack = AmoledCache.fromJson(AmoledCache.toJson(bare));
        assertNull(bareBack.ids);
        assertNull(bareBack.hash);
        assertEquals(0, bareBack.changes.size());
    }
}
