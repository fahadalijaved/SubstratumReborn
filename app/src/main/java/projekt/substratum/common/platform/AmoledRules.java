/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.common.platform;

import android.content.Context;
import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The colors and choices behind "AMOLED for every app". A theme provides its own in
 * {@code assets/amoled/rules.json}; anything it leaves out keeps the default below.
 * <pre>
 * {
 *   "background": "#000000",      every dark surface: pages, cards, lists, buttons
 *   "floating":   "#000000",      popups, menus, dialogs, floating bars
 *   "outline":    "#1f1f1f",      outline of floating elements
 *   "blur":       "#fa000000",    tint over blurred panels (alpha = how much blur shows)
 *   "slider":     "#1a1a1a",      slider and seekbar tracks
 *   "thumb":      "#2a2a2a",      scrollbar thumbs
 *   "include":    ["^com\\.samsung\\..*"],   regexes of packages; default: Samsung, Google, system
 *   "exclude":    ["^com\\.google\\.android\\.gms$"],
 *   "colors": { "com.android.systemui": { "qs_tile_round_background_on": "#387aff",
 *                                         "^sec_dashboard_.*_color$": "#262626" } }
 * }
 * </pre>
 * Keys in "colors" are exact resource names, or regexes when they start with ^.
 */
public final class AmoledRules {

    public static final String ASSET = "amoled/rules.json";

    int background = 0xff000000;
    int floating = 0xff000000;
    int outline = 0xff1f1f1f;
    int blur = 0xfa000000;
    int slider = 0xff1a1a1a;
    int thumb = 0xff2a2a2a;
    final List<Pattern> include = new ArrayList<>();
    final List<Pattern> exclude = new ArrayList<>();
    final Map<String, Map<String, Integer>> exact = new HashMap<>();
    final Map<String, List<Object[]>> patterns = new HashMap<>(); // {Pattern, Integer}

    /**
     * Built-in rules, the same as Reborn AMOLED's rules.json.
     */
    public static AmoledRules defaults() {
        AmoledRules r = new AmoledRules();
        r.include.add(Pattern.compile(
                "^(android|com\\.(samsung|sec|google|android)\\..*|com\\.wssyncmldm|com\\.wssnps)$"));
        Map<String, Integer> sysui = new HashMap<>();
        sysui.put("panel_bg_color", 0xfa000000);
        sysui.put("panel_background_blur_color", 0xf2000000);
        sysui.put("basic_token_effect_blur_surface", 0xf2000000);
        sysui.put("basic_token_effect_blur_surface_dark", 0xf2000000);
        sysui.put("basic_token_effect_blur_chip", 0xf2000000);
        sysui.put("basic_token_effect_blur_chip_dark", 0xf2000000);
        sysui.put("qs_tile_layout_container_bg", 0xff000000);
        sysui.put("qs_tile_container_bg", 0xff000000);
        sysui.put("qs_tile_round_background_off", 0xff000000);
        sysui.put("qs_tile_round_background_dim", 0xff000000);
        sysui.put("seekbar_background_color", 0xff1f1f1f);
        sysui.put("seekbar_background_color_expand", 0xff1f1f1f);
        sysui.put("seekbar_color_expand", 0xfffcfcfc);
        sysui.put("qs_tile_round_background_on", 0xff387aff);
        sysui.put("qs_switch_off_background_color", 0xff242424);
        sysui.put("qs_detail_layout_popup_menu_background_color", 0xfa000000);
        sysui.put("qs_detail_layout_popup_menu_background_color_no_blur", 0xff000000);
        sysui.put("sec_qs_detail_bg_color", 0xfa000000);
        sysui.put("notification_material_background_color", 0xff0a0a0a);
        sysui.put("notification_material_background_dimmed_color", 0xff0a0a0a);
        sysui.put("notification_material_background_on_lockscreen", 0xf2000000);
        sysui.put("sec_brightness_mirror_background_color", 0xff000000);
        sysui.put("qs_tile_container_bg_border_color", 0xff262626);
        sysui.put("qs_tile_icon_off_tint_color", 0xfffcfcff);
        sysui.put("sec_qs_switch_off_background_color", 0xff242424);
        sysui.put("sec_qs_media_player_background_color", 0xff000000);
        r.exact.put("com.android.systemui", sysui);
        List<Object[]> settings = new ArrayList<>();
        settings.add(new Object[]{Pattern.compile("^sec_dashboard_(?!.*(text|layer)).*_color\\d*$"),
                0xff262626});
        r.patterns.put("com.android.settings", settings);
        return r;
    }

    /**
     * The rules of a theme package, layered over the defaults.
     */
    public static AmoledRules fromTheme(Context context, String themePackage) throws Exception {
        Context theme = context.createPackageContext(themePackage, 0);
        try (InputStream in = theme.getAssets().open(ASSET)) {
            return fromJson(new String(readAll(in), StandardCharsets.UTF_8));
        }
    }

    public static boolean isRulesTheme(Context context, String themePackage) {
        try (InputStream ignored = context.createPackageContext(themePackage, 0)
                .getAssets().open(ASSET)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static AmoledRules fromJson(String json) throws Exception {
        AmoledRules r = defaults();
        JSONObject o = new JSONObject(json);
        if (o.has("background")) r.background = Color.parseColor(o.getString("background"));
        if (o.has("floating")) r.floating = Color.parseColor(o.getString("floating"));
        if (o.has("outline")) r.outline = Color.parseColor(o.getString("outline"));
        if (o.has("blur")) r.blur = Color.parseColor(o.getString("blur"));
        if (o.has("slider")) r.slider = Color.parseColor(o.getString("slider"));
        if (o.has("thumb")) r.thumb = Color.parseColor(o.getString("thumb"));
        if (o.has("include")) {
            r.include.clear();
            JSONArray a = o.getJSONArray("include");
            for (int i = 0; i < a.length(); i++) r.include.add(Pattern.compile(a.getString(i)));
        }
        if (o.has("exclude")) {
            JSONArray a = o.getJSONArray("exclude");
            for (int i = 0; i < a.length(); i++) r.exclude.add(Pattern.compile(a.getString(i)));
        }
        if (o.has("colors")) {
            JSONObject colors = o.getJSONObject("colors");
            for (Iterator<String> pkgs = colors.keys(); pkgs.hasNext(); ) {
                String pkg = pkgs.next();
                JSONObject entries = colors.getJSONObject(pkg);
                for (Iterator<String> names = entries.keys(); names.hasNext(); ) {
                    String name = names.next();
                    int color = Color.parseColor(entries.getString(name));
                    if (name.startsWith("^")) {
                        r.patterns.computeIfAbsent(pkg, k -> new ArrayList<>())
                                .add(0, new Object[]{Pattern.compile(name), color});
                    } else {
                        r.exact.computeIfAbsent(pkg, k -> new HashMap<>()).put(name, color);
                    }
                }
            }
        }
        return r;
    }

    boolean covers(String packageName) {
        for (Pattern p : exclude) if (p.matcher(packageName).matches()) return false;
        for (Pattern p : include) if (p.matcher(packageName).matches()) return true;
        return false;
    }

    /**
     * Hand-picked color for a resource, or null to let the rules decide.
     */
    Integer explicit(String packageName, String name) {
        Map<String, Integer> e = exact.get(packageName);
        if (e != null && e.containsKey(name)) return e.get(name);
        List<Object[]> p = patterns.get(packageName);
        if (p != null) {
            for (Object[] entry : p) {
                if (((Pattern) entry[0]).matcher(name).find()) return (Integer) entry[1];
            }
        }
        return null;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        return out.toByteArray();
    }
}
