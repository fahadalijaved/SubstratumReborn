/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.common.platform;

import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FabricatedColorsTest {

    /** A tiny framework: white everywhere, a surface that turns dark at night. */
    private static final FabricatedValues.FrameworkColors FRAMEWORK = (name, night) -> {
        switch (name) {
            case "white":
                return 0xffffffff;
            case "surface_header":
                return night ? 0xff101010 : 0xfff5f5f5;
            default:
                return null;
        }
    };

    private static Map<String, String> colors(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put(pairs[i], pairs[i + 1]);
        return map;
    }

    private static Integer resolve(String text, String config, Map<String, String> colors) {
        return FabricatedValues.resolveColor(text, config, colors, FRAMEWORK);
    }

    @Test
    public void literalForms() {
        Map<String, String> none = new HashMap<>();
        assertEquals(Integer.valueOf(0xff112233), resolve("#112233", "", none));
        assertEquals(Integer.valueOf(0x80112233), resolve("#80112233", "", none));
        assertEquals(Integer.valueOf(0xffaabbcc), resolve("#abc", "", none));
        assertEquals(Integer.valueOf(0x88aabbcc), resolve("#8abc", "", none));
        assertEquals(Integer.valueOf(0xff000000), resolve("  #000000 ", "", none));
        assertNull(resolve("#12345", "", none));
        assertNull(resolve("#zzzzzz", "", none));
        assertNull(resolve("", "", none));
    }

    @Test
    public void localReferencesFollowTheSameConfigThenTheDefault() {
        Map<String, String> map = colors(
                "accent|", "#ff0000",
                "accent|night", "#00ff00",
                "link|", "@color/accent",
                "only_default|", "#0000ff");
        assertEquals(Integer.valueOf(0xffff0000), resolve("@color/accent", "", map));
        assertEquals(Integer.valueOf(0xff00ff00), resolve("@color/accent", "night", map));
        // link only exists in values/, accent has a night value: the night lookup follows it
        assertEquals(Integer.valueOf(0xff00ff00), resolve("@color/link", "night", map));
        assertEquals(Integer.valueOf(0xff0000ff), resolve("@color/only_default", "night", map));
    }

    @Test
    public void chainsStopAfterFiveReferences() {
        Map<String, String> map = colors(
                "c0|", "#123456",
                "c1|", "@color/c0",
                "c2|", "@color/c1",
                "c3|", "@color/c2",
                "c4|", "@color/c3",
                "c5|", "@color/c4");
        // @color/c4 -> c3 -> c2 -> c1 -> c0: five references
        assertEquals(Integer.valueOf(0xff123456), resolve("@color/c4", "", map));
        // six
        assertNull(resolve("@color/c5", "", map));
    }

    @Test
    public void cyclesDoNotLoop() {
        Map<String, String> map = colors("a|", "@color/b", "b|", "@color/a");
        assertNull(resolve("@color/a", "", map));
    }

    @Test
    public void frameworkReferencesUseTheNightFlag() {
        Map<String, String> none = new HashMap<>();
        assertEquals(Integer.valueOf(0xffffffff), resolve("@android:color/white", "", none));
        assertEquals(Integer.valueOf(0xfff5f5f5),
                resolve("@*android:color/surface_header", "", none));
        assertEquals(Integer.valueOf(0xff101010),
                resolve("@*android:color/surface_header", "night", none));
        assertEquals(Integer.valueOf(0xff101010),
                resolve("@*android:color/surface_header", "night-v31", none));
        assertNull(resolve("@android:color/does_not_exist", "", none));
        assertNull(FabricatedValues.resolveColor("@android:color/white", "", none, null));
    }

    @Test
    public void everythingElseStaysWithTheModule() {
        Map<String, String> map = colors("a|", "#ffffff");
        assertNull(resolve("?android:attr/colorAccent", "", map));
        assertNull(resolve("@color/target_own_color", "", map));
        assertNull(resolve("@null", "", map));
        assertNull(resolve("@drawable/a", "", map));
    }

    @Test
    public void resolveColorsBuildsItemsAndReportsLeftovers() {
        Map<String, String> map = colors(
                "bg|", "@*android:color/surface_header",
                "text|", "@color/accent",
                "accent|", "#ff0000",
                "attr|", "?attr/colorPrimary",
                "night_only|night", "@android:color/white");
        Map<String, String[]> items = new LinkedHashMap<>();
        Set<String> skipped = new HashSet<>();
        FabricatedValues.resolveColors(map, FRAMEWORK, items, skipped);

        assertEquals("0xfff5f5f5", items.get("color/bg|")[2]);
        assertEquals("", items.get("color/bg|")[3]);
        // The framework color differs at night: a night entry follows it
        assertEquals("0xff101010", items.get("color/bg|night")[2]);
        assertEquals("night", items.get("color/bg|night")[3]);
        assertEquals("0xffff0000", items.get("color/text|")[2]);
        assertFalse(items.containsKey("color/text|night"));
        assertEquals("0xffffffff", items.get("color/night_only|night")[2]);
        assertEquals("color/bg", items.get("color/bg|")[0]);
        assertEquals("28", items.get("color/bg|")[1]); // TYPE_INT_COLOR_ARGB8

        assertFalse(items.containsKey("color/attr|"));
        assertTrue(skipped.contains("color/attr"));
        assertEquals(1, skipped.size());
    }

    @Test
    public void nightQualifier() {
        assertTrue(FabricatedValues.isNight("night"));
        assertTrue(FabricatedValues.isNight("land-night-v31"));
        assertFalse(FabricatedValues.isNight(""));
        assertFalse(FabricatedValues.isNight("notnight"));
    }
}
