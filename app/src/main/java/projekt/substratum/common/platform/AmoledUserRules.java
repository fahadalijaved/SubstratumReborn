/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.common.platform;

import android.content.Context;
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
import java.util.Collections;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * The user's own exceptions to "AMOLED for every app", per app and resource name, kept in
 * {@code files/amoled/user_rules.json}:
 * <pre>
 * {"keep": {"com.android.settings": ["sec_widget_color"]},
 *  "pin":  {"com.android.systemui": {"qs_tile_round_background_on": "#FF009587"}}}
 * </pre>
 * A kept color stays stock (nothing is written for it); a pinned color gets exactly that value in
 * dark mode. They come before the theme's rules and the accent (see
 * {@link AmoledRules#explicitDecision}), and take effect when the app's colors are read again.
 */
public final class AmoledUserRules {

    static final String FILE = "user_rules.json";
    private static final String TAG = "SubstratumAmoled";
    private static final Object LOCK = new Object();

    private final Map<String, Set<String>> keep = new TreeMap<>();
    private final Map<String, Map<String, Integer>> pin = new TreeMap<>();

    /**
     * Keep a color stock (replaces a pin of it).
     */
    public void keep(String pkg, String name) {
        unpin(pkg, name);
        keep.computeIfAbsent(pkg, k -> new TreeSet<>()).add(name);
    }

    /**
     * Give a color this value in dark mode (replaces a keep of it).
     */
    public void pin(String pkg, String name, int color) {
        unkeep(pkg, name);
        pin.computeIfAbsent(pkg, k -> new TreeMap<>()).put(name, color);
    }

    /**
     * Back to the rules.
     *
     * @return Whether there was a rule for it
     */
    public boolean clear(String pkg, String name) {
        boolean kept = unkeep(pkg, name);
        return unpin(pkg, name) || kept;
    }

    public boolean isKept(String pkg, String name) {
        Set<String> names = keep.get(pkg);
        return names != null && names.contains(name);
    }

    /**
     * The pinned color, or null
     */
    public Integer pinned(String pkg, String name) {
        Map<String, Integer> names = pin.get(pkg);
        return names == null ? null : names.get(name);
    }

    public boolean isEmpty() {
        return keep.isEmpty() && pin.isEmpty();
    }

    /**
     * Apps with a rule
     */
    public Set<String> packages() {
        Set<String> packages = new TreeSet<>(keep.keySet());
        packages.addAll(pin.keySet());
        return Collections.unmodifiableSet(packages);
    }

    /**
     * The user's decision for a resource: {@link AmoledRules#KEEP}, a pinned color, or null.
     */
    AmoledPalette.Decision decision(String pkg, String name) {
        if (isKept(pkg, name)) return AmoledRules.KEEP;
        Integer color = pinned(pkg, name);
        return color == null ? null : new AmoledPalette.Decision(color, AmoledPalette.USER_PIN);
    }

    private boolean unkeep(String pkg, String name) {
        Set<String> names = keep.get(pkg);
        if (names == null || !names.remove(name)) return false;
        if (names.isEmpty()) keep.remove(pkg);
        return true;
    }

    private boolean unpin(String pkg, String name) {
        Map<String, Integer> names = pin.get(pkg);
        if (names == null || names.remove(name) == null) return false;
        if (names.isEmpty()) pin.remove(pkg);
        return true;
    }

    // JSON

    public String toJson() {
        try {
            JSONObject o = new JSONObject();
            JSONObject kept = new JSONObject();
            for (Map.Entry<String, Set<String>> entry : keep.entrySet()) {
                kept.put(entry.getKey(), new JSONArray(entry.getValue()));
            }
            JSONObject pinned = new JSONObject();
            for (Map.Entry<String, Map<String, Integer>> entry : pin.entrySet()) {
                JSONObject names = new JSONObject();
                for (Map.Entry<String, Integer> color : entry.getValue().entrySet()) {
                    names.put(color.getKey(), hex(color.getValue()));
                }
                pinned.put(entry.getKey(), names);
            }
            o.put("keep", kept);
            o.put("pin", pinned);
            return o.toString(1);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * The rules in a JSON text; entries that do not parse are left out.
     */
    public static AmoledUserRules fromJson(String json) throws JSONException {
        AmoledUserRules rules = new AmoledUserRules();
        JSONObject o = new JSONObject(json);
        JSONObject kept = o.optJSONObject("keep");
        if (kept != null) {
            for (Iterator<String> it = kept.keys(); it.hasNext(); ) {
                String pkg = it.next();
                JSONArray names = kept.optJSONArray(pkg);
                if (names == null) continue;
                for (int i = 0; i < names.length(); i++) {
                    String name = names.optString(i, null);
                    if (name != null && !name.isEmpty()) rules.keep(pkg, name);
                }
            }
        }
        JSONObject pinned = o.optJSONObject("pin");
        if (pinned != null) {
            for (Iterator<String> it = pinned.keys(); it.hasNext(); ) {
                String pkg = it.next();
                JSONObject names = pinned.optJSONObject(pkg);
                if (names == null) continue;
                for (Iterator<String> n = names.keys(); n.hasNext(); ) {
                    String name = n.next();
                    try {
                        rules.pin(pkg, name, AmoledPalette.parseColor(names.getString(name)));
                    } catch (IllegalArgumentException | JSONException e) {
                        // not a color
                    }
                }
            }
        }
        return rules;
    }

    static String hex(int color) {
        return String.format(Locale.ROOT, "#%08X", color);
    }

    // Reading and writing

    public static AmoledUserRules read(Context context) {
        synchronized (LOCK) {
            return load(context);
        }
    }

    /**
     * Read, change and write the rules as one step.
     *
     * @return The rules as written
     */
    public static AmoledUserRules update(Context context, Consumer<AmoledUserRules> change) {
        synchronized (LOCK) {
            AmoledUserRules rules = load(context);
            change.accept(rules);
            rules.write(context);
            return rules;
        }
    }

    static File file(Context context) {
        return new File(new File(context.getFilesDir(), AmoledState.FOLDER), FILE);
    }

    /**
     * When the rules were last written (0 when there are none): a cache of previews made with
     * them is stale once this changes.
     */
    public static long lastWritten(Context context) {
        return file(context).lastModified();
    }

    private static AmoledUserRules load(Context context) {
        File file = file(context);
        if (!file.exists()) return new AmoledUserRules();
        try {
            return fromJson(new String(Files.readAllBytes(file.toPath()),
                    StandardCharsets.UTF_8));
        } catch (IOException | JSONException | RuntimeException e) {
            Log.w(TAG, "Unreadable " + file + ", no user rules: " + e);
            return new AmoledUserRules();
        }
    }

    private boolean write(Context context) {
        File file = file(context);
        File dir = file.getParentFile();
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File temp = new File(dir, FILE + ".tmp");
        try {
            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write(toJson().getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            }
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException | RuntimeException e) {
            Log.e(TAG, "Could not write " + file, e);
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            return false;
        }
    }
}
