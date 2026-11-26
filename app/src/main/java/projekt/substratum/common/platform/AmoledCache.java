/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.common.platform;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What the engine computed for each app, kept in {@code files/amoled/cache/<pkg>.json}, so a
 * re-apply of an app that did not change skips reading its colors, and skips registering them when
 * its overlay (or module list) is already in place with the same contents.
 * <p>
 * An entry is valid for one {@link #key}: the app version (version code, last update time, APK
 * path), the system build, {@link #ENGINE_VERSION} and a hash of everything in the rules that
 * decides colors (rules, look, accent, the user's own rules: {@link AmoledRules#fingerprint}).
 * The app's color resource ids are kept under the version part alone ({@link #idsKey}): a new look
 * reads the same ids again without walking the resource table.
 */
final class AmoledCache {

    /**
     * Raised whenever compute() or the overlay contents change in a way the key cannot see
     */
    static final int ENGINE_VERSION = 1;
    static final String FOLDER = "cache";
    private static final String TAG = "RebornAmoled";

    static final class Entry {
        String key;
        String idsKey;
        /**
         * The app's color resource ids, or null
         */
        List<Integer> ids;
        int primary50 = AmoledPalette.ONEUI_BLUE;
        List<AmoledChange> changes;
        /**
         * Targets ({@code color/<name>}) the overlay leaves out: not overlayable for a shell
         * overlay
         */
        Set<String> rejected = new LinkedHashSet<>();
        /**
         * {@link AmoledState.Route} name of what was committed
         */
        String route;
        /**
         * Hash of what was committed (route and the definition or module list), or null when
         * nothing is known to be in place
         */
        String hash;
        int count;
    }

    private AmoledCache() {
    }

    // Keys

    /**
     * The rules part of the key.
     */
    static String rulesHash(AmoledRules rules) {
        return sha1(ENGINE_VERSION + "\n" + rules.fingerprint());
    }

    /**
     * The installed version of an app, as far as its colors go.
     */
    static String idsKey(PackageInfo info) {
        long version = androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info);
        String source = info.applicationInfo != null ? info.applicationInfo.sourceDir : "";
        return version + "|" + info.lastUpdateTime + "|" + source + "|" + Build.FINGERPRINT +
                "|" + ENGINE_VERSION;
    }

    static String key(String idsKey, String rulesHash) {
        return idsKey + "|" + rulesHash;
    }

    static String sha1(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format("%02x", b & 0xff));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(text.hashCode()) + ':' + text.length();
        }
    }

    // Files

    static File dir(Context context) {
        return new File(new File(context.getFilesDir(), AmoledState.FOLDER), FOLDER);
    }

    private static File file(Context context, String pkg) {
        return new File(dir(context), pkg + ".json");
    }

    /**
     * The entry of an app, or null.
     */
    static Entry read(Context context, String pkg) {
        File file = file(context, pkg);
        if (!file.isFile()) return null;
        try {
            return fromJson(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        } catch (IOException | JSONException | RuntimeException e) {
            Log.w(TAG, "Unreadable cache of " + pkg + ": " + e);
            //noinspection ResultOfMethodCallIgnored
            file.delete();
            return null;
        }
    }

    static void write(Context context, String pkg, Entry entry) {
        File file = file(context, pkg);
        File dir = file.getParentFile();
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File temp = new File(dir, file.getName() + ".tmp");
        try {
            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write(toJson(entry).getBytes(StandardCharsets.UTF_8));
            }
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | JSONException | RuntimeException e) {
            Log.w(TAG, "Could not cache " + pkg + ": " + e);
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
        }
    }

    static void delete(Context context, String pkg) {
        //noinspection ResultOfMethodCallIgnored
        file(context, pkg).delete();
    }

    /**
     * Forget every entry.
     */
    static void clear(Context context) {
        File[] files = dir(context).listFiles();
        if (files == null) return;
        //noinspection ResultOfMethodCallIgnored
        for (File file : files) file.delete();
    }

    // JSON

    static String toJson(Entry e) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("version", ENGINE_VERSION);
        o.put("key", e.key);
        if (e.idsKey != null) o.put("idsKey", e.idsKey);
        if (e.ids != null) {
            JSONArray ids = new JSONArray();
            for (int id : e.ids) ids.put(id);
            o.put("ids", ids);
        }
        o.put("primary50", AmoledUserRules.hex(e.primary50));
        JSONArray changes = new JSONArray();
        if (e.changes != null) for (AmoledChange change : e.changes) changes.put(change.toJson());
        o.put("changes", changes);
        o.put("rejected", new JSONArray(e.rejected));
        if (e.route != null) o.put("route", e.route);
        if (e.hash != null) o.put("hash", e.hash);
        o.put("count", e.count);
        return o.toString();
    }

    static Entry fromJson(String json) throws JSONException {
        JSONObject o = new JSONObject(json);
        if (o.optInt("version", 0) != ENGINE_VERSION) throw new JSONException("old engine");
        Entry e = new Entry();
        e.key = o.getString("key");
        e.idsKey = o.has("idsKey") ? o.getString("idsKey") : null;
        JSONArray ids = o.optJSONArray("ids");
        if (ids != null) {
            e.ids = new ArrayList<>(ids.length());
            for (int i = 0; i < ids.length(); i++) e.ids.add(ids.getInt(i));
        }
        e.primary50 = AmoledPalette.parseColor(o.optString("primary50", "#ff387aff"));
        JSONArray changes = o.getJSONArray("changes");
        e.changes = new ArrayList<>(changes.length());
        for (int i = 0; i < changes.length(); i++) {
            e.changes.add(AmoledChange.fromJson(changes.getJSONObject(i)));
        }
        JSONArray rejected = o.optJSONArray("rejected");
        if (rejected != null) {
            for (int i = 0; i < rejected.length(); i++) e.rejected.add(rejected.getString(i));
        }
        e.route = o.has("route") ? o.getString("route") : null;
        e.hash = o.has("hash") ? o.getString("hash") : null;
        e.count = o.optInt("count", 0);
        return e;
    }
}
