/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.common.platform;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Which resources of an app are declared {@code <overlayable>}, read from its compiled resource
 * table ({@code resources.arsc}), with the policies that may overlay them. Pure Java: unit tested.
 * <p>
 * idmap2 refuses a fabricated overlay owned by the shell for any resource without an overlayable
 * declaration, unless the target is signed like the shell (platform-signed: android, System UI,
 * Settings): "target resource has no overlayable declaration", and the whole overlay ends up in
 * STATE_NO_IDMAP. Knowing the declarations up front, those colors go straight to the LSPosed
 * module instead of an overlay that is refused and registered again.
 * <p>
 * The table is streamed, skipping everything but the package headers and the
 * {@code RES_TABLE_OVERLAYABLE_TYPE} chunks with their {@code RES_TABLE_OVERLAYABLE_POLICY_TYPE}
 * children: {@code uint32 policy_flags, uint32 entry_count}, then entry_count resource ids.
 */
public final class ArscOverlayable {

    static final int RES_STRING_POOL_TYPE = 0x0001;
    static final int RES_TABLE_TYPE = 0x0002;
    static final int RES_TABLE_PACKAGE_TYPE = 0x0200;
    static final int RES_TABLE_OVERLAYABLE_TYPE = 0x0204;
    static final int RES_TABLE_OVERLAYABLE_POLICY_TYPE = 0x0205;
    /**
     * Policy flags (ResTable_overlayable_policy_header::PolicyFlags)
     */
    public static final int POLICY_PUBLIC = 0x00000001;
    public static final int POLICY_SYSTEM_PARTITION = 0x00000002;
    public static final int POLICY_SIGNATURE = 0x00000010;
    private static final String ARSC = "resources.arsc";
    private static final String SHELL = "com.android.shell";

    private ArscOverlayable() {
    }

    /**
     * The overlayable declarations of an installed app: its base APK and splits.
     *
     * @return Resource id -> policy flags (of every overlayable declaring it, combined); empty
     * when the app declares none; null when a table could not be read
     */
    public static Map<Integer, Integer> read(String sourceDir, String[] splitSourceDirs) {
        Map<Integer, Integer> all = new HashMap<>();
        if (!readApk(sourceDir, all)) return null;
        if (splitSourceDirs != null) {
            for (String split : splitSourceDirs) {
                // a split without a table is fine; an unreadable one is not
                if (!readApk(split, all)) return null;
            }
        }
        return all;
    }

    private static boolean readApk(String path, Map<Integer, Integer> out) {
        if (path == null) return false;
        try (ZipFile zip = new ZipFile(path)) {
            ZipEntry entry = zip.getEntry(ARSC);
            if (entry == null) return true;
            try (InputStream in = new BufferedInputStream(zip.getInputStream(entry), 1 << 16)) {
                out.putAll(merge(out, parse(in)));
            }
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static Map<Integer, Integer> merge(Map<Integer, Integer> into,
                                               Map<Integer, Integer> more) {
        Map<Integer, Integer> merged = new HashMap<>(more);
        for (Map.Entry<Integer, Integer> e : merged.entrySet()) {
            Integer before = into.get(e.getKey());
            if (before != null) e.setValue(e.getValue() | before);
        }
        return merged;
    }

    /**
     * Parse one resource table. Pure: unit tested.
     *
     * @return Resource id -> policy flags, empty when the table declares no overlayable
     * @throws IOException Not a resource table, or cut short
     */
    public static Map<Integer, Integer> parse(InputStream input) throws IOException {
        Reader in = new Reader(input);
        Map<Integer, Integer> out = new HashMap<>();
        int type = in.u16();
        int headerSize = in.u16();
        long size = in.u32();
        if (type != RES_TABLE_TYPE || headerSize < 12 || size < headerSize) {
            throw new IOException("not a resource table");
        }
        in.skip(headerSize - 8);
        long pos = headerSize;
        while (pos + 8 <= size) {
            int chunkType = in.u16();
            int chunkHeader = in.u16();
            long chunkSize = in.u32();
            if (chunkSize < 8 || chunkHeader < 8 || chunkHeader > chunkSize) break;
            if (chunkType == RES_TABLE_PACKAGE_TYPE) {
                readPackage(in, chunkHeader, chunkSize, out);
            } else {
                in.skip(chunkSize - 8);
            }
            pos += chunkSize;
        }
        return out;
    }

    private static void readPackage(Reader in, int headerSize, long size,
                                    Map<Integer, Integer> out) throws IOException {
        in.skip(headerSize - 8);
        long pos = headerSize;
        while (pos + 8 <= size) {
            int type = in.u16();
            int header = in.u16();
            long chunkSize = in.u32();
            if (chunkSize < 8 || header < 8 || header > chunkSize || pos + chunkSize > size) {
                // a broken chunk: skip what is left of the package
                in.skip(size - pos - 8);
                return;
            }
            if (type == RES_TABLE_OVERLAYABLE_TYPE) {
                readOverlayable(in, header, chunkSize, out);
            } else {
                in.skip(chunkSize - 8);
            }
            pos += chunkSize;
        }
        if (pos < size) in.skip(size - pos);
    }

    /**
     * ResTable_overlayable_header: name and actor (char16_t[256] each), then its policy chunks.
     */
    private static void readOverlayable(Reader in, int headerSize, long size,
                                        Map<Integer, Integer> out) throws IOException {
        in.skip(headerSize - 8);
        long pos = headerSize;
        while (pos + 8 <= size) {
            int type = in.u16();
            int header = in.u16();
            long chunkSize = in.u32();
            if (chunkSize < 8 || header < 8 || header > chunkSize || pos + chunkSize > size) {
                in.skip(size - pos - 8);
                return;
            }
            if (type == RES_TABLE_OVERLAYABLE_POLICY_TYPE && header >= 16) {
                int flags = (int) in.u32();
                long count = in.u32();
                in.skip(header - 16);
                long room = (chunkSize - header) / 4;
                long n = Math.min(count, room);
                for (long i = 0; i < n; i++) {
                    int id = (int) in.u32();
                    Integer before = out.get(id);
                    out.put(id, before == null ? flags : before | flags);
                }
                in.skip(chunkSize - header - n * 4);
            } else {
                in.skip(chunkSize - 8);
            }
            pos += chunkSize;
        }
        if (pos < size) in.skip(size - pos);
    }

    /**
     * Whether idmap2 lets a shell-owned fabricated overlay change a resource. Such an overlay
     * fulfils the PUBLIC policy, and SIGNATURE when the target is signed like the shell; a
     * resource without an overlayable declaration needs one of the default policies, which here
     * is SIGNATURE. A target signed like the shell is left to idmap2 entirely (a refusal falls
     * back to the retry).
     *
     * @param declarations   {@link #read} of the target
     * @param signatureMatch The target is signed like com.android.shell
     */
    public static boolean accepted(Map<Integer, Integer> declarations, int id,
                                   boolean signatureMatch) {
        if (signatureMatch) return true;
        Integer policies = declarations.get(id);
        return policies != null && (policies & POLICY_PUBLIC) != 0;
    }

    /**
     * The resources a shell-owned fabricated overlay may not change in an app: none when the app
     * is signed like the shell or its table cannot be read (idmap2 decides then), else every one
     * outside a PUBLIC overlayable. Used by the AMOLED engine and for classic overlays.
     *
     * @param nameToId The overlay's targets ({@code type/name}) and their ids in the app
     * @return The refused targets, in the given order
     */
    public static Set<String> refusedByShell(PackageManager pm, ApplicationInfo info,
                                             Map<String, Integer> nameToId) {
        if (nameToId.isEmpty() || info == null) return new LinkedHashSet<>();
        try {
            if (pm.checkSignatures(SHELL, info.packageName) == PackageManager.SIGNATURE_MATCH) {
                return new LinkedHashSet<>();
            }
        } catch (RuntimeException e) {
            return new LinkedHashSet<>();
        }
        Map<Integer, Integer> declared = read(info.sourceDir, info.splitSourceDirs);
        if (declared == null) return new LinkedHashSet<>();
        return refusedBy(declared, nameToId);
    }

    /**
     * The part of {@link #refusedByShell} after the signature check. Pure.
     */
    static Set<String> refusedBy(Map<Integer, Integer> declared, Map<String, Integer> nameToId) {
        Set<String> refused = new LinkedHashSet<>();
        for (Map.Entry<String, Integer> e : nameToId.entrySet()) {
            if (!accepted(declared, e.getValue(), false)) refused.add(e.getKey());
        }
        return refused;
    }

    /**
     * Little-endian reads of a stream
     */
    private static final class Reader {
        private final InputStream in;

        Reader(InputStream in) {
            this.in = in;
        }

        int u8() throws IOException {
            int b = in.read();
            if (b < 0) throw new EOFException();
            return b;
        }

        int u16() throws IOException {
            return u8() | (u8() << 8);
        }

        long u32() throws IOException {
            return (u16() & 0xffffL) | ((long) u16() << 16);
        }

        void skip(long n) throws IOException {
            while (n > 0) {
                long skipped = in.skip(n);
                if (skipped <= 0) {
                    if (in.read() < 0) throw new EOFException();
                    skipped = 1;
                }
                n -= skipped;
            }
        }
    }
}
