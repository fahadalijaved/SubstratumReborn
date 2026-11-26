/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.common.platform;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.Log;
import android.util.TypedValue;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import projekt.substratum.util.helpers.Root;

/**
 * Applies an overlay's colors, booleans and integers as a fabricated overlay in the system
 * Overlay Manager (root required).
 * <p>
 * Fabricated values are resolved natively: they reach colors that the target only uses through
 * references, including those inside its own drawables, follow qualifiers such as night mode and
 * apply live, system-wide for the framework. The LSPosed module keeps handling what fabricated
 * overlays cannot carry (dimensions, drawables, layouts).
 * <p>
 * Fabricated values are literals, so color references in legacy themes are resolved when the
 * overlay is registered (see {@link #resolveColor}):
 * <ul>
 * <li>{@code @color/x} resolves inside the overlay's own collected values, same configuration
 * first, then the default one, following up to {@value #MAX_REFERENCE_DEPTH} references.</li>
 * <li>{@code @android:color/x} and {@code @*android:color/x} resolve to the framework value the
 * system shows right now, read with a night configuration for night entries. A framework color
 * the theme itself overrides (in its framework overlay, or through Monet) therefore resolves to
 * its current system value, frozen at build time: rebuild the overlay to pick up later changes.
 * A default entry whose resolved value differs at night also gets a night entry.</li>
 * <li>Anything else ({@code ?attr/}, a target's own colors, deeper chains) is left to the
 * module: such values are reported back so the index tells the module to keep applying them.</li>
 * </ul>
 * <p>
 * Root's fabricated overlays are owned by the shell: their full name is
 * {@code com.android.shell:<overlay name>}.
 */
public final class FabricatedValues {

    static final String DIR = "/data/system/substratum";
    private static final String TAG = "SubstratumFabricated";
    private static final String OWNER = "com.android.shell:";
    private static final int TYPE_INT_DEC = 0x10;
    private static final int TYPE_INT_BOOLEAN = 0x12;
    private static final int TYPE_INT_COLOR_ARGB8 = 0x1c;
    private static final String LABEL = "u:object_r:system_data_file:s0";
    private static volatile boolean dirReady;
    static final int MAX_REFERENCE_DEPTH = 5;
    static final String NIGHT = "night";

    /**
     * Framework colors for the resolver; null when the color does not exist.
     */
    interface FrameworkColors {
        Integer color(String name, boolean night);
    }

    private FabricatedValues() {
    }

    /**
     * Collect the plain values of an overlay's sources and register them, disabled.
     *
     * @param workArea The overlay's compilation folder (any values* folder below it is read;
     *                 variant folders named type2_* take precedence, as in the aapt call)
     * @return Number of values registered, 0 if there were none or it failed
     */
    public static int register(Context context, File workArea, String target, String name) {
        return register(context, workArea, target, name, null);
    }

    /**
     * @param leftToModule Receives {@code type/name} of every value that could not be fabricated
     *                     in all of its configurations (unresolvable references): the LSPosed
     *                     module has to keep applying those. May be null.
     */
    public static int register(Context context, File workArea, String target, String name,
                               Set<String> leftToModule) {
        return register(context, workArea, target, name, leftToModule, false, null);
    }

    /**
     * @param leftToModule Also receives the values a shell overlay may not change in the target
     *                     (see {@link ClassicRoute}) and the bools and integers this shell could
     *                     not carry
     * @param darkOnly     Night entries only, paired with the target's stock day values
     *                     ({@link ClassicDarkMode})
     * @param report       Receives what was moved or left out, may be null
     */
    public static int register(Context context, File workArea, String target, String name,
                               Set<String> leftToModule, boolean darkOnly,
                               ClassicReport report) {
        Map<String, String[]> items = new LinkedHashMap<>();
        Map<String, String> colors = new LinkedHashMap<>();
        Set<String> skipped = new HashSet<>();
        List<File> folders = new ArrayList<>();
        collectValueFolders(workArea, 0, folders);
        folders.sort((a, b) -> Boolean.compare(isVariant(a), isVariant(b)));
        for (File folder : folders) readValues(folder, items, colors, skipped);
        resolveColors(colors, systemColors(context), items, skipped);
        Set<String> toModule = leftToModule != null ? leftToModule : new HashSet<>();
        toModule.addAll(skipped);
        List<String[]> list = new ArrayList<>(items.values());
        if (darkOnly && !list.isEmpty()) {
            ClassicDarkMode.Result dark = ClassicDarkMode.transform(list,
                    ClassicDarkMode.stockReader(context, target));
            list = dark.items;
            if (report != null) report.darkDropped.addAll(dark.dropped);
            Log.i(TAG, name + ": dark mode only, " + dark.stockDay + " stock light values, " +
                    dark.dropped.size() + " light-only values left out");
        }
        // What a shell overlay may not change here goes to the module, before idmap2 refuses
        // the whole overlay for it
        Set<String> refused = ClassicRoute.refusedFor(context, target, list);
        list = ClassicRoute.withoutRefused(list, refused, toModule);
        Set<String> scalars = new HashSet<>();
        List<String[]> registered = registerChecked(context, target, name, list, scalars);
        toModule.addAll(scalars);
        if (registered == null) return 0;
        ClassicRoute.saveDefinition(context, name, registered);
        return registered.size();
    }

    /**
     * Turn the collected colors into items. Pure: unit tested.
     *
     * @param colors    Raw color texts by {@code name|config}, in reading order
     * @param framework Framework colors, may be null
     * @param items     Receives the items by {@code color/name|config}
     * @param skipped   Receives {@code color/name} for each color left to the module
     */
    static void resolveColors(Map<String, String> colors, FrameworkColors framework,
                              Map<String, String[]> items, Set<String> skipped) {
        for (Map.Entry<String, String> entry : colors.entrySet()) {
            String key = entry.getKey();
            int bar = key.lastIndexOf('|');
            String colorName = key.substring(0, bar);
            String config = key.substring(bar + 1);
            Integer argb = resolveColor(entry.getValue(), config, colors, framework);
            if (argb == null) {
                skipped.add("color/" + colorName);
                continue;
            }
            items.put("color/" + colorName + '|' + config, colorItem(colorName, argb, config));
            // A default value can differ at night (a framework color, or a local one with a night
            // variant): the reference would follow, so the literal needs a night entry too
            if (config.isEmpty() && !entry.getValue().trim().startsWith("#") &&
                    !colors.containsKey(colorName + '|' + NIGHT)) {
                Integer night = resolveColor(entry.getValue(), NIGHT, colors, framework);
                if (night != null && !night.equals(argb)) {
                    items.put("color/" + colorName + '|' + NIGHT,
                            colorItem(colorName, night, NIGHT));
                }
            }
        }
    }

    /**
     * Resolve a color value to ARGB. Pure: unit tested.
     *
     * @param text      The value as written: {@code #RGB}, {@code #ARGB}, {@code #RRGGBB},
     *                  {@code #AARRGGBB}, {@code @color/x}, {@code @android:color/x} or
     *                  {@code @*android:color/x}
     * @param config    Configuration of the entry ("" for values/, "night" for values-night/)
     * @param colors    The overlay's own raw colors by {@code name|config}
     * @param framework Framework colors, may be null
     * @return The color, or null when it has to stay with the module
     */
    static Integer resolveColor(String text, String config, Map<String, String> colors,
                                FrameworkColors framework) {
        return resolveColor(text, config, colors, framework, 0);
    }

    private static Integer resolveColor(String text, String config, Map<String, String> colors,
                                        FrameworkColors framework, int depth) {
        if (text == null) return null;
        text = text.trim();
        if (text.startsWith("#")) return parseColor(text);
        if (depth >= MAX_REFERENCE_DEPTH) return null;
        if (text.startsWith("@color/")) {
            String ref = text.substring("@color/".length());
            String raw = colors.get(ref + '|' + config);
            // The resource system falls back to the default configuration as well
            if (raw == null && !config.isEmpty()) raw = colors.get(ref + '|');
            return raw == null ? null : resolveColor(raw, config, colors, framework, depth + 1);
        }
        if (text.startsWith("@android:color/") || text.startsWith("@*android:color/")) {
            if (framework == null) return null;
            String ref = text.substring(text.indexOf('/') + 1);
            if (ref.isEmpty()) return null;
            return framework.color(ref, isNight(config));
        }
        return null;
    }

    static boolean isNight(String config) {
        for (String qualifier : config.split("-")) {
            if (qualifier.equals(NIGHT)) return true;
        }
        return false;
    }

    /**
     * {@code #RGB}, {@code #ARGB}, {@code #RRGGBB} or {@code #AARRGGBB}; null otherwise. Pure.
     */
    static Integer parseColor(String text) {
        if (text == null || !text.startsWith("#")) return null;
        String hex = text.substring(1);
        if (hex.length() == 3 || hex.length() == 4) {
            StringBuilder longForm = new StringBuilder();
            for (int i = 0; i < hex.length(); i++) {
                longForm.append(hex.charAt(i)).append(hex.charAt(i));
            }
            hex = longForm.toString();
        }
        if (hex.length() != 6 && hex.length() != 8) return null;
        long value;
        try {
            value = Long.parseLong(hex, 16);
        } catch (NumberFormatException e) {
            return null;
        }
        if (hex.length() == 6) value |= 0xff000000L;
        return (int) value;
    }

    private static String[] colorItem(String name, int argb, String config) {
        return new String[]{"color/" + name, String.valueOf(TYPE_INT_COLOR_ARGB8),
                String.format(Locale.US, "0x%08x", argb), config};
    }

    /**
     * Framework colors as the system shows them right now, day or night.
     */
    private static FrameworkColors systemColors(Context context) {
        Resources[] byMode = new Resources[2];
        return (colorName, night) -> {
            int slot = night ? 1 : 0;
            if (byMode[slot] == null) byMode[slot] = configured(context, night);
            Resources res = byMode[slot];
            int id = res.getIdentifier(colorName, "color", "android");
            if (id == 0) return null;
            TypedValue value = new TypedValue();
            try {
                res.getValue(id, value, true);
            } catch (Resources.NotFoundException e) {
                return null;
            }
            // Plain colors only: a color state list frozen to one color would lose its states
            if (value.type < TypedValue.TYPE_FIRST_COLOR_INT ||
                    value.type > TypedValue.TYPE_LAST_COLOR_INT) {
                return null;
            }
            return value.data;
        };
    }

    private static Resources configured(Context context, boolean night) {
        Resources base = context != null ? context.getResources() : Resources.getSystem();
        Configuration config = new Configuration(base.getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) |
                (night ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        try {
            if (context != null) return context.createConfigurationContext(config).getResources();
        } catch (RuntimeException ignored) {
            // fall through to the bare framework
        }
        Resources system = Resources.getSystem();
        //noinspection deprecation
        return new Resources(system.getAssets(), system.getDisplayMetrics(), config);
    }

    /**
     * Register prepared items {target, type (decimal), value, config} as a fabricated overlay.
     *
     * @return Number of values registered, 0 if there were none or it failed
     */
    public static int registerItems(Context context, String target, String name,
                                    List<String[]> items) {
        if (items.isEmpty()) return 0;
        return ok(fabricate(context, target, name, items), name) ? items.size() : 0;
    }

    /**
     * {@link #registerItems}, retrying without bools and integers when the shell refuses the
     * definition: in {@code --file} mode this shell may take each item's type from the target
     * path ({@code bool/x}) and fail the whole definition on it ("Error: For input string:
     * \"bool\""), so the overlay's colours would reach scoped apps only.
     *
     * @param movedToModule Receives the bools and integers left out on the retry; the module
     *                      applies them by value
     * @return The items registered, or null when nothing was
     */
    static List<String[]> registerChecked(Context context, String target, String name,
                                          List<String[]> items, Set<String> movedToModule) {
        if (items.isEmpty()) return null;
        String out = fabricate(context, target, name, items);
        if (ok(out, null)) {
            Log.i(TAG, name + ": " + items.size() + " values registered");
            return items;
        }
        Set<String> scalars = new HashSet<>();
        List<String[]> colors = withoutScalars(items, scalars);
        if (scalars.isEmpty()) {
            ok(out, name);
            return null;
        }
        Log.w(TAG, name + ": the shell refused the definition" +
                (out != null && out.contains("For input string") ? " (bool/integer types)" : "") +
                ", retrying without its " + scalars.size() + " bools and integers");
        movedToModule.addAll(scalars);
        if (colors.isEmpty()) return null;
        if (!ok(fabricate(context, target, name, colors), name)) return null;
        Log.i(TAG, name + ": " + colors.size() + " colours registered, " + scalars.size() +
                " bools and integers left to the module");
        return colors;
    }

    /**
     * The items without bools and integers, whose {@code type/name} go to {@code scalars}. Pure.
     */
    static List<String[]> withoutScalars(List<String[]> items, Set<String> scalars) {
        List<String[]> kept = new ArrayList<>(items.size());
        for (String[] item : items) {
            if (item[0].startsWith("bool/") || item[0].startsWith("integer/")) {
                scalars.add(item[0]);
            } else {
                kept.add(item);
            }
        }
        return kept;
    }

    /**
     * Writes the definition and runs {@code cmd overlay fabricate}; the shell's output
     */
    private static String fabricate(Context context, String target, String name,
                                    List<String[]> items) {
        File definition = new File(context.getCacheDir(), name + ".xml");
        try (FileWriter writer = new FileWriter(definition)) {
            writer.write(definitionXml(items));
        } catch (IOException e) {
            Log.e(TAG, "Could not write the definition", e);
            return null;
        }
        // system_server reads the definition itself, so it has to live in a system data folder
        prepareDir();
        String dest = DIR + '/' + name + ".xml";
        String out = Root.runCommand("cp -f '" + definition.getAbsolutePath() + "' '" + dest + "'" +
                " && chown system:system '" + dest + "' && chmod 644 '" + dest + "'" +
                " && chcon " + LABEL + " '" + dest + "'" +
                " && cmd overlay fabricate --target " + target + " --name " + name +
                " --file '" + dest + "' 2>&1; echo rc=$?");
        //noinspection ResultOfMethodCallIgnored
        definition.delete();
        return out;
    }

    /**
     * @param name Logs a failure for this overlay when not null
     */
    private static boolean ok(String out, String name) {
        boolean ok = out != null && out.trim().endsWith("rc=0") && !out.contains("Error");
        if (!ok && name != null) {
            Log.e(TAG, "fabricate failed for " + name + ": " + out);
            // The folder may have gone: set it up again next time
            dirReady = false;
        }
        return ok;
    }

    /**
     * The definition file of prepared items {target, type (decimal), value, config}.
     */
    static String definitionXml(List<String[]> items) {
        StringBuilder xml = new StringBuilder("<overlay>\n");
        for (String[] item : items) {
            xml.append(String.format(Locale.US,
                    "  <item target=\"%s\" type=\"0x%02x\" value=\"%s\"%s />\n",
                    item[0], Integer.parseInt(item[1]), item[2],
                    item[3].isEmpty() ? "" : " config=\"" + item[3] + "\""));
        }
        return xml.append("</overlay>\n").toString();
    }

    /**
     * Create the definitions folder, owned by system and labelled as system data, once per
     * process (only the folder itself: each definition gets its owner and label as it is copied).
     */
    private static synchronized void prepareDir() {
        if (dirReady) return;
        String out = Root.runCommand("mkdir -p " + DIR + " && chown system:system " + DIR +
                " && chcon " + LABEL + " " + DIR + "; echo rc=$?");
        dirReady = out != null && out.trim().endsWith("rc=0");
    }

    public static void enable(Iterable<String> names) {
        run("enable", names);
    }

    public static void disable(Iterable<String> names) {
        run("disable", names);
    }

    /**
     * Switch an overlay's fabricated half off for good: disabled, and its definition removed so
     * the restore after a reboot does not register it again
     */
    static void forget(String name) {
        Root.runCommand("cmd overlay disable --user 0 " + OWNER + name + " >/dev/null 2>&1; " +
                "rm -f '" + DIR + '/' + name + ".xml'");
    }

    private static void run(String verb, Iterable<String> names) {
        StringBuilder command = new StringBuilder();
        for (String name : names) {
            command.append("cmd overlay ").append(verb).append(" --user 0 ")
                    .append(OWNER).append(name).append(" >/dev/null 2>&1; ");
        }
        if (command.length() != 0) Root.runCommand(command.toString());
    }

    private static boolean isVariant(File folder) {
        return folder.getParentFile() != null &&
                folder.getParentFile().getName().startsWith("type2_");
    }

    private static void collectValueFolders(File dir, int depth, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null || depth > 4) return;
        Arrays.sort(children);
        for (File child : children) {
            if (!child.isDirectory()) continue;
            String n = child.getName();
            if (n.equals("values") || n.startsWith("values-")) {
                out.add(child);
            } else if (!n.equals("assets")) {
                collectValueFolders(child, depth + 1, out);
            }
        }
    }

    private static void readValues(File folder, Map<String, String[]> items,
                                   Map<String, String> colors, Set<String> skipped) {
        String config = folder.getName().equals("values") ? "" :
                folder.getName().substring("values-".length());
        File[] files = folder.listFiles((d, f) -> f.endsWith(".xml"));
        if (files == null) return;
        for (File file : files) {
            try (InputStream in = new FileInputStream(file)) {
                XmlPullParser parser = Xml.newPullParser();
                parser.setInput(in, null);
                for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT;
                     event = parser.next()) {
                    if (event != XmlPullParser.START_TAG) continue;
                    String tag = parser.getName();
                    String type = "item".equals(tag) ? parser.getAttributeValue(null, "type") : tag;
                    String name = parser.getAttributeValue(null, "name");
                    if (name == null || type == null) continue;
                    String text = parser.next() == XmlPullParser.TEXT ? parser.getText().trim() : "";
                    if ("color".equals(type)) {
                        // Resolved once every folder is read: references may point anywhere
                        colors.put(name + '|' + config, text);
                        continue;
                    }
                    String[] item = encode(type, name, text, config);
                    if (item != null) {
                        items.put(type + '/' + name + '|' + config, item);
                    } else if ("bool".equals(type) || "integer".equals(type)) {
                        // A variant's reference overrides the base value: the module applies it
                        items.remove(type + '/' + name + '|' + config);
                        skipped.add(type + '/' + name);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Skipping " + file + ": " + e);
            }
        }
    }

    /**
     * @return {target, type, value, config}, or null when the value cannot be fabricated
     */
    private static String[] encode(String type, String name, String text, String config) {
        try {
            switch (type) {
                case "color":
                    Integer argb = parseColor(text);
                    return argb == null ? null : colorItem(name, argb, config);
                case "bool":
                    if (!text.equals("true") && !text.equals("false")) return null;
                    return new String[]{"bool/" + name, String.valueOf(TYPE_INT_BOOLEAN),
                            text.equals("true") ? "0xffffffff" : "0x00000000", config};
                case "integer":
                    return new String[]{"integer/" + name, String.valueOf(TYPE_INT_DEC),
                            String.format("0x%08x", Integer.decode(text)), config};
                default:
                    return null;
            }
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
