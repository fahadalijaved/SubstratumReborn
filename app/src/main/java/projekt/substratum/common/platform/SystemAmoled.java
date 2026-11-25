/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.common.platform;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.Log;
import android.util.TypedValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * "AMOLED for every app": turns the dark mode of every Samsung, Google and system app on the
 * device into true black, without a theme APK.
 * <p>
 * For each app it loads the app's own resources in dark mode, resolves what every color really
 * shows (following One UI's reference chains), applies the AMOLED rules and registers the result
 * as a night-only fabricated overlay (see {@link FabricatedValues}). Light mode is never touched,
 * and the overlay manager applies it live.
 */
public final class SystemAmoled {

    public static final String SUFFIX = ".RebornSystemAmoled";
    private static final String TAG = "SubstratumAmoled";
    private static final Pattern VENDORS = Pattern.compile(
            "^(android|com\\.(samsung|sec|google|android)\\..*|com\\.wssyncmldm|com\\.wssnps)$");

    private static final Pattern STATE = Pattern.compile(
            "ripple|press|focus|select|hover|drag|highlight|state|checked|activated");
    private static final Pattern TEXT = Pattern.compile(
            "text|title|label|hint|icon|summary|glyph|tint|foreground|_fg");
    private static final Pattern BG_STRICT = Pattern.compile(
            "bg|background|surface|container|fill|scrim|blur");
    private static final Pattern BG = Pattern.compile(
            "bg|background|surface|container|fill|panel|scrim|blur|window|keyboard|card|link");
    private static final Pattern LINE = Pattern.compile(
            "divider|outline|stroke|separator|_line|border|frame");
    private static final Pattern FLOAT = Pattern.compile(
            "float|popup|menu|dialog|search_?(bar|field|textfield|view)|navi|bottom_bar|toolbar|" +
                    "chip|fab|handle|toast|snackbar");
    private static final Pattern AREA = Pattern.compile("list|area|layout|screen|page|window");
    private static final Pattern SLIDER = Pattern.compile("slider|seekbar|seek_bar|progress");

    /**
     * Hand-tuned values on top of the rules (dark mode only).
     */
    private static final Map<String, Map<String, Integer>> EXPLICIT = new HashMap<>();

    static {
        Map<String, Integer> sysui = new HashMap<>();
        // Notification shade and QS: an AMOLED blur, black tile groups and tiles
        sysui.put("panel_bg_color", 0xfa000000);
        sysui.put("panel_background_blur_color", 0xf2000000);
        sysui.put("basic_token_effect_blur_surface", 0xf2000000);
        sysui.put("basic_token_effect_blur_surface_dark", 0xf2000000);
        sysui.put("basic_token_effect_blur_chip", 0xf2000000);
        sysui.put("basic_token_effect_blur_chip_dark", 0xf2000000);
        sysui.put("qs_tile_layout_container_bg", 0xff0d0d0d);
        sysui.put("qs_tile_container_bg", 0xff0d0d0d);
        sysui.put("qs_tile_round_background_off", 0xff1a1a1a);
        sysui.put("qs_tile_round_background_dim", 0xff121212);
        sysui.put("qs_tile_round_background_on", 0xff387aff);
        sysui.put("qs_switch_off_background_color", 0xff1a1a1a);
        sysui.put("qs_detail_layout_popup_menu_background_color", 0xf20a0a0a);
        sysui.put("qs_detail_layout_popup_menu_background_color_no_blur", 0xff0a0a0a);
        sysui.put("sec_qs_detail_bg_color", 0xf2000000);
        sysui.put("notification_material_background_color", 0xff0a0a0a);
        sysui.put("notification_material_background_dimmed_color", 0xff0a0a0a);
        sysui.put("notification_material_background_on_lockscreen", 0xf2000000);
        sysui.put("sec_brightness_mirror_background_color", 0xff000000);
        EXPLICIT.put("com.android.systemui", sysui);
    }

    private SystemAmoled() {
    }

    public interface Progress {
        void onPackage(String packageName, int done, int total);
    }

    /**
     * Apply to every Samsung, Google and system app. Runs for a while: call off the main thread.
     *
     * @return {apps themed, colors changed}
     */
    public static int[] applyAll(Context context, Progress progress) {
        List<String> targets = targets(context);
        int apps = 0, colors = 0;
        for (int i = 0; i < targets.size(); i++) {
            String pkg = targets.get(i);
            if (progress != null) progress.onPackage(pkg, i, targets.size());
            try {
                List<String[]> items = compute(context, pkg);
                if (items.isEmpty()) continue;
                if (FabricatedValues.registerItems(context, pkg, pkg + SUFFIX, items) > 0) {
                    List<String> name = new ArrayList<>();
                    name.add(pkg + SUFFIX);
                    FabricatedValues.enable(name);
                    apps++;
                    colors += items.size();
                }
            } catch (Throwable t) {
                Log.w(TAG, "Skipping " + pkg + ": " + t);
            }
        }
        return new int[]{apps, colors};
    }

    public static void removeAll(Context context) {
        List<String> names = new ArrayList<>();
        for (String pkg : targets(context)) names.add(pkg + SUFFIX);
        FabricatedValues.disable(names);
    }

    private static List<String> targets(Context context) {
        List<String> list = new ArrayList<>();
        for (PackageInfo info : context.getPackageManager().getInstalledPackages(0)) {
            String pkg = info.packageName;
            if (!VENDORS.matcher(pkg).matches()) continue;
            if (isOverlay(info)) continue; // overlays themselves
            list.add(pkg);
        }
        return list;
    }

    private static boolean isOverlay(PackageInfo info) {
        try {
            return PackageInfo.class.getField("overlayTarget").get(info) != null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return info.packageName.contains(".overlay") ||
                    info.packageName.contains("auto_generated");
        }
    }

    /**
     * @return fabricated items {target, type, value, config} for the app's dark mode
     */
    static List<String[]> compute(Context context, String pkg)
            throws PackageManager.NameNotFoundException {
        Context app = context.createPackageContext(pkg, Context.CONTEXT_IGNORE_SECURITY);
        Configuration night = new Configuration(app.getResources().getConfiguration());
        night.uiMode = (night.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) |
                Configuration.UI_MODE_NIGHT_YES;
        Resources res = app.createConfigurationContext(night).getResources();
        Map<String, Integer> explicit = EXPLICIT.get(pkg);

        List<String[]> items = new ArrayList<>();
        TypedValue tv = new TypedValue();
        for (int id : colorIds(res, pkg)) {
            String name;
            try {
                name = res.getResourceEntryName(id);
                res.getValue(id, tv, true);
            } catch (Resources.NotFoundException e) {
                continue;
            }
            // Color state lists keep their states: only plain colors are replaced
            if (tv.type < TypedValue.TYPE_FIRST_COLOR_INT || tv.type > TypedValue.TYPE_LAST_COLOR_INT)
                continue;
            Integer mapped = explicit != null && explicit.containsKey(name) ?
                    explicit.get(name) : amoled(tv.data, name);
            if (mapped == null || mapped == tv.data) continue;
            items.add(new String[]{"color/" + name, "28",
                    String.format("0x%08x", mapped), "night"});
        }
        return items;
    }

    /**
     * All color resource ids of a package, found by walking its resource table.
     */
    private static List<Integer> colorIds(Resources res, String pkg) {
        List<Integer> ids = new ArrayList<>();
        int[] packageIds = "android".equals(pkg) ? new int[]{0x01} :
                new int[]{0x7f, 0x7e, 0x7d, 0x7c};
        for (int packageId : packageIds) {
            for (int type = 1; type <= 0x60; type++) {
                int base = (packageId << 24) | (type << 16);
                String typeName = typeName(res, base);
                if (typeName == null) continue;
                if (!typeName.equals("color")) continue;
                int misses = 0;
                for (int entry = 0; entry <= 0xffff && misses < 64; entry++) {
                    int id = base | entry;
                    try {
                        if (!pkg.equals(res.getResourcePackageName(id))) break;
                        res.getResourceEntryName(id);
                        ids.add(id);
                        misses = 0;
                    } catch (Resources.NotFoundException e) {
                        misses++;
                    }
                }
                return ids;
            }
        }
        return ids;
    }

    private static String typeName(Resources res, int base) {
        for (int entry = 0; entry < 8; entry++) {
            try {
                return res.getResourceTypeName(base | entry);
            } catch (Resources.NotFoundException ignored) {
                // sparse table, try the next entry
            }
        }
        return null;
    }

    /**
     * AMOLED replacement for a dark-mode color, or null to keep it. Same rules as
     * tools/oneui-reference/gen_amoled.py.
     */
    static Integer amoled(int color, String name) {
        int a = color >>> 24, r = (color >> 16) & 0xff, g = (color >> 8) & 0xff, b = color & 0xff;
        int hi = Math.max(r, Math.max(g, b)), lo = Math.min(r, Math.min(g, b));
        boolean state = STATE.matcher(name).find();
        boolean text = TEXT.matcher(name).find() && !BG_STRICT.matcher(name).find();
        boolean bg = BG.matcher(name).find();
        boolean floating = FLOAT.matcher(name).find() && !AREA.matcher(name).find();
        if (text || state) return null;
        if (a < 0xff) {
            // see-through white or tinted surfaces would show as grey or tint on black
            if (a <= 0x66 && hi > 0x60 && (bg || LINE.matcher(name).find())) return 0;
            if (a <= 0x40 && lo >= 0xc0) return 0;
            // see-through dark tints over blur: an AMOLED blur
            if (hi <= 0x60 && a >= 0x20 && (bg || floating)) return 0xf2000000;
            return null;
        }
        if (name.contains("thumb")) return hi <= 0x80 ? 0xff2a2a2a : null;
        if (name.contains("track") || SLIDER.matcher(name).find() && bg)
            return hi <= 0x80 ? 0xff1a1a1a : null;
        if (hi <= 0x30) return floating ? 0xff0a0a0a : 0xff000000;
        if (hi - lo <= 0x18 && hi <= 0x80) {
            if (LINE.matcher(name).find()) return 0xff000000;
            return floating ? 0xff0a0a0a : 0xff000000;
        }
        return null;
    }
}
