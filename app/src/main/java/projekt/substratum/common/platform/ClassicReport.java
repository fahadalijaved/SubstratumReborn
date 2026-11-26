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
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What the engine did to one classic overlay on this phone, kept for the apply summary and the
 * Theme Doctor ({@code files/classic/report/<overlay>.json}):
 * <ul>
 * <li>{@link #pruned}: lines of the theme the compiler dropped because this system no longer has
 * what they reference ({@code file: name (missing reference)});</li>
 * <li>{@link #darkDropped}: values made for light mode only, left out in dark mode only;</li>
 * <li>{@link #toModule}: values a shell overlay may not change here, or this shell could not
 * carry (bools and integers), which the LSPosed module applies instead.</li>
 * </ul>
 */
public final class ClassicReport {

    private static final String TAG = "SubstratumClassic";

    public final List<String> pruned = new ArrayList<>();
    public final List<String> darkDropped = new ArrayList<>();
    public final Set<String> toModule = new LinkedHashSet<>();
    public String target;
    public String theme;
    public boolean darkOnly;
    public long time;

    private static File file(Context context, String overlay) {
        File dir = new File(context.getFilesDir(), "classic/report");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, overlay + ".json");
    }

    public void save(Context context, String overlay) {
        if (context == null || overlay == null) return;
        try (FileWriter writer = new FileWriter(file(context, overlay))) {
            writer.write(toJson().toString());
        } catch (IOException | JSONException e) {
            Log.w(TAG, "could not keep the report of " + overlay, e);
        }
    }

    /**
     * The report of an overlay, or null when there is none
     */
    public static ClassicReport load(Context context, String overlay) {
        File file = file(context, overlay);
        if (!file.isFile()) return null;
        try {
            return fromJson(new JSONObject(new String(Files.readAllBytes(file.toPath()),
                    StandardCharsets.UTF_8)));
        } catch (IOException | JSONException e) {
            return null;
        }
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("target", target);
        o.put("theme", theme);
        o.put("darkOnly", darkOnly);
        o.put("time", time);
        o.put("pruned", new JSONArray(pruned));
        o.put("darkDropped", new JSONArray(darkDropped));
        o.put("toModule", new JSONArray(toModule));
        return o;
    }

    static ClassicReport fromJson(JSONObject o) throws JSONException {
        ClassicReport r = new ClassicReport();
        r.target = o.optString("target", null);
        r.theme = o.optString("theme", null);
        r.darkOnly = o.optBoolean("darkOnly");
        r.time = o.optLong("time");
        addAll(o.optJSONArray("pruned"), r.pruned);
        addAll(o.optJSONArray("darkDropped"), r.darkDropped);
        addAll(o.optJSONArray("toModule"), r.toModule);
        return r;
    }

    private static void addAll(JSONArray array, Collection<String> into) throws JSONException {
        if (array == null) return;
        for (int i = 0; i < array.length(); i++) into.add(array.getString(i));
    }
}
