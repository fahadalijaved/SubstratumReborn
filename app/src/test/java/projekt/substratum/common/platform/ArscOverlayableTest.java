/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.common.platform;

import org.junit.Assume;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The overlayable declarations read from a resource table: a small table built here (a package
 * with a type spec, two overlayables and three policies), and the framework table of the SDK.
 */
public class ArscOverlayableTest {

    private static final int PUBLIC = ArscOverlayable.POLICY_PUBLIC;
    private static final int SIGNATURE = ArscOverlayable.POLICY_SIGNATURE;
    private static final int SYSTEM = ArscOverlayable.POLICY_SYSTEM_PARTITION;

    @Test
    public void readsPoliciesOfEveryOverlayable() throws IOException {
        byte[] table = table(
                overlayable(policy(PUBLIC, 0x7f060001, 0x7f060002), policy(SIGNATURE, 0x7f060003)),
                overlayable(policy(PUBLIC | SYSTEM, 0x7f060003)));
        Map<Integer, Integer> declared = ArscOverlayable.parse(new ByteArrayInputStream(table));
        assertEquals(3, declared.size());
        assertEquals(Integer.valueOf(PUBLIC), declared.get(0x7f060001));
        assertEquals(Integer.valueOf(PUBLIC), declared.get(0x7f060002));
        assertEquals(Integer.valueOf(PUBLIC | SYSTEM | SIGNATURE), declared.get(0x7f060003));
    }

    @Test
    public void onlyPublicDeclarationsTakeAShellOverlay() throws IOException {
        byte[] table = table(overlayable(policy(PUBLIC, 0x7f060001), policy(SIGNATURE, 0x7f060002)));
        Map<Integer, Integer> declared = ArscOverlayable.parse(new ByteArrayInputStream(table));
        assertTrue(ArscOverlayable.accepted(declared, 0x7f060001, false));
        // declared, but only for overlays signed like the target
        assertFalse(ArscOverlayable.accepted(declared, 0x7f060002, false));
        // no declaration: "target resource has no overlayable declaration"
        assertFalse(ArscOverlayable.accepted(declared, 0x7f060009, false));
        // a target signed like the shell takes everything
        assertTrue(ArscOverlayable.accepted(declared, 0x7f060009, true));
    }

    @Test
    public void aTableWithoutOverlayablesDeclaresNothing() throws IOException {
        Map<Integer, Integer> declared = ArscOverlayable.parse(new ByteArrayInputStream(table()));
        assertTrue(declared.isEmpty());
        assertFalse(ArscOverlayable.accepted(declared, 0x7f060001, false));
    }

    @Test
    public void refusesBrokenTables() {
        byte[] table = table(overlayable(policy(PUBLIC, 0x7f060001)));
        for (byte[] broken : new byte[][]{Arrays.copyOf(table, table.length - 6),
                Arrays.copyOf(table, 5), new byte[]{1, 0, 12, 0, 0, 0, 0, 0}}) {
            try {
                ArscOverlayable.parse(new ByteArrayInputStream(broken));
                fail("parsed a broken table of " + broken.length + " bytes");
            } catch (IOException expected) {
                // fine
            }
        }
    }

    /**
     * The SDK's framework table (several packages, thousands of type chunks): read to the end.
     */
    @Test
    public void walksTheSdkFrameworkTable() throws IOException {
        File jar = sdkJar();
        Assume.assumeTrue("no SDK android.jar here", jar != null);
        try (ZipFile zip = new ZipFile(jar)) {
            ZipEntry entry = zip.getEntry("resources.arsc");
            assertNotNull(entry);
            try (InputStream in = zip.getInputStream(entry)) {
                Map<Integer, Integer> declared = ArscOverlayable.parse(in);
                for (Map.Entry<Integer, Integer> e : declared.entrySet()) {
                    assertEquals(0x01, e.getKey() >>> 24);
                    assertTrue(e.getValue() != 0);
                }
            }
        }
    }

    private static File sdkJar() {
        String home = System.getenv("ANDROID_HOME");
        for (String root : new String[]{home, "/opt/android-sdk"}) {
            if (root == null) continue;
            File[] platforms = new File(root, "platforms").listFiles();
            if (platforms == null) continue;
            Arrays.sort(platforms);
            for (int i = platforms.length - 1; i >= 0; i--) {
                File jar = new File(platforms[i], "android.jar");
                if (jar.isFile()) return jar;
            }
        }
        return null;
    }

    // A minimal resource table

    private static byte[] table(byte[]... overlayables) {
        ByteArrayOutputStream children = new ByteArrayOutputStream();
        // a type spec chunk the reader has to skip
        children.write(chunk(0x0202, 16, new byte[8], ints(0, 0, 0)), 0, 28);
        for (byte[] overlayable : overlayables) children.write(overlayable, 0, overlayable.length);
        byte[] packageHeader = new byte[288 - 8];
        ByteBuffer.wrap(packageHeader).order(ByteOrder.LITTLE_ENDIAN).putInt(0x7f);
        byte[] pkg = chunk(0x0200, 288, packageHeader, children.toByteArray());
        byte[] pool = chunk(0x0001, 28, new byte[20], new byte[0]);
        byte[] header = new byte[4];
        ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).putInt(1);
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(pool, 0, pool.length);
        body.write(pkg, 0, pkg.length);
        return chunk(0x0002, 12, header, body.toByteArray());
    }

    private static byte[] overlayable(byte[]... policies) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (byte[] policy : policies) body.write(policy, 0, policy.length);
        // name and actor, char16_t[256] each
        return chunk(0x0204, 1032, new byte[1024], body.toByteArray());
    }

    private static byte[] policy(int flags, int... ids) {
        return chunk(0x0205, 16, ints(flags, ids.length), ints(ids));
    }

    /**
     * A chunk: type, header size, total size, the rest of the header, then the body.
     */
    private static byte[] chunk(int type, int headerSize, byte[] headerRest, byte[] body) {
        if (headerRest.length != headerSize - 8) throw new IllegalArgumentException();
        ByteBuffer b = ByteBuffer.allocate(headerSize + body.length).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort((short) type).putShort((short) headerSize).putInt(headerSize + body.length);
        b.put(headerRest).put(body);
        return b.array();
    }

    private static byte[] ints(int... values) {
        ByteBuffer b = ByteBuffer.allocate(values.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int v : values) b.putInt(v);
        return b.array();
    }
}
