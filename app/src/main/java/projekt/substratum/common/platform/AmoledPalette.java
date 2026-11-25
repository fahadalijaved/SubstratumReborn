/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.common.platform;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The per-color decision of "AMOLED for every app": what a dark-mode color becomes, and which
 * rule decided it. Pure Java (no Android classes), so the rules run in JVM unit tests against the
 * One UI reference in docs/oneui9/reference (see AmoledGoldenTest).
 */
public final class AmoledPalette {

    /**
     * Rule labels. The first three keep the stock color; {@link #KEPT} is any other color no rule
     * changes (light and mid tones, translucent layers that are not surfaces).
     */
    public static final String TEXT_KEPT = "text-kept";
    public static final String STATE_KEPT = "state-kept";
    public static final String LIGHT_VARIANT = "light-variant";
    public static final String KEPT = "kept";
    public static final String OUTLINE = "outline";
    public static final String CLEAR = "clear";
    public static final String BLUR = "blur";
    public static final String THUMB = "thumb";
    public static final String SLIDER = "slider";
    public static final String SURFACE = "surface";
    public static final String FLOATING = "floating";
    /**
     * Labels of colors a theme names in its rules: an exact resource name, or a regex
     */
    public static final String EXPLICIT = "explicit";
    public static final String PATTERN_PREFIX = "pattern:";

    static final Pattern STATE = Pattern.compile(
            "ripple|press|focus|select|hover|drag|highlight|state|checked|activated");
    static final Pattern TEXT = Pattern.compile(
            "text|title|label|hint|icon|summary|glyph|tint|foreground|_fg|(^|_)on_|inverse");
    // Light-theme variants keep their values: dark mode uses the _dark ones
    static final Pattern LIGHT = Pattern.compile("_(light|day)$");
    static final Pattern BG_STRICT = Pattern.compile(
            "bg|background|surface|container|fill|scrim|blur");
    static final Pattern BG = Pattern.compile(
            "bg|background|surface|container|fill|panel|scrim|blur|window|keyboard|card|link");
    static final Pattern LINE = Pattern.compile(
            "divider|outline|stroke|separator|_line|border|frame");
    static final Pattern FLOAT = Pattern.compile(
            "float|popup|menu|dialog|search_?(bar|field|textfield|view)|navi|bottom_bar|toolbar|" +
                    "chip|fab|handle|toast|snackbar");
    static final Pattern AREA = Pattern.compile("list|area|layout|screen|page|window");
    static final Pattern SLIDERS = Pattern.compile("slider|seekbar|seek_bar|progress");

    private static final Map<String, Integer> NAMED = new HashMap<>();

    static {
        // The names android.graphics.Color.parseColor accepts
        NAMED.put("black", 0xff000000);
        NAMED.put("darkgray", 0xff444444);
        NAMED.put("gray", 0xff888888);
        NAMED.put("lightgray", 0xffcccccc);
        NAMED.put("white", 0xffffffff);
        NAMED.put("red", 0xffff0000);
        NAMED.put("green", 0xff00ff00);
        NAMED.put("blue", 0xff0000ff);
        NAMED.put("yellow", 0xffffff00);
        NAMED.put("cyan", 0xff00ffff);
        NAMED.put("magenta", 0xffff00ff);
        NAMED.put("aqua", 0xff00ffff);
        NAMED.put("fuchsia", 0xffff00ff);
        NAMED.put("darkgrey", 0xff444444);
        NAMED.put("grey", 0xff888888);
        NAMED.put("lightgrey", 0xffcccccc);
        NAMED.put("lime", 0xff00ff00);
        NAMED.put("maroon", 0xff800000);
        NAMED.put("navy", 0xff000080);
        NAMED.put("olive", 0xff808000);
        NAMED.put("purple", 0xff800080);
        NAMED.put("silver", 0xffc0c0c0);
        NAMED.put("teal", 0xff008080);
    }

    private AmoledPalette() {
    }

    /**
     * What one color becomes: {@link #color} is the AMOLED value, or null to keep the stock one.
     */
    public static final class Decision {
        public final Integer color;
        public final String rule;

        Decision(Integer color, String rule) {
            this.color = color;
            this.rule = rule;
        }

        @Override
        public String toString() {
            return (color == null ? "keep" : String.format("#%08x", color)) + ' ' + rule;
        }
    }

    /**
     * The decision compute() makes for a plain dark-mode color of a package: a color the rules
     * name for it first, the generic rules otherwise.
     */
    public static Decision forColor(String packageName, String name, int color, AmoledRules rules) {
        Decision explicit = rules.explicitDecision(packageName, name);
        return explicit != null ? explicit : decide(color, name, rules);
    }

    /**
     * AMOLED replacement for a dark-mode color by its resource name. Same rules as
     * tools/oneui-reference/gen_amoled.py.
     */
    public static Decision decide(int color, String name, AmoledRules rules) {
        int a = color >>> 24, r = (color >> 16) & 0xff, g = (color >> 8) & 0xff, b = color & 0xff;
        int hi = Math.max(r, Math.max(g, b)), lo = Math.min(r, Math.min(g, b));
        boolean state = STATE.matcher(name).find();
        boolean text = TEXT.matcher(name).find() && !BG_STRICT.matcher(name).find();
        boolean bg = BG.matcher(name).find();
        boolean floating = FLOAT.matcher(name).find() && !AREA.matcher(name).find();
        if (text) return keep(TEXT_KEPT);
        if (state) return keep(STATE_KEPT);
        if (LIGHT.matcher(name).find()) return keep(LIGHT_VARIANT);
        boolean line = LINE.matcher(name).find();
        // Outlines of floating things (popups, menus, dialogs, bars) stay as a thin dark border so
        // black popups still separate from a black page
        if (line && floating) return new Decision(rules.outline, OUTLINE);
        if (a < 0xff) {
            // see-through white or tinted surfaces would show as grey or tint on black
            // (over blur that would leave the bare, untinted blur: the AMOLED blur instead)
            if (a <= 0x66 && hi > 0x60 && (bg || line)) {
                return name.contains("blur") ? new Decision(rules.blur, BLUR) :
                        new Decision(0, CLEAR);
            }
            if (a <= 0x40 && lo >= 0xc0) return new Decision(0, CLEAR);
            // see-through dark tints over blur: an AMOLED blur
            if (hi <= 0x60 && a >= 0x20 && (bg || floating)) return new Decision(rules.blur, BLUR);
            return keep(KEPT);
        }
        if (name.contains("thumb")) {
            return hi <= 0x80 ? new Decision(rules.thumb, THUMB) : keep(KEPT);
        }
        if (name.contains("track") || SLIDERS.matcher(name).find() && bg) {
            return hi <= 0x80 ? new Decision(rules.slider, SLIDER) : keep(KEPT);
        }
        Decision surface = floating ? new Decision(rules.floating, FLOATING) :
                new Decision(rules.background, SURFACE);
        if (hi <= 0x30) return surface;
        // dark greys (cards, containers, lines); mid greys are secondary text and stay
        if (hi - lo <= 0x18 && hi <= 0x52) return surface;
        return keep(KEPT);
    }

    private static Decision keep(String rule) {
        return new Decision(null, rule);
    }

    /**
     * A color as #RGB, #ARGB, #RRGGBB or #AARRGGBB (no alpha means opaque), or one of the names
     * android.graphics.Color.parseColor accepts.
     *
     * @throws IllegalArgumentException if it is neither
     */
    public static int parseColor(String value) {
        if (value == null) throw new IllegalArgumentException("Unknown color: null");
        String s = value.trim();
        if (s.startsWith("#")) {
            String hex = s.substring(1);
            for (int i = 0; i < hex.length(); i++) {
                if (Character.digit(hex.charAt(i), 16) < 0) {
                    throw new IllegalArgumentException("Unknown color: " + value);
                }
            }
            switch (hex.length()) {
                case 3:
                case 4: {
                    StringBuilder longForm = new StringBuilder(hex.length() * 2);
                    for (int i = 0; i < hex.length(); i++) {
                        longForm.append(hex.charAt(i)).append(hex.charAt(i));
                    }
                    long v = Long.parseLong(longForm.toString(), 16);
                    return hex.length() == 3 ? (int) (v | 0xff000000L) : (int) v;
                }
                case 6:
                    return (int) (Long.parseLong(hex, 16) | 0xff000000L);
                case 8:
                    return (int) Long.parseLong(hex, 16);
                default:
                    throw new IllegalArgumentException("Unknown color: " + value);
            }
        }
        Integer named = NAMED.get(s.toLowerCase(Locale.ROOT));
        if (named == null) throw new IllegalArgumentException("Unknown color: " + value);
        return named;
    }
}
