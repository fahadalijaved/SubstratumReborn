/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.common.platform;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RebornStatusTest {

    @Test
    public void heartbeatParsesTheModuleStatus() {
        RebornStatus.Heartbeat hb = RebornStatus.parseHeartbeat(
                "v=1103;t=2000;qs=1;layout=1;battery=0;guard=ok", 1103, 1000);
        assertTrue(hb.present);
        assertEquals(1103, hb.version);
        assertEquals(2000, hb.time);
        assertTrue(hb.qs);
        assertTrue(hb.layout);
        assertFalse(hb.battery);
        assertTrue(hb.current);
        assertTrue(hb.sinceBoot);
        assertTrue(hb.guardOk());
        assertTrue(hb.fresh());
    }

    @Test
    public void heartbeatFromAnOlderBuildOrBootIsNotFresh() {
        RebornStatus.Heartbeat old = RebornStatus.parseHeartbeat(
                "v=1102;t=2000;qs=1;layout=1;battery=0;guard=ok", 1103, 1000);
        assertTrue(old.present);
        assertFalse(old.current);
        assertFalse(old.fresh());

        RebornStatus.Heartbeat beforeBoot = RebornStatus.parseHeartbeat(
                "v=1103;t=500;guard=ok", 1103, 1000);
        assertFalse(beforeBoot.sinceBoot);
        assertFalse(beforeBoot.fresh());
    }

    @Test
    public void heartbeatGuardStates() {
        assertTrue(RebornStatus.parseHeartbeat("v=1;t=5;guard=tripped", 1, 0).guardTripped());
        RebornStatus.Heartbeat off = RebornStatus.parseHeartbeat("v=1;t=5;guard=off", 1, 0);
        assertTrue(off.guardOff());
        assertFalse(off.fresh());
    }

    @Test
    public void missingOrBrokenHeartbeat() {
        assertFalse(RebornStatus.parseHeartbeat(null, 1103, 0).present);
        assertFalse(RebornStatus.parseHeartbeat("", 1103, 0).present);
        RebornStatus.Heartbeat junk = RebornStatus.parseHeartbeat("v=x;t=;guard", 1103, 0);
        assertFalse(junk.present);
        assertNull(junk.guard);
    }

    @Test
    public void suVersionNamesTheRootSolution() {
        assertEquals("KernelSU 12081", RebornStatus.suVersion("12081:KernelSU"));
        assertEquals("MAGISK 27.0", RebornStatus.suVersion("27.0:MAGISK:R\n"));
        assertEquals("root", RebornStatus.suVersion(null));
        assertEquals("root", RebornStatus.suVersion("  "));
        assertEquals("v1.0", RebornStatus.suVersion("v1.0"));
    }

    @Test
    public void oneUiVersionFromTheProperty() {
        assertEquals("One UI 9.0", RebornStatus.oneUiName("90000"));
        assertEquals("One UI 8.5", RebornStatus.oneUiName("80500"));
        assertNull(RebornStatus.oneUiName(""));
        assertNull(RebornStatus.oneUiName(null));
        assertNull(RebornStatus.oneUiName("abc"));
    }

    @Test
    public void missingScopeListsWhatTheModuleNeeds() {
        RebornStatus.LsposedInfo info = new RebornStatus.LsposedInfo();
        info.level = RebornStatus.Level.OK;
        info.scope.add("com.android.systemui");
        info.scope.add("com.android.settings");
        assertEquals(1, info.missingScope().size());
        assertEquals("projekt.substratum", info.missingScope().get(0));
        info.level = RebornStatus.Level.UNKNOWN;
        assertTrue(info.missingScope().isEmpty());
    }
}
