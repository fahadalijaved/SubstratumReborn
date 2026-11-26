/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.common.platform;

import com.google.android.material.color.utilities.Hct;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The preview's colors, from the shipped stock asset through the rules.
 */
public class AmoledPreviewTest {

    private static AmoledPreview.Stock stock() throws Exception {
        File asset = new File("src/main/assets/" + AmoledPreview.ASSET);
        if (!asset.isFile()) asset = new File("app/src/main/assets/" + AmoledPreview.ASSET);
        return AmoledPreview.Stock.fromJson(new String(Files.readAllBytes(asset.toPath()),
                StandardCharsets.UTF_8));
    }

    private static int amoled(Map<String, int[]> roles, String role) {
        int[] pair = roles.get(role);
        assertNotNull(role, pair);
        return pair[AmoledPreview.AMOLED];
    }

    @Test
    public void everyRoleHasStockColors() throws Exception {
        Map<String, int[]> roles = AmoledPreview.roles(stock(), AmoledRules.defaults());
        assertEquals(AmoledPreview.ROLES, new java.util.ArrayList<>(roles.keySet()));
        assertEquals(0xff010102, roles.get(AmoledPreview.SETTINGS_PAGE)[AmoledPreview.STOCK]);
        assertEquals(0xff17171a, roles.get(AmoledPreview.SETTINGS_CARD)[AmoledPreview.STOCK]);
        assertEquals(0x26ffffff, roles.get(AmoledPreview.QS_TRACK)[AmoledPreview.STOCK]);
    }

    @Test
    public void pureBlack() throws Exception {
        Map<String, int[]> roles = AmoledPreview.roles(stock(), AmoledRules.defaults());
        assertEquals(0xff000000, amoled(roles, AmoledPreview.SETTINGS_PAGE));
        assertEquals(0xff000000, amoled(roles, AmoledPreview.SETTINGS_CARD));
        assertEquals(0xff000000, amoled(roles, AmoledPreview.QS_PANEL));
        assertEquals(0xffffffff, amoled(roles, AmoledPreview.QS_TILE_ON));
        assertEquals(QsOptions.DEFAULT_TRACK, amoled(roles, AmoledPreview.QS_TRACK));
        assertEquals(0xff1f1f1f, amoled(roles, AmoledPreview.POPUP_OUTLINE));
        // Text and the stock accent stay
        assertEquals(roles.get(AmoledPreview.SETTINGS_TITLE)[AmoledPreview.STOCK],
                amoled(roles, AmoledPreview.SETTINGS_TITLE));
        assertEquals(0xff387aff, amoled(roles, AmoledPreview.SETTINGS_SWITCH_ON));
    }

    @Test
    public void elevatedCardsShowOnBlack() throws Exception {
        AmoledRules rules = AmoledRules.defaults().setLevel(AmoledLevel.ELEVATED);
        Map<String, int[]> roles = AmoledPreview.roles(stock(), rules);
        assertEquals(0xff000000, amoled(roles, AmoledPreview.SETTINGS_PAGE));
        assertEquals(0xff0b0b0b, amoled(roles, AmoledPreview.SETTINGS_CARD));
        assertEquals(0xff141414, amoled(roles, AmoledPreview.POPUP_BG));
        Map<String, int[]> dim = AmoledPreview.roles(stock(),
                AmoledRules.defaults().setLevel(AmoledLevel.DIM));
        assertEquals(0xff0e0e0e, amoled(dim, AmoledPreview.SETTINGS_PAGE));
        assertEquals(0xff141414, amoled(dim, AmoledPreview.SETTINGS_CARD));
    }

    @Test
    public void spritePreset() throws Exception {
        AmoledPresets.Preset sprite = AmoledPresets.preset(AmoledPresets.SPRITE);
        AmoledRules rules = AmoledRules.defaults().withOptions(sprite.level, sprite.accent, null,
                sprite.tile, null);
        Map<String, int[]> roles = AmoledPreview.roles(stock(), rules);
        assertEquals(AmoledPresets.SPRITE_TEAL, amoled(roles, AmoledPreview.QS_TILE_ON));
        double teal = Hct.fromInt(AmoledPresets.SPRITE_TEAL).getHue();
        for (String role : new String[]{AmoledPreview.SETTINGS_SWITCH_ON,
                AmoledPreview.SETTINGS_ACCENT}) {
            int color = amoled(roles, role);
            int stock = roles.get(role)[AmoledPreview.STOCK];
            assertEquals(role, teal, Hct.fromInt(color).getHue(), 6);
            assertEquals(role, Hct.fromInt(stock).getTone(), Hct.fromInt(color).getTone(), 1);
        }
        assertEquals(0xff0b0b0b, amoled(roles, AmoledPreview.SETTINGS_CARD));
    }

    @Test
    public void deviceRecordsAndUserRules() throws Exception {
        AmoledPreview.Stock stock = stock();
        // The device's own stock color, once AMOLED has read it
        stock.use(new AmoledChange.Record("com.android.settings", "pure", null,
                AmoledPalette.ONEUI_BLUE, "OVERLAY", null, 1, Collections.singletonList(
                new AmoledChange("sesl_background_color_dark", 1, AmoledChange.COLOR,
                        0xfffcfcff, 0xff202022, 0xff000000, AmoledPalette.SURFACE, null))));
        assertEquals(Integer.valueOf(0xff202022), stock.stock(AmoledPreview.SETTINGS_CARD));
        AmoledUserRules user = new AmoledUserRules();
        user.keep("com.android.settings", "sesl_background_color_dark");
        user.pin("com.android.systemui", "qs_tile_round_background_on", 0xffff8800);
        Map<String, int[]> roles = AmoledPreview.roles(stock,
                AmoledRules.defaults().setLevel(AmoledLevel.ELEVATED).withUserRules(user));
        assertEquals(0xff202022, amoled(roles, AmoledPreview.SETTINGS_CARD));
        assertEquals(0xffff8800, amoled(roles, AmoledPreview.QS_TILE_ON));
        assertTrue(roles.containsKey(AmoledPreview.LIST_ROW));
    }
}
