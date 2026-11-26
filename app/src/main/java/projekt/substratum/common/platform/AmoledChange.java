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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * One color "AMOLED for every app" changes in an app: what it was in light and dark mode, what it
 * becomes, and which rule decided. compute() returns these; {@link #toItems} turns them into the
 * fabricated overlay items (and the module's lines) as before.
 * <p>
 * The light entry follows the Monet-safe rules: it is written only when the light value is fixed
 * (a literal color, or references that end in one; a theme attribute or a wallpaper color keeps
 * following its source), never for a color state list (it keeps its states in light mode), and it
 * carries the stock light value unless the accent applies in light mode too.
 * <p>
 * After each app, the changes are kept in {@code files/amoled/changes/<pkg>.json} for the
 * inspector and the preview.
 */
public final class AmoledChange {

    public static final String COLOR = "color";
    public static final String CSL = "csl";
    static final String CHANGES = "changes";
    private static final String TAG = "SubstratumAmoled";

    public final String name;
    public final int id;
    /**
     * {@link #COLOR} or {@link #CSL} (a color state list a rule names a color for)
     */
    public final String type;
    /**
     * The light-mode value, or null (a state list, a reference that did not resolve)
     */
    public final Integer stockLight;
    /**
     * The dark-mode value, or null for a color state list
     */
    public final Integer stockDark;
    public final int amoled;
    public final String rule;
    /**
     * The light (default configuration) entry written, or null for none
     */
    public final Integer lightEntry;

    public AmoledChange(String name, int id, String type, Integer stockLight, Integer stockDark,
                        int amoled, String rule, Integer lightEntry) {
        this.name = name;
        this.id = id;
        this.type = type;
        this.stockLight = stockLight;
        this.stockDark = stockDark;
        this.amoled = amoled;
        this.rule = rule;
        this.lightEntry = lightEntry;
    }

    // Deciding

    /**
     * The dark-mode decision for a resource, or null when it keeps its stock value (the generic
     * rules keep it, the user keeps it, or the value would not change). A color state list
     * ({@code stockDark} null) only changes when a rule names a color for it.
     */
    public static AmoledPalette.Decision night(String pkg, String name, Integer stockDark,
                                               AmoledRules rules, int primary50) {
        if (!AmoledRules.touches(pkg, name)) return null;
        AmoledPalette.Decision decision = stockDark == null ?
                rules.explicitDecision(pkg, name) :
                AmoledPalette.forColor(pkg, name, stockDark, rules, primary50);
        if (decision == null || decision.color == null) return null;
        if (stockDark != null && decision.color.intValue() == stockDark) return null;
        return decision;
    }

    /**
     * The change for a resource whose dark-mode decision is made.
     *
     * @param dayLiteral The light-mode value when it is fixed (a literal color, or references
     *                   that end in one), else null (it follows a theme attribute or the
     *                   wallpaper, a state list, no light value)
     * @param stockLight The light-mode value with references followed, or null
     */
    public static AmoledChange of(String pkg, String name, int id, Integer stockDark,
                                  AmoledPalette.Decision night, Integer dayLiteral,
                                  Integer stockLight, AmoledRules rules, int primary50) {
        Integer light = null;
        if (stockDark != null && dayLiteral != null) {
            light = dayLiteral;
            if (rules.accentLight && AmoledPalette.ACCENT.equals(night.rule)) {
                Integer accent = AmoledPalette.accentFor(pkg, name, dayLiteral, rules, primary50);
                if (accent != null) light = accent;
            }
        }
        return new AmoledChange(name, id, stockDark == null ? CSL : COLOR, stockLight, stockDark,
                night.color, night.rule, light);
    }

    // Overlay items

    /**
     * Fabricated items: {target, type, value, "", id} for the light entry, if any, then
     * {target, type, value, "night", id, stock dark value or "" for a state list, rule}.
     */
    public List<String[]> toItems() {
        List<String[]> items = new ArrayList<>(2);
        String target = "color/" + name;
        String hexId = String.format("0x%08x", id);
        if (lightEntry != null) {
            items.add(new String[]{target, "28", String.format("0x%08x", lightEntry), "", hexId});
        }
        items.add(new String[]{target, "28", String.format("0x%08x", amoled), "night", hexId,
                stockDark != null ? String.format("0x%08x", stockDark) : "", rule});
        return items;
    }

    public static List<String[]> toItems(Collection<AmoledChange> changes) {
        List<String[]> items = new ArrayList<>(changes.size() * 2);
        for (AmoledChange change : changes) items.addAll(change.toItems());
        return items;
    }

    // JSON

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("name", name);
        o.put("id", String.format("0x%08x", id));
        o.put("type", type);
        if (stockLight != null) o.put("stockLight", AmoledUserRules.hex(stockLight));
        if (stockDark != null) o.put("stockDark", AmoledUserRules.hex(stockDark));
        o.put("amoled", AmoledUserRules.hex(amoled));
        o.put("rule", rule);
        if (lightEntry != null) o.put("lightEntry", AmoledUserRules.hex(lightEntry));
        return o;
    }

    public static AmoledChange fromJson(JSONObject o) throws JSONException {
        return new AmoledChange(o.getString("name"),
                (int) Long.parseLong(o.getString("id").substring(2), 16),
                o.optString("type", COLOR), color(o, "stockLight"), color(o, "stockDark"),
                AmoledPalette.parseColor(o.getString("amoled")), o.optString("rule", ""),
                color(o, "lightEntry"));
    }

    private static Integer color(JSONObject o, String key) {
        if (!o.has(key) || o.isNull(key)) return null;
        try {
            return AmoledPalette.parseColor(o.optString(key));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * What was applied to one app: the look and the changes.
     */
    public static final class Record {
        public final String packageName;
        public final String level;
        /**
         * The accent applied, or null
         */
        public final Integer accent;
        public final int primary50;
        public final String route;
        public final String versionKey;
        public final long time;
        public final List<AmoledChange> changes;

        public Record(String packageName, String level, Integer accent, int primary50,
                      String route, String versionKey, long time, List<AmoledChange> changes) {
            this.packageName = packageName;
            this.level = level;
            this.accent = accent;
            this.primary50 = primary50;
            this.route = route;
            this.versionKey = versionKey;
            this.time = time;
            this.changes = Collections.unmodifiableList(new ArrayList<>(changes));
        }

        /**
         * The change of a resource, or null
         */
        public AmoledChange change(String name) {
            for (AmoledChange change : changes) if (change.name.equals(name)) return change;
            return null;
        }

        public String toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("version", 1);
            o.put("package", packageName);
            o.put("level", level);
            if (accent != null) o.put("accent", AmoledUserRules.hex(accent));
            o.put("primary50", AmoledUserRules.hex(primary50));
            if (route != null) o.put("route", route);
            if (versionKey != null) o.put("versionKey", versionKey);
            o.put("time", time);
            JSONArray list = new JSONArray();
            for (AmoledChange change : changes) list.put(change.toJson());
            o.put(CHANGES, list);
            return o.toString(1);
        }

        public static Record fromJson(String json) throws JSONException {
            JSONObject o = new JSONObject(json);
            List<AmoledChange> changes = new ArrayList<>();
            JSONArray list = o.optJSONArray(CHANGES);
            if (list != null) {
                for (int i = 0; i < list.length(); i++) {
                    JSONObject item = list.optJSONObject(i);
                    if (item == null) continue;
                    try {
                        changes.add(AmoledChange.fromJson(item));
                    } catch (JSONException | RuntimeException e) {
                        // a malformed entry
                    }
                }
            }
            Integer primary = color(o, "primary50");
            return new Record(o.optString("package"), o.optString("level", "pure"),
                    color(o, "accent"), primary != null ? primary : AmoledPalette.ONEUI_BLUE,
                    o.has("route") ? o.optString("route") : null,
                    o.has("versionKey") ? o.optString("versionKey") : null,
                    o.optLong("time", 0), changes);
        }
    }

    // Files

    public static File dir(Context context) {
        return new File(new File(context.getFilesDir(), AmoledState.FOLDER), CHANGES);
    }

    public static File file(Context context, String pkg) {
        return new File(dir(context), pkg + ".json");
    }

    /**
     * Keep what was applied to an app (an atomic write).
     *
     * @return The file, or null if it could not be written
     */
    public static File save(Context context, Record record) {
        File file = file(context, record.packageName);
        File dir = file.getParentFile();
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File temp = new File(dir, file.getName() + ".tmp");
        try {
            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write(record.toJson().getBytes(StandardCharsets.UTF_8));
            }
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            return file;
        } catch (IOException | JSONException | RuntimeException e) {
            Log.w(TAG, "Could not keep the changes of " + record.packageName + ": " + e);
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            return null;
        }
    }

    /**
     * What was applied to an app, or null.
     */
    public static Record read(Context context, String pkg) {
        File file = file(context, pkg);
        if (!file.isFile()) return null;
        try {
            return Record.fromJson(new String(Files.readAllBytes(file.toPath()),
                    StandardCharsets.UTF_8));
        } catch (IOException | JSONException | RuntimeException e) {
            Log.w(TAG, "Unreadable " + file + ": " + e);
            return null;
        }
    }

    public static void delete(Context context, String pkg) {
        //noinspection ResultOfMethodCallIgnored
        file(context, pkg).delete();
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%s %s -> #%08x %s", name,
                stockDark == null ? "csl" : String.format(Locale.ROOT, "#%08x", stockDark),
                amoled, rule);
    }
}
