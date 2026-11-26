/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.common.platform;

import com.google.android.material.color.utilities.Hct;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The per-color decision of "AMOLED for every app": what a dark-mode color becomes, and which
 * rule decided it. Pure Java (no Android classes; Material's HCT color space is plain Java too),
 * so the rules run in JVM unit tests against the One UI reference in docs/oneui9/reference (see
 * AmoledGoldenTest).
 * <p>
 * The order, in {@link #forColor}: the user's own rules (keep stock, pin a color), the colors a
 * rules theme names, the accent, then the generic rules of {@link #decide}.
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
    /**
     * An accent color, recolored to the chosen accent with its tone kept
     */
    public static final String ACCENT = "accent";
    /**
     * The user kept the stock color (a sentinel: nothing is written for it)
     */
    public static final String USER_KEEP = "user-keep";
    /**
     * The user pinned a color
     */
    public static final String USER_PIN = "user-pin";

    /**
     * One UI's accent (basic_token_primary_50), used when an app has none of its own
     */
    public static final int ONEUI_BLUE = 0xff387aff;
    static final String SYSTEMUI = "com.android.systemui";
    static final String ACTIVE_TILE = "qs_tile_round_background_on";
    /**
     * The colors an accent recolors: One UI's primary ramp (basic_token_primary_01 to _100) and
     * the Samsung widgets' primary colors. Everything else that shows the accent references these,
     * so it follows.
     */
    static final Pattern ACCENT_NAMES = Pattern.compile(
            "^(basic_token_primary_\\d+|sesl_primary_color_dark|sesl_primary_dark_color_dark)$");

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
     * The decision compute() makes for a plain dark-mode color of a package whose accent is One
     * UI blue (see {@link #forColor(String, String, int, AmoledRules, int)}).
     */
    public static Decision forColor(String packageName, String name, int color, AmoledRules rules) {
        return forColor(packageName, name, color, rules, ONEUI_BLUE);
    }

    /**
     * The decision compute() makes for a plain dark-mode color of a package: the user's rules and
     * the colors the rules theme names first, then the accent, then the generic rules.
     *
     * @param primary50 The app's own basic_token_primary_50 (its stock accent), which sets how
     *                  colorful each accent shade is
     */
    public static Decision forColor(String packageName, String name, int color, AmoledRules rules,
                                    int primary50) {
        Decision explicit = rules.explicitDecision(packageName, name);
        if (explicit != null) return explicit;
        Integer accent = accentFor(packageName, name, color, rules, primary50);
        if (accent != null) return new Decision(accent, ACCENT);
        return decide(color, name, rules);
    }

    /**
     * The accent color for a resource, or null when it is not an accent color (or no accent is
     * chosen). The active quick settings tile takes the accent itself when the tile option is
     * "accent"; the accent colors keep their tone (see {@link #toneSwap}).
     */
    public static Integer accentFor(String packageName, String name, int stock, AmoledRules rules,
                                    int primary50) {
        Integer accent = rules.accent;
        if (accent == null) return null;
        if (rules.accentTile && SYSTEMUI.equals(packageName) && ACTIVE_TILE.equals(name)) {
            return accent | 0xff000000;
        }
        if (!ACCENT_NAMES.matcher(name).matches()) return null;
        return toneSwap(stock, accent, primary50);
    }

    /**
     * A shade of the stock accent as the same shade of another accent, in Material's HCT color
     * space: the accent's hue, its chroma scaled by how colorful this shade is next to the stock
     * accent (0 to 1.2 times), and the shade's own tone. So One UI's light and dark accent steps
     * keep their contrast with text and surfaces: white text, switch thumbs and check marks on an
     * accent fill stay readable. A grey or white accent therefore gives neutral shades at the
     * stock tones (the active quick settings tile takes the accent itself). The stock alpha is
     * kept.
     *
     * @param primary50 The stock accent the shades were made from
     */
    public static int toneSwap(int stock, int accent, int primary50) {
        Hct shade = Hct.fromInt(stock | 0xff000000);
        Hct target = Hct.fromInt(accent | 0xff000000);
        Hct reference = Hct.fromInt(primary50 | 0xff000000);
        double ratio = reference.getChroma() < 1 ? 1 :
                Math.max(0, Math.min(1.2, shade.getChroma() / reference.getChroma()));
        int rgb = Hct.from(target.getHue(), target.getChroma() * ratio, shade.getTone()).toInt();
        return (stock & 0xff000000) | (rgb & 0x00ffffff);
    }

    /**
     * The step of a level's ramp a dark surface takes, by its stock dark value: 0 page, 1 card,
     * 2 raised, 3 container. One UI's own grey ramp keeps its roles (basic_token_gray_100 page,
     * _95 card surface, _90 raised and popup, _80 and _70 containers); any other color goes by
     * its brightest channel: up to 0x20, 0x2c, 0x3b, else (up to 0x52, the darkest grey the rules
     * paint).
     */
    public static int tier(int color) {
        switch (color | 0xff000000) {
            case 0xff010102:
                return 0;
            case 0xff17171a:
                return 1;
            case 0xff252528:
                return 2;
            case 0xff3a3a3d:
            case 0xff4d4d52:
                return 3;
            default:
                break;
        }
        int hi = Math.max((color >> 16) & 0xff, Math.max((color >> 8) & 0xff, color & 0xff));
        if (hi <= 0x20) return 0;
        if (hi <= 0x2c) return 1;
        if (hi <= 0x3b) return 2;
        return 3;
    }

    /**
     * {@code base} moved towards {@code toward} by {@code amount} (0 to 1), per channel; the
     * result is opaque.
     */
    public static int blend(int base, int toward, float amount) {
        int r = mix((base >> 16) & 0xff, (toward >> 16) & 0xff, amount);
        int g = mix((base >> 8) & 0xff, (toward >> 8) & 0xff, amount);
        int b = mix(base & 0xff, toward & 0xff, amount);
        return 0xff000000 | (r << 16) | (g << 8) | b;
    }

    private static int mix(int a, int b, float amount) {
        return Math.max(0, Math.min(255, Math.round(a + (b - a) * amount)));
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
        // Pure black: the rules' background and floating colors; other levels: a step of the ramp
        Decision surface = floating ? new Decision(rules.floatingFor(color), FLOATING) :
                new Decision(rules.surfaceFor(color), SURFACE);
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
