/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.common.platform;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

public class VersionKeyTest {

    private static final String APK = "/data/app/~~Qx1==/com.android.vending-Zy2==/base.apk";

    @Test
    public void keyIsPathAndVersionCode() {
        assertEquals(APK + "|84061910", XposedBackend.versionKey(APK, 84061910L));
        assertEquals(APK + "|" + Long.MAX_VALUE, XposedBackend.versionKey(APK, Long.MAX_VALUE));
    }

    @Test
    public void updateOrReinstallChangesTheKey() {
        String key = XposedBackend.versionKey(APK, 84061910L);
        assertEquals(key, XposedBackend.versionKey(APK, 84061910L));
        assertNotEquals(key, XposedBackend.versionKey(APK, 84061911L));
        assertNotEquals(key, XposedBackend.versionKey(APK.replace("Zy2", "Zy3"), 84061910L));
    }

    @Test
    public void headerRoundTrip() {
        String key = XposedBackend.versionKey(APK, 84061910L);
        assertEquals(key, XposedBackend.readKey(XposedBackend.KEY_HEADER + key));
        assertEquals(key, XposedBackend.readKey("  " + XposedBackend.KEY_HEADER + key + " \r"));
        assertEquals(XposedBackend.KEY_HEADER, "# key=");
    }

    @Test
    public void noKeyWithoutHeader() {
        assertNull(XposedBackend.readKey(null));
        assertNull(XposedBackend.readKey(""));
        assertNull(XposedBackend.readKey("# key="));
        assertNull(XposedBackend.readKey("0x7f060001 0xff000000 0xff000000 0xff121212"));
    }
}
