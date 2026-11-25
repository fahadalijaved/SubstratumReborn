/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.common.platform;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AmoledStateTest {

    @Test
    public void jsonRoundTrip() throws Exception {
        AmoledState state = new AmoledState();
        state.enabled = true;
        state.rulesTheme = "projekt.reborn.amoled";
        state.appliedAt = 1764100000000L;
        state.restoredAt = 1764100500000L;
        state.bootCount = 42;
        state.appliedBootCount = 41;
        AmoledState.App store = state.app("com.android.vending");
        store.route = AmoledState.Route.MODULE;
        store.userEnabled = false;
        store.versionKey = "/data/app/~~a==/com.android.vending-b==/base.apk|84061910";
        store.colors = 57;
        store.restartNeeded = true;
        store.error = "java.lang.IllegalStateException: \"quoted\"";
        AmoledState.App settings = state.app("com.android.settings");
        settings.route = AmoledState.Route.OVERLAY;
        settings.colors = 212;

        String json = state.toJson();
        AmoledState read = AmoledState.fromJson(json);
        assertTrue(read.enabled);
        assertEquals(state.rulesTheme, read.rulesTheme);
        assertEquals(state.appliedAt, read.appliedAt);
        assertEquals(state.restoredAt, read.restoredAt);
        assertEquals(42, read.bootCount);
        assertEquals(41, read.appliedBootCount);
        assertEquals(2, read.perApp.size());
        AmoledState.App readStore = read.perApp.get("com.android.vending");
        assertEquals(AmoledState.Route.MODULE, readStore.route);
        assertFalse(readStore.userEnabled);
        assertEquals(store.versionKey, readStore.versionKey);
        assertEquals(57, readStore.colors);
        assertTrue(readStore.restartNeeded);
        assertEquals(store.error, readStore.error);
        AmoledState.App readSettings = read.perApp.get("com.android.settings");
        assertEquals(AmoledState.Route.OVERLAY, readSettings.route);
        assertTrue(readSettings.userEnabled);
        assertNull(readSettings.versionKey);
        assertNull(readSettings.error);
        // Stable: writing what was read gives the same file
        assertEquals(json, read.toJson());
        assertTrue(json.contains("\"version\": 1"));
    }

    @Test
    public void missingAndUnknownValuesFallBack() throws Exception {
        AmoledState state = AmoledState.fromJson(
                "{\"version\":1,\"perApp\":{\"a.b\":{\"route\":\"SOMETHING_NEW\"},\"c.d\":{}}}");
        assertFalse(state.enabled);
        assertNull(state.rulesTheme);
        assertEquals(0, state.appliedAt);
        assertEquals(AmoledState.Route.NONE, state.perApp.get("a.b").route);
        assertTrue(state.perApp.get("c.d").userEnabled);
        assertTrue(state.isUserEnabled("not.listed"));
        assertEquals(0, state.countThemed());
    }

    @Test
    public void survivedRebootNeedsARestoreAfterABootAfterTheApply() {
        AmoledState state = new AmoledState();
        state.enabled = true;
        state.appliedAt = 1000;
        state.appliedBootCount = 7;
        state.bootCount = 7;
        assertFalse(state.survivedReboot());
        state.restoredAt = 2000;
        assertFalse(state.survivedReboot()); // same boot
        state.bootCount = 8;
        assertTrue(state.survivedReboot());
        state.enabled = false;
        assertFalse(state.survivedReboot());
    }

    @Test
    public void migratesOldSwitches() {
        AmoledState state = AmoledState.migrate(
                new HashSet<>(Arrays.asList("com.android.settings", "com.android.vending")),
                Arrays.asList(
                        "com.android.vending.amoled.ids.off",
                        "com.google.android.apps.maps.amoled.ids",
                        "com.google.android.youtube.amoled.ids.off",
                        XposedBackend.AMOLED_FLAG,
                        "qs-options",
                        "overlay.apk",
                        ".amoled.ids.off"),
                true);
        assertTrue(state.enabled);
        assertEquals(4, state.perApp.size());
        AmoledState.App store = state.perApp.get("com.android.vending");
        assertEquals(AmoledState.Route.MODULE, store.route);
        assertFalse(store.userEnabled);
        AmoledState.App youtube = state.perApp.get("com.google.android.youtube");
        assertEquals(AmoledState.Route.MODULE, youtube.route);
        assertFalse(youtube.userEnabled);
        AmoledState.App maps = state.perApp.get("com.google.android.apps.maps");
        assertEquals(AmoledState.Route.MODULE, maps.route);
        assertTrue(maps.userEnabled);
        AmoledState.App settings = state.perApp.get("com.android.settings");
        assertEquals(AmoledState.Route.NONE, settings.route);
        assertFalse(settings.userEnabled);
        assertEquals(1, state.countThemed());
        assertEquals(0, state.appliedAt); // restore keeps the old behaviour until an apply
    }

    @Test
    public void migratesNothing() {
        AmoledState state = AmoledState.migrate(Collections.emptySet(),
                Collections.emptyList(), false);
        assertFalse(state.enabled);
        assertTrue(state.perApp.isEmpty());
    }

    @Test
    public void parkedFileNames() {
        assertEquals("com.android.vending",
                AmoledState.offPackage("com.android.vending.amoled.ids.off"));
        assertNull(AmoledState.offPackage("com.android.vending.amoled.ids"));
        assertNull(AmoledState.offPackage(".amoled.ids.off"));
        assertNull(AmoledState.offPackage("hooks.off"));
    }
}
