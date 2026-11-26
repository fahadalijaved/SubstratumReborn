/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.common.platform;

import android.content.Context;

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
 *   "background": "#000000",      every dark surface: pages, cards, lists, buttons (level pure)
 *   "floating":   "#000000",      popups, menus, dialogs, floating bars (level pure)
 *   "outline":    "#1f1f1f",      outline of floating elements
 *   "hairline":   "#303030",      lines, strokes and dividers that would vanish into the page
 *   "blur":       "#ff000000",    tint over blurred panels (alpha = how much blur shows)
 *   "slider":     "#1a1a1a",      slider and seekbar tracks
 *   "thumb":      "#2a2a2a",      scrollbar thumbs
 *   "level":      "pure",         pure, dim, elevated or tinted (see AmoledLevel)
 *   "ramp":       ["#000000", "#0b0b0b", "#141414", "#1c1c1c"],
 *                                 page, card, raised and container surfaces of the levels other
 *                                 than pure; default: the level's own ramp
 *   "accent":     "none",         none, monet (the wallpaper's accent), oneui (#387aff),
 *                                 sprite (#009587), white, or a color: recolors One UI's accent
 *                                 colors with their tone kept, in dark mode
 *   "accentLight": false,         the accent in light mode too
 *   "tint":       0.05,           how much of the accent a tinted ramp takes
 *   "qsTrack":    "#10ffffff",    One UI 9 brightness and volume bar tracks (LSPosed module)
 *   "include":    ["^com\\.samsung\\..*"],   regexes of packages; default: Samsung, Google, system
 *   "exclude":    ["^com\\.google\\.android\\.gms$"],
 *   "colors": { "com.android.systemui": { "qs_tile_round_background_on": "#ffffff",
 *                                         "^sec_dashboard_.*_color$": "#262626" } }
 * }
 * </pre>
 * Keys in "colors" are exact resource names, or regexes when they start with ^.
 * <p>
 * The choices made in Substratum (look, accent, active tile: {@link #withUserOptions}) override
 * the theme's, and the user's own rules ({@link AmoledUserRules}: keep a color stock, pin one)
 * come before everything.
 */
public final class AmoledRules {

    public static final String ASSET = "amoled/rules.json";

    int background = 0xff000000;
    int floating = 0xff000000;
    int outline = 0xff1f1f1f;
    int hairline = 0xff303030;
    int blur = 0xff000000;
    int slider = 0xff1a1a1a;
    int thumb = 0xff2a2a2a;
    final List<Pattern> include = new ArrayList<>();
    final List<Pattern> exclude = new ArrayList<>();
    final Map<String, Map<String, Integer>> exact = new HashMap<>();
    final Map<String, List<Object[]>> patterns = new HashMap<>(); // {Pattern, Integer}
    /**
     * Quick settings row height the theme suggests, or 0
     */
    public float qsAspect;
    AmoledLevel level = AmoledLevel.PURE;
    /**
     * The theme's own ramp for the levels other than pure, or null for the level's
     */
    int[] ramp;
    /**
     * The accent (opaque), or null for none
     */
    Integer accent;
    /**
     * The accent as chosen: none, monet, oneui, sprite, white or a color (see
     * {@link AmoledPresets}); null if nobody chose one
     */
    String accentChoice;
    boolean accentLight;
    float tint = 0.05f;
    int qsTrack = QsOptions.DEFAULT_TRACK;
    /**
     * The active quick settings tile takes the accent
     */
    boolean accentTile;
    AmoledUserRules user;
    private int[] effectiveRamp;

    public static final String PREF_ACTIVE_TILE = "amoled_active_tile";
    public static final String PREF_DISABLED_APPS = "amoled_disabled_apps";
    public static final String PREF_QS_CHOSEN = "amoled_qs_chosen";
    public static final String TILE_WHITE = "white";
    public static final String TILE_BLUE = "blue";
    public static final String TILE_STOCK = "stock";
    /**
     * The active tile takes the accent (white when there is none)
     */
    public static final String TILE_ACCENT = "accent";
    private static final String ACTIVE_TILE = AmoledPalette.ACTIVE_TILE;
    /**
     * The decision for a color the user keeps stock: compute() writes nothing for it
     */
    static final AmoledPalette.Decision KEEP =
            new AmoledPalette.Decision(null, AmoledPalette.USER_KEEP);

    /**
     * Built-in rules, the same as Reborn AMOLED's rules.json.
     */
    public static AmoledRules defaults() {
        AmoledRules r = new AmoledRules();
        r.include.add(Pattern.compile(
                "^(android|com\\.(samsung|sec|google|android)\\..*|com\\.wssyncmldm|com\\.wssnps)$"));
        Map<String, Integer> sysui = new HashMap<>();
        sysui.put("panel_bg_color", 0xff000000);
        sysui.put("panel_background_blur_color", 0xff000000);
        sysui.put("basic_token_effect_blur_surface", 0xff000000);
        sysui.put("basic_token_effect_blur_surface_dark", 0xff000000);
        sysui.put("basic_token_effect_blur_chip", 0xff000000);
        sysui.put("basic_token_effect_blur_chip_dark", 0xff000000);
        sysui.put("qs_tile_layout_container_bg", 0xff000000);
        sysui.put("qs_tile_container_bg", 0xff000000);
        sysui.put("qs_tile_round_background_off", 0xff000000);
        sysui.put("qs_tile_round_background_dim", 0xff000000);
        sysui.put("seekbar_background_color", 0xff1f1f1f);
        sysui.put("seekbar_background_color_expand", 0xff1f1f1f);
        sysui.put("seekbar_color_expand", 0xfffcfcfc);
        sysui.put("qs_tile_round_background_on", 0xffffffff);
        sysui.put("qs_switch_off_background_color", 0xff242424);
        sysui.put("qs_detail_layout_popup_menu_background_color", 0xff000000);
        sysui.put("qs_detail_layout_popup_menu_background_color_no_blur", 0xff000000);
        sysui.put("sec_qs_detail_bg_color", 0xff000000);
        sysui.put("notification_material_background_color", 0xff0a0a0a);
        sysui.put("notification_material_background_dimmed_color", 0xff0a0a0a);
        sysui.put("notification_material_background_on_lockscreen", 0xff000000);
        sysui.put("sec_brightness_mirror_background_color", 0xff000000);
        sysui.put("qs_tile_container_bg_border_color", 0xff262626);
        sysui.put("qs_tile_icon_off_tint_color", 0xfffcfcff);
        sysui.put("sec_qs_switch_off_background_color", 0xff242424);
        sysui.put("sec_qs_media_player_background_color", 0xff000000);
        // One UI 9 QS boxes: this color with blur reduced, a wallpaper tint otherwise
        sysui.put("qs_tile_coloring_container_bg_ultra_mode", 0xff000000);
        // Brightness and volume bars: a plain white fill instead of a grey-to-white gradient
        sysui.put("tw_progress_color_control_activated", 0xffffffff);
        sysui.put("tw_progress_color_control_activated_start", 0xffffffff);
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

    /**
     * The rules AMOLED applies with: the theme's (the built-in ones for null or a theme that
     * cannot be read), the look chosen in Substratum and the user's own rules. Reads files.
     */
    public static AmoledRules effective(Context context, String themePackage,
                                        android.content.SharedPreferences prefs) {
        AmoledRules rules = defaults();
        if (themePackage != null) {
            try {
                rules = fromTheme(context, themePackage);
            } catch (Exception e) {
                rules = defaults();
            }
        }
        return rules.withUserOptions(context, prefs).withUserRules(AmoledUserRules.read(context));
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
        if (o.has("background")) r.background = AmoledPalette.parseColor(o.getString("background"));
        if (o.has("floating")) r.floating = AmoledPalette.parseColor(o.getString("floating"));
        if (o.has("outline")) r.outline = AmoledPalette.parseColor(o.getString("outline"));
        if (o.has("hairline")) r.hairline = AmoledPalette.parseColor(o.getString("hairline"));
        if (o.has("blur")) r.blur = AmoledPalette.parseColor(o.getString("blur"));
        if (o.has("slider")) r.slider = AmoledPalette.parseColor(o.getString("slider"));
        if (o.has("thumb")) r.thumb = AmoledPalette.parseColor(o.getString("thumb"));
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
                    int color = AmoledPalette.parseColor(entries.getString(name));
                    if (name.startsWith("^")) {
                        r.patterns.computeIfAbsent(pkg, k -> new ArrayList<>())
                                .add(0, new Object[]{Pattern.compile(name), color});
                    } else {
                        r.exact.computeIfAbsent(pkg, k -> new HashMap<>()).put(name, color);
                    }
                }
            }
        }
        if (o.has("qs")) {
            JSONObject qs = o.getJSONObject("qs");
            if (qs.has("aspect")) r.qsAspect = (float) qs.getDouble("aspect");
        }
        if (o.has("level")) r.level = AmoledLevel.parse(o.getString("level"), AmoledLevel.PURE);
        if (o.has("ramp")) {
            JSONArray a = o.getJSONArray("ramp");
            if (a.length() != 4) throw new IllegalArgumentException("ramp needs 4 colors");
            r.ramp = new int[4];
            for (int i = 0; i < 4; i++) {
                r.ramp[i] = AmoledPalette.parseColor(a.getString(i)) | 0xff000000;
            }
        }
        if (o.has("accent")) {
            String choice = o.isNull("accent") ? AmoledPresets.ACCENT_NONE : o.getString("accent");
            r.accentChoice = choice;
            r.accent = AmoledPresets.resolveAccent(choice, null);
        }
        if (o.has("accentLight")) r.accentLight = o.getBoolean("accentLight");
        if (o.has("tint")) r.tint = (float) Math.max(0, Math.min(1, o.getDouble("tint")));
        if (o.has("qsTrack")) r.qsTrack = AmoledPalette.parseColor(o.getString("qsTrack"));
        return r;
    }

    // The look

    public AmoledLevel level() {
        return level;
    }

    /**
     * The accent (opaque), or null for none
     */
    public Integer accent() {
        return accent;
    }

    public String accentChoice() {
        return accentChoice;
    }

    public boolean accentLight() {
        return accentLight;
    }

    public boolean accentTile() {
        return accentTile;
    }

    /**
     * One UI 9's brightness and volume bar tracks, drawn by the LSPosed module
     */
    public int qsTrack() {
        return qsTrack;
    }

    /**
     * A different level drops the theme's own ramp, which was made for its level.
     */
    public AmoledRules setLevel(AmoledLevel level) {
        if (level != null && level != this.level) {
            this.level = level;
            ramp = null;
            effectiveRamp = null;
        }
        return this;
    }

    public AmoledRules setAccent(Integer accent) {
        this.accent = accent == null ? null : accent | 0xff000000;
        effectiveRamp = null;
        return this;
    }

    public AmoledRules setAccentLight(boolean accentLight) {
        this.accentLight = accentLight;
        return this;
    }

    /**
     * The surface steps of the level: page, card, raised, container. A tinted level blends the
     * accent (One UI blue without one) into the last three by {@code tint}.
     */
    public int[] ramp() {
        int[] r = effectiveRamp;
        if (r == null) {
            r = ramp != null ? ramp.clone() : level.defaultRamp();
            if (level == AmoledLevel.TINTED) {
                int toward = accent != null ? accent : AmoledPalette.ONEUI_BLUE;
                for (int i = 1; i < r.length; i++) r[i] = AmoledPalette.blend(r[i], toward, tint);
            }
            effectiveRamp = r;
        }
        return r.clone();
    }

    /**
     * What a dark surface becomes (see {@link AmoledPalette#tier})
     */
    int surfaceFor(int stock) {
        if (level == AmoledLevel.PURE) return background;
        return rampStep(AmoledPalette.tier(stock));
    }

    /**
     * What a floating surface becomes: at least the raised step, so popups stay visible
     */
    int floatingFor(int stock) {
        if (level == AmoledLevel.PURE) return floating;
        return rampStep(Math.max(2, AmoledPalette.tier(stock)));
    }

    /**
     * The page: what the darkest surfaces become
     */
    int pageColor() {
        return level == AmoledLevel.PURE ? background : rampStep(0);
    }

    /**
     * Everything that decides the colors compute() writes, as one stable text: the colors, the
     * look, the packages covered, the theme's named colors and the user's rules. Kept with each
     * app's cached colors ({@link AmoledCache}): any change here reads the app again.
     */
    public String fingerprint() {
        StringBuilder s = new StringBuilder();
        s.append(String.format(java.util.Locale.ROOT, "b%08x f%08x o%08x h%08x u%08x s%08x t%08x",
                background, floating, outline, hairline, blur, slider, thumb));
        s.append(" level=").append(level.key());
        s.append(" ramp=");
        for (int step : ramp()) s.append(String.format(java.util.Locale.ROOT, "%08x,", step));
        s.append(" accent=").append(accent == null ? "-" : Integer.toHexString(accent));
        s.append(" light=").append(accentLight).append(" tile=").append(accentTile);
        s.append(" tint=").append(tint);
        s.append(" include=");
        for (Pattern p : include) s.append(p.pattern()).append('\u0001');
        s.append(" exclude=");
        for (Pattern p : exclude) s.append(p.pattern()).append('\u0001');
        s.append(" exact=");
        for (Map.Entry<String, Map<String, Integer>> pkg : new java.util.TreeMap<>(exact).entrySet()) {
            s.append(pkg.getKey()).append('{');
            for (Map.Entry<String, Integer> e : new java.util.TreeMap<>(pkg.getValue()).entrySet()) {
                s.append(e.getKey()).append('=').append(Integer.toHexString(e.getValue()))
                        .append(',');
            }
            s.append('}');
        }
        s.append(" patterns=");
        for (Map.Entry<String, List<Object[]>> pkg :
                new java.util.TreeMap<>(patterns).entrySet()) {
            s.append(pkg.getKey()).append('{');
            for (Object[] e : pkg.getValue()) {
                s.append(((Pattern) e[0]).pattern()).append('=')
                        .append(Integer.toHexString((Integer) e[1])).append(',');
            }
            s.append('}');
        }
        s.append(" user=");
        try {
            s.append(user == null ? "-" : user.toJson());
        } catch (Exception e) {
            s.append("?");
        }
        return s.toString();
    }

    private int rampStep(int step) {
        if (effectiveRamp == null) ramp();
        return effectiveRamp[step];
    }

    // Choices made in Substratum

    /**
     * Choices made in Substratum override the theme: the look (preset, level, accent) and the
     * color of active quick settings tiles. A Monet accent falls back to One UI blue (no context
     * to read the wallpaper's colors with).
     */
    public AmoledRules withUserOptions(android.content.SharedPreferences prefs) {
        return withUserOptions(prefs, null);
    }

    /**
     * {@link #withUserOptions(android.content.SharedPreferences)} with the wallpaper's accent
     * read now, for a Monet accent (Android 12 and later).
     */
    public AmoledRules withUserOptions(android.content.Context context,
                                       android.content.SharedPreferences prefs) {
        return withUserOptions(prefs, AmoledPresets.monetAccent(context));
    }

    public AmoledRules withUserOptions(android.content.SharedPreferences prefs, Integer monet) {
        if (prefs == null) return withOptions(null, null, null, null, monet);
        AmoledPresets.Look look = AmoledPresets.look(prefs);
        return withOptions(look.level, look.accent, look.accentLight,
                prefs.getString(PREF_ACTIVE_TILE, null), monet);
    }

    /**
     * A look given here instead of read from the preferences (each part null to keep what is
     * there), for previews of a look that is not chosen yet: a preset, or an active tile option.
     *
     * @param accent An accent choice (see {@link AmoledPresets#resolveAccent})
     * @param tile   An active tile option ({@link #TILE_WHITE}...), or null
     * @param monet  The wallpaper's accent, or null
     */
    public AmoledRules withLook(AmoledLevel level, String accent, Boolean accentLight, String tile,
                                Integer monet) {
        return withOptions(level, accent, accentLight, tile, monet);
    }

    /**
     * The choices, each null to keep what the theme says.
     *
     * @param accent An accent choice (see {@link AmoledPresets#resolveAccent})
     * @param monet  The wallpaper's accent, or null
     */
    AmoledRules withOptions(AmoledLevel level, String accent, Boolean accentLight, String tile,
                            Integer monet) {
        setLevel(level);
        if (accent != null) accentChoice = accent;
        if (accentChoice != null) setAccent(AmoledPresets.resolveAccent(accentChoice, monet));
        if (accentLight != null) this.accentLight = accentLight;
        accentTile = false;
        if (tile == null) return this;
        Map<String, Integer> sysui = exact.computeIfAbsent("com.android.systemui",
                k -> new HashMap<>());
        switch (tile) {
            case TILE_WHITE:
                sysui.put(ACTIVE_TILE, 0xffffffff);
                break;
            case TILE_BLUE:
                sysui.put(ACTIVE_TILE, AmoledPalette.ONEUI_BLUE);
                break;
            case TILE_STOCK:
                sysui.remove(ACTIVE_TILE);
                break;
            case TILE_ACCENT:
                if (this.accent != null) {
                    sysui.remove(ACTIVE_TILE);
                    accentTile = true;
                } else {
                    sysui.put(ACTIVE_TILE, 0xffffffff);
                }
                break;
            default:
                break;
        }
        return this;
    }

    /**
     * The user's own rules, consulted before anything else.
     */
    public AmoledRules withUserRules(AmoledUserRules user) {
        this.user = user;
        return this;
    }

    /**
     * Whether "AMOLED for every app" may change a resource at all: never the framework's Monet
     * roles (system_accent1_500, system_neutral1_900...), which follow the wallpaper in both
     * modes and which other colors reference.
     */
    public static boolean touches(String packageName, String name) {
        return !("android".equals(packageName) && name.startsWith("system_"));
    }

    public boolean covers(String packageName) {
        for (Pattern p : exclude) if (p.matcher(packageName).matches()) return false;
        for (Pattern p : include) if (p.matcher(packageName).matches()) return true;
        return false;
    }

    /**
     * Hand-picked color for a resource, or null to let the rules decide (also for a color the
     * user keeps stock: {@link #explicitDecision} tells the two apart).
     */
    Integer explicit(String packageName, String name) {
        AmoledPalette.Decision decision = explicitDecision(packageName, name);
        return decision == null ? null : decision.color;
    }

    /**
     * Hand-picked color for a resource with the rule that named it: the user's rules first
     * ({@link #KEEP}, a sentinel with no color, or {@code user-pin}), then the theme's
     * ({@code explicit}, or {@code pattern:<regex>}). Null to let the accent and the generic
     * rules decide.
     */
    AmoledPalette.Decision explicitDecision(String packageName, String name) {
        if (user != null) {
            AmoledPalette.Decision decision = user.decision(packageName, name);
            if (decision != null) return decision;
        }
        Map<String, Integer> e = exact.get(packageName);
        if (e != null && e.containsKey(name)) {
            return new AmoledPalette.Decision(e.get(name), AmoledPalette.EXPLICIT);
        }
        List<Object[]> p = patterns.get(packageName);
        if (p != null) {
            for (Object[] entry : p) {
                Pattern pattern = (Pattern) entry[0];
                if (pattern.matcher(name).find()) {
                    return new AmoledPalette.Decision((Integer) entry[1],
                            AmoledPalette.PATTERN_PREFIX + pattern.pattern());
                }
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
