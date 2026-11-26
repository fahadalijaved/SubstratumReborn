/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.common.platform;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Classic overlays whose entries idmap2 refuses: the refused entries go to the module's list
 * (keep: lines), the rest stays native. Also the held markers.
 */
public class ClassicRouteTest {

    private static String[] item(String target, String value, String config) {
        return new String[]{target, "28", value, config};
    }

    /**
     * Seen with DeepDarkness on One UI 9: the telecom, package installer and SoundAlive overlays
     * showed "---" in cmd overlay list. A refused entry must not sink the others, and must reach
     * the module instead of nowhere.
     */
    @Test
    public void aRefusedEntryIsRoutedToTheModuleList() {
        List<String[]> items = Arrays.asList(
                item("color/incall_background", "0xff000000", ""),
                item("color/incall_background", "0xff000000", "night"),
                item("color/dialer_accent", "0xff00bcd4", ""),
                item("color/call_card_bg", "0xff111111", ""));
        Set<String> refused = new HashSet<>(Collections.singletonList("color/incall_background"));
        Set<String> toModule = new LinkedHashSet<>();

        List<String[]> native_ = ClassicRoute.withoutRefused(items, refused, toModule);

        assertEquals(2, native_.size());
        for (String[] item : native_) assertFalse(refused.contains(item[0]));
        assertEquals(Collections.singleton("color/incall_background"), toModule);

        // The index tells the module to keep applying it (by name, from the overlay)
        String index = ClassicRoute.index("com.android.server.telecom",
                "com.android.server.telecom.DeepDarkness", true, true,
                Arrays.asList("color/incall_background", "color/dialer_accent",
                        "color/call_card_bg", "drawable/ic_call"), toModule);
        List<String> lines = Arrays.asList(index.split("\n"));
        assertEquals("target=com.android.server.telecom", lines.get(0));
        assertEquals("package=com.android.server.telecom.DeepDarkness", lines.get(1));
        assertTrue(lines.contains(XposedBackend.FABRICATED));
        assertTrue(lines.contains(XposedBackend.DARK_ONLY));
        assertTrue(lines.contains("keep:color/incall_background"));
        assertTrue(lines.contains("color/dialer_accent"));
        assertTrue(lines.contains("drawable/ic_call"));
        assertFalse(lines.contains("keep:color/dialer_accent"));
    }

    @Test
    public void predictionUsesThePublicOverlayables() {
        Map<Integer, Integer> declared = new HashMap<>();
        declared.put(0x7f060001, ArscOverlayable.POLICY_PUBLIC);
        declared.put(0x7f060002, ArscOverlayable.POLICY_SIGNATURE);
        Map<String, Integer> nameToId = new LinkedHashMap<>();
        nameToId.put("color/public_one", 0x7f060001);
        nameToId.put("color/signature_one", 0x7f060002);
        nameToId.put("color/undeclared", 0x7f060003);
        Set<String> refused = ArscOverlayable.refusedBy(declared, nameToId);
        assertEquals(new LinkedHashSet<>(Arrays.asList("color/signature_one",
                "color/undeclared")), refused);
    }

    @Test
    public void anIndexIsReroutedAfterARefusal() {
        String index = ClassicRoute.index("com.sec.android.app.soundalive", "p.overlay", true,
                false, Arrays.asList("color/a", "color/b", "dimen/c"), new HashSet<>());
        String some = ClassicRoute.rerouted(index,
                new HashSet<>(Collections.singletonList("color/b")), false);
        assertTrue(some.contains("\nkeep:color/b\n"));
        assertTrue(some.contains("\ncolor/a\n"));
        assertTrue(some.contains(XposedBackend.FABRICATED));
        // Rerouting twice does not stack the prefix
        assertEquals(some, ClassicRoute.rerouted(some,
                new HashSet<>(Collections.singletonList("color/b")), false));

        // Nothing registered: the module applies all of it
        String all = ClassicRoute.rerouted(index, new HashSet<>(Arrays.asList("color/a",
                "color/b")), true);
        assertFalse(all.contains(XposedBackend.FABRICATED));
        assertFalse(all.contains("keep:"));
        assertTrue(all.contains("\ncolor/a\n"));
    }

    @Test
    public void refusedOverlaysAreFoundInTheList() {
        String out = "com.android.server.telecom\n" +
                "--- com.android.shell:com.android.server.telecom.DeepDarkness\n" +
                "com.android.settings\n" +
                "[x] com.android.shell:com.android.settings.DeepDarkness\n" +
                "[ ] com.android.shell:com.sec.android.app.soundalive.DeepDarkness\n" +
                "[x] com.samsung.android.themecenter\n";
        Map<String, Character> states = ClassicRoute.shellStates(out);
        assertEquals(Character.valueOf('-'),
                states.get("com.android.server.telecom.DeepDarkness"));
        assertEquals(Character.valueOf('x'), states.get("com.android.settings.DeepDarkness"));
        assertEquals(Character.valueOf(' '),
                states.get("com.sec.android.app.soundalive.DeepDarkness"));
        assertEquals(3, states.size());
    }

    @Test
    public void aSavedDefinitionReadsBack() {
        List<String[]> items = new ArrayList<>();
        items.add(item("color/a", "0xff000000", ""));
        items.add(item("color/a", "0xff111111", "night-v27"));
        items.add(new String[]{"bool/b", "18", "0xffffffff", ""});
        List<String[]> back = ClassicRoute.parseDefinition(FabricatedValues.definitionXml(items));
        assertEquals(items.size(), back.size());
        for (int i = 0; i < items.size(); i++) {
            assertTrue(Arrays.equals(items.get(i), back.get(i)));
        }
    }

    @Test
    public void holdingMovesOnlyEnabledMarkers() {
        List<String> listing = Arrays.asList(
                "a.apk", "a.idx", "a.enabled",
                "b.apk", "b.idx", "b" + XposedBackend.HELD,
                "c.apk", "c.idx");
        List<String[]> hold = ClassicRoute.markerMoves(listing, Arrays.asList("a", "b", "c"),
                true);
        assertEquals(1, hold.size());
        assertEquals("a.enabled", hold.get(0)[0]);
        assertEquals("a" + XposedBackend.HELD, hold.get(0)[1]);

        List<String[]> release = ClassicRoute.markerMoves(listing, Arrays.asList("a", "b", "c"),
                false);
        assertEquals(1, release.size());
        assertEquals("b" + XposedBackend.HELD, release.get(0)[0]);
        assertEquals("b.enabled", release.get(0)[1]);
        // The module never loads a held marker: it only reads names ending in .enabled
        assertFalse(("b" + XposedBackend.HELD).endsWith(XposedBackend.ENABLED));
    }

    @Test
    public void boolsAndIntegersAreSplitOffForTheModule() {
        List<String[]> items = Arrays.asList(
                item("color/a", "0xff000000", ""),
                new String[]{"bool/config_x", "18", "0xffffffff", ""},
                new String[]{"integer/config_y", "16", "0x00000004", ""},
                new String[]{"bool/config_x", "18", "0x00000000", "night"});
        Set<String> scalars = new HashSet<>();
        List<String[]> colors = FabricatedValues.withoutScalars(items, scalars);
        assertEquals(1, colors.size());
        assertEquals("color/a", colors.get(0)[0]);
        assertEquals(new HashSet<>(Arrays.asList("bool/config_x", "integer/config_y")), scalars);
    }
}
