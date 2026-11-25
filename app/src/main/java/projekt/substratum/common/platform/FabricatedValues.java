/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.common.platform;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
        Map<String, String[]> items = new LinkedHashMap<>();
        List<File> folders = new ArrayList<>();
        collectValueFolders(workArea, 0, folders);
        folders.sort((a, b) -> Boolean.compare(isVariant(a), isVariant(b)));
        for (File folder : folders) readValues(folder, items);
        return registerItems(context, target, name, new ArrayList<>(items.values()));
    }

    /**
     * Register prepared items {target, type (decimal), value, config} as a fabricated overlay.
     *
     * @return Number of values registered, 0 if there were none or it failed
     */
    public static int registerItems(Context context, String target, String name,
                                    List<String[]> items) {
        if (items.isEmpty()) return 0;

        File definition = new File(context.getCacheDir(), name + ".xml");
        try (FileWriter writer = new FileWriter(definition)) {
            writer.write("<overlay>\n");
            for (String[] item : items) {
                writer.write(String.format(Locale.US,
                        "  <item target=\"%s\" type=\"0x%02x\" value=\"%s\"%s />\n",
                        item[0], Integer.parseInt(item[1]), item[2],
                        item[3].isEmpty() ? "" : " config=\"" + item[3] + "\""));
            }
            writer.write("</overlay>\n");
        } catch (IOException e) {
            Log.e(TAG, "Could not write the definition", e);
            return 0;
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
        if (out == null || !out.trim().endsWith("rc=0") || out.contains("Error")) {
            Log.e(TAG, "fabricate failed for " + name + ": " + out);
            // The folder may have gone: set it up again next time
            dirReady = false;
            return 0;
        }
        return items.size();
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

    private static void readValues(File folder, Map<String, String[]> items) {
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
                    String[] item = encode(type, name, text, config);
                    if (item != null) items.put(type + '/' + name + '|' + config, item);
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
                    if (!text.startsWith("#")) return null; // references stay with the module
                    return new String[]{"color/" + name, String.valueOf(TYPE_INT_COLOR_ARGB8),
                            String.format("0x%08x", Color.parseColor(expand(text))), config};
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

    /**
     * #RGB and #ARGB to the long forms Color.parseColor accepts.
     */
    private static String expand(String color) {
        if (color.length() != 4 && color.length() != 5) return color;
        StringBuilder out = new StringBuilder("#");
        for (int i = 1; i < color.length(); i++) out.append(color.charAt(i)).append(color.charAt(i));
        return out.toString();
    }
}
