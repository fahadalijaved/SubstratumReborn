/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.common.platform;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.TypedValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import projekt.substratum.Substratum;

/**
 * "Dark mode only" for classic themes. Classic Substratum themes were made for phones that are
 * always dark, so their unqualified {@code values/} colours apply in light mode too: on One UI 9
 * a classic framework overlay turned heads-up notifications white on white once scheduled dark
 * mode switched to light.
 * <p>
 * With the setting on (the default for classic themes), every value the engine writes for the
 * theme uses the night configuration, and the day configuration gets the target's own stock
 * value where that is fixed (as the AMOLED engine pairs its night colours). A stock value that
 * follows the wallpaper or a theme attribute gets no day entry: light mode keeps following it.
 * Light mode is then exactly stock. Entries that cannot be moved to night mode (a value made for
 * light mode only, or a configuration the night qualifier cannot be placed in) are dropped and
 * reported, never written for both modes. The LSPosed module applies what the theme leaves to it
 * only while the configuration is night ({@link XposedBackend#DARK_ONLY} in the index).
 * <p>
 * The transform is pure (unit tested); {@link #stockReader} reads the stock values on the device.
 */
public final class ClassicDarkMode {

    /**
     * Per theme: {@code classic_dark_only_<theme package>}
     */
    public static final String PREF_PREFIX = "classic_dark_only_";
    /**
     * Manifest meta-data a theme sets to {@code true} when its overlays are made for light mode
     * as well: the setting then starts off
     */
    public static final String META_LIGHT = "Reborn_LightMode";
    static final String NIGHT = "night";
    static final String NOT_NIGHT = "notnight";

    /**
     * Qualifiers that come after the night mode one in a configuration string (density, touch
     * screen, keyboard, navigation, screen size and platform version), in Android's order
     */
    private static final Pattern AFTER_NIGHT = Pattern.compile(
            "(ldpi|mdpi|tvdpi|hdpi|xhdpi|xxhdpi|xxxhdpi|nodpi|anydpi|\\d+dpi|notouch|finger|" +
                    "stylus|keysexposed|keyshidden|keyssoft|nokeys|qwerty|12key|navexposed|" +
                    "navhidden|nonav|dpad|trackball|wheel|\\d+x\\d+|v\\d+)");

    private ClassicDarkMode() {
    }

    /**
     * The target's stock value in light mode
     */
    interface StockDay {
        /**
         * @return The item value ({@code 0x...}) of the target's own light value, or null when
         * it has none or it follows the wallpaper or a theme attribute (no day entry then)
         */
        String value(String type, String name);
    }

    /**
     * What {@link #transform} made of an overlay's items
     */
    static final class Result {
        final List<String[]> items = new ArrayList<>();
        /**
         * {@code type/name (config)} of every entry left out, for the report
         */
        final List<String> dropped = new ArrayList<>();
        /**
         * Day entries carrying the stock value
         */
        int stockDay;
    }

    /**
     * Whether a classic theme's overlays apply in dark mode only. On unless the user turned it
     * off, or the theme says it is made for light mode too.
     */
    public static boolean isOn(Context context, String themePid) {
        if (themePid == null) return true;
        SharedPreferences prefs = Substratum.getPreferences();
        String key = PREF_PREFIX + themePid;
        if (prefs.contains(key)) return prefs.getBoolean(key, true);
        return !declaresLight(context, themePid);
    }

    public static void set(String themePid, boolean on) {
        if (themePid == null) return;
        Substratum.getPreferences().edit().putBoolean(PREF_PREFIX + themePid, on).apply();
    }

    /**
     * The theme's {@value #META_LIGHT} meta-data is true
     */
    public static boolean declaresLight(Context context, String themePid) {
        if (context == null || themePid == null) return false;
        try {
            ApplicationInfo info = context.getPackageManager()
                    .getApplicationInfo(themePid, PackageManager.GET_META_DATA);
            Bundle meta = info.metaData;
            if (meta == null) return false;
            Object value = meta.get(META_LIGHT);
            return value != null && "true".equalsIgnoreCase(String.valueOf(value).trim());
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return false;
        }
    }

    /**
     * Move prepared items {target, type (decimal), value, config} to night mode and pair them
     * with the stock day value. Pure: unit tested.
     * <ul>
     * <li>a night entry stays as it is;</li>
     * <li>a day entry (no night qualifier) becomes a night entry, unless the theme has its own
     * night value for the same configuration, which wins;</li>
     * <li>a light-mode-only entry ({@code notnight}) and an entry whose configuration cannot take
     * the night qualifier are dropped and reported;</li>
     * <li>every name that ends up with a night entry gets one default entry with the stock day
     * value, when {@code stock} knows a fixed one.</li>
     * </ul>
     *
     * @param stock Stock day values, or null for none (night entries only)
     */
    static Result transform(List<String[]> items, StockDay stock) {
        Result result = new Result();
        Set<String> themeNight = new HashSet<>();
        for (String[] item : items) {
            if (hasQualifier(item[3], NIGHT)) themeNight.add(item[0] + '|' + item[3]);
        }
        Map<String, String[]> out = new LinkedHashMap<>();
        Map<String, String> typeOf = new LinkedHashMap<>();
        Set<String> nightTargets = new LinkedHashSet<>();
        for (String[] item : items) {
            String config = item[3];
            if (hasQualifier(config, NOT_NIGHT)) {
                result.dropped.add(item[0] + " (" + config + ')');
                continue;
            }
            boolean moved = !hasQualifier(config, NIGHT);
            String night = moved ? nightConfig(config) : config;
            if (night == null) {
                result.dropped.add(item[0] + " (" + config + ')');
                continue;
            }
            // The theme's own night value for this configuration wins over its day value
            if (moved && themeNight.contains(item[0] + '|' + night)) continue;
            out.put(item[0] + '|' + night, new String[]{item[0], item[1], item[2], night});
            nightTargets.add(item[0]);
            typeOf.put(item[0], item[1]);
        }
        // Light mode stays stock: each name gets the app's own day value where it is fixed
        Map<String, String[]> day = new LinkedHashMap<>();
        for (String target : nightTargets) {
            int slash = target.indexOf('/');
            if (slash <= 0 || stock == null) continue;
            String value = stock.value(target.substring(0, slash), target.substring(slash + 1));
            if (value == null) continue;
            day.put(target, new String[]{target, typeOf.get(target), value, ""});
            result.stockDay++;
        }
        result.items.addAll(day.values());
        result.items.addAll(out.values());
        return result;
    }

    /**
     * A configuration with the night qualifier in its place ({@code ""} -> {@code night},
     * {@code v27} -> {@code night-v27}, {@code land} -> {@code land-night}). Pure.
     *
     * @return The night configuration, the same one when it is night already, or null when it is
     * for light mode only
     */
    static String nightConfig(String config) {
        if (config == null || config.isEmpty()) return NIGHT;
        if (hasQualifier(config, NIGHT)) return config;
        if (hasQualifier(config, NOT_NIGHT)) return null;
        String[] parts = config.split("-");
        List<String> out = new ArrayList<>(parts.length + 1);
        boolean placed = false;
        for (String part : parts) {
            if (!placed && AFTER_NIGHT.matcher(part.toLowerCase(Locale.ROOT)).matches()) {
                out.add(NIGHT);
                placed = true;
            }
            out.add(part);
        }
        if (!placed) out.add(NIGHT);
        return String.join("-", out);
    }

    static boolean hasQualifier(String config, String qualifier) {
        if (config == null || config.isEmpty()) return false;
        for (String part : config.split("-")) {
            if (part.equals(qualifier)) return true;
        }
        return false;
    }

    /**
     * The target's stock light values, read without any fabricated overlay (the classic theme's
     * own one from an earlier build included). The framework is read through Reborn's own
     * resources, which show it as every app does.
     *
     * @return The reader, or null when stock cannot be read here (night entries only then: in
     * light mode the app falls back to its own values)
     */
    static StockDay stockReader(Context context, String target) {
        if (context == null || target == null) return null;
        String reading = "android".equals(target) ? context.getPackageName() : target;
        Resources[] res = AmoledStock.read(context, reading, true);
        if (res == null) return null;
        Resources day = res[1];
        Map<String, String> cache = new HashMap<>();
        return (type, name) -> {
            String key = type + '/' + name;
            if (cache.containsKey(key)) return cache.get(key);
            String value = readStock(day, target, type, name);
            cache.put(key, value);
            return value;
        };
    }

    private static String readStock(Resources day, String target, String type, String name) {
        int id = day.getIdentifier(name, type, target);
        if (id == 0) return null;
        TypedValue raw = new TypedValue();
        TypedValue value = new TypedValue();
        try {
            day.getValue(id, raw, false);
            day.getValue(id, value, true);
        } catch (Resources.NotFoundException e) {
            return null;
        }
        return stockItemValue(type, value.type, value.data,
                "color".equals(type) && SystemAmoled.fixed(day, raw));
    }

    /**
     * The item value of a stock value, or null when it cannot (or must not) be written. Pure.
     *
     * @param fixed For colours: a literal, or references that end in one (not the wallpaper's)
     */
    static String stockItemValue(String type, int valueType, int data, boolean fixed) {
        switch (type) {
            case "color":
                if (!fixed || valueType < TypedValue.TYPE_FIRST_COLOR_INT ||
                        valueType > TypedValue.TYPE_LAST_COLOR_INT) {
                    return null;
                }
                return String.format(Locale.US, "0x%08x", data);
            case "bool":
                if (valueType != TypedValue.TYPE_INT_BOOLEAN) return null;
                return data != 0 ? "0xffffffff" : "0x00000000";
            case "integer":
                if (valueType < TypedValue.TYPE_FIRST_INT || valueType > TypedValue.TYPE_LAST_INT) {
                    return null;
                }
                return String.format(Locale.US, "0x%08x", data);
            default:
                return null;
        }
    }
}
