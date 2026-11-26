/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.common.platform;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Dark mode only: a classic theme written for always-dark phones (unqualified colours) must leave
 * light mode exactly stock.
 */
public class ClassicDarkModeTest {

    private static final String COLOR = "28";
    private static final String BOOL = "18";

    private static String[] item(String target, String type, String value, String config) {
        return new String[]{target, type, value, config};
    }

    /**
     * The target's stock light values (what One UI 9's android shows in light mode); a missing
     * name, or one that follows the wallpaper, has none
     */
    private static final Map<String, String> STOCK = new HashMap<>();

    static {
        STOCK.put("color/notification_default_color_light", "0xff1f1f1f");
        STOCK.put("color/background_material_light", "0xfffafafa");
        STOCK.put("color/background_floating_material_light", "0xffffffff");
        STOCK.put("color/button_material_light", "0xffd6d7d7");
        STOCK.put("bool/config_dark", "0x00000000");
    }

    private static final ClassicDarkMode.StockDay STOCK_DAY = (type, name) ->
            STOCK.get(type + '/' + name);

    private static Map<String, String[]> byKey(List<String[]> items) {
        Map<String, String[]> map = new HashMap<>();
        for (String[] item : items) {
            String key = item[0] + '|' + item[3];
            assertNull("one entry per name and configuration: " + key, map.put(key, item));
        }
        return map;
    }

    /**
     * The classic android overlay seen on the phone: its unqualified values were white-on-white
     * heads-up notifications in light mode. Now they apply at night only, and the day
     * configuration carries the stock values.
     */
    @Test
    public void unqualifiedColoursComeOutStockInLightMode() {
        List<String[]> theme = Arrays.asList(
                item("color/notification_default_color_light", COLOR, "0xffffffff", ""),
                item("color/background_material_light", COLOR, "0xccffffff", ""),
                item("color/background_floating_material_light", COLOR, "0xccb3b3b3", ""),
                item("color/button_material_light", COLOR, "0xcc009587", ""));

        ClassicDarkMode.Result result = ClassicDarkMode.transform(theme, STOCK_DAY);
        Map<String, String[]> out = byKey(result.items);

        // Light mode (the default configuration): every value is the app's own
        for (String[] original : theme) {
            String[] day = out.get(original[0] + '|');
            assertEquals(original[0] + " by day", STOCK.get(original[0]), day[2]);
            // ...and the theme's value only at night
            String[] night = out.get(original[0] + "|night");
            assertEquals(original[0] + " at night", original[2], night[2]);
        }
        assertEquals(8, result.items.size());
        assertEquals(4, result.stockDay);
        assertTrue(result.dropped.isEmpty());
        // No theme value is left in a configuration light mode can match
        for (String[] item : result.items) {
            if (!ClassicDarkMode.hasQualifier(item[3], "night")) {
                assertEquals(item[0], STOCK.get(item[0]), item[2]);
            }
        }
    }

    @Test
    public void aThemesOwnNightValueWinsOverItsDayValue() {
        List<String[]> theme = Arrays.asList(
                item("color/background_material_light", COLOR, "0xffeeeeee", ""),
                item("color/background_material_light", COLOR, "0xff000000", "night"));
        Map<String, String[]> out = byKey(ClassicDarkMode.transform(theme, STOCK_DAY).items);
        assertEquals("0xff000000", out.get("color/background_material_light|night")[2]);
        assertEquals("0xfffafafa", out.get("color/background_material_light|")[2]);
        assertEquals(2, out.size());
    }

    @Test
    public void noStockValueMeansNightOnly() {
        // The stock light value follows the wallpaper (or the name is not the app's): no day
        // entry, light mode keeps following its source
        List<String[]> theme = new ArrayList<>();
        theme.add(item("color/accent_device_default_light", COLOR, "0xff00ff00", ""));
        ClassicDarkMode.Result result = ClassicDarkMode.transform(theme, STOCK_DAY);
        assertEquals(1, result.items.size());
        assertEquals("night", result.items.get(0)[3]);
        assertEquals(0, result.stockDay);

        ClassicDarkMode.Result unreadable = ClassicDarkMode.transform(theme, null);
        assertEquals(1, unreadable.items.size());
        assertEquals("night", unreadable.items.get(0)[3]);
    }

    @Test
    public void lightOnlyValuesAreDroppedAndReported() {
        List<String[]> theme = Arrays.asList(
                item("color/button_material_light", COLOR, "0xff123456", "notnight"),
                item("bool/config_dark", BOOL, "0xffffffff", ""));
        ClassicDarkMode.Result result = ClassicDarkMode.transform(theme, STOCK_DAY);
        Map<String, String[]> out = byKey(result.items);
        assertEquals(1, result.dropped.size());
        assertTrue(result.dropped.get(0).startsWith("color/button_material_light"));
        // Never written for both modes
        assertNull(out.get("color/button_material_light|notnight"));
        assertNull(out.get("color/button_material_light|"));
        // Bools follow the same rule
        assertEquals("0xffffffff", out.get("bool/config_dark|night")[2]);
        assertEquals("0x00000000", out.get("bool/config_dark|")[2]);
    }

    @Test
    public void qualifiedFoldersMoveToNightInPlace() {
        assertEquals("night", ClassicDarkMode.nightConfig(""));
        assertEquals("night-v27", ClassicDarkMode.nightConfig("v27"));
        assertEquals("land-night", ClassicDarkMode.nightConfig("land"));
        assertEquals("sw600dp-night-v24", ClassicDarkMode.nightConfig("sw600dp-v24"));
        assertEquals("night-xxhdpi", ClassicDarkMode.nightConfig("xxhdpi"));
        assertEquals("night-v31", ClassicDarkMode.nightConfig("night-v31"));
        assertNull(ClassicDarkMode.nightConfig("notnight"));

        List<String[]> theme = Arrays.asList(
                item("color/button_material_light", COLOR, "0xff111111", "v27"),
                item("color/button_material_light", COLOR, "0xff222222", ""));
        Map<String, String[]> out = byKey(ClassicDarkMode.transform(theme, STOCK_DAY).items);
        assertEquals("0xff111111", out.get("color/button_material_light|night-v27")[2]);
        assertEquals("0xff222222", out.get("color/button_material_light|night")[2]);
        assertEquals("0xffd6d7d7", out.get("color/button_material_light|")[2]);
        assertEquals(3, out.size());
    }

    @Test
    public void stockValuesAreWrittenOnlyWhenFixed() {
        int color = android.util.TypedValue.TYPE_INT_COLOR_ARGB8;
        assertEquals("0xff1f1f1f", ClassicDarkMode.stockItemValue("color", color, 0xff1f1f1f, true));
        // A colour that follows the wallpaper or a theme attribute: no day entry
        assertNull(ClassicDarkMode.stockItemValue("color", color, 0xff1f1f1f, false));
        // A colour state list
        assertNull(ClassicDarkMode.stockItemValue("color",
                android.util.TypedValue.TYPE_STRING, 0, true));
        assertEquals("0xffffffff", ClassicDarkMode.stockItemValue("bool",
                android.util.TypedValue.TYPE_INT_BOOLEAN, 1, false));
        assertEquals("0x00000004", ClassicDarkMode.stockItemValue("integer",
                android.util.TypedValue.TYPE_INT_DEC, 4, false));
    }
}
