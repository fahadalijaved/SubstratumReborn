/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.util.compilers;

import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Makes themes written for older releases compile on this one (self-healing compile).
 * <p>
 * aapt fails a whole overlay when a single line references a resource the system no longer has
 * (a framework style One UI 9 dropped, an app's own drawable that moved). This reads aapt's
 * errors and removes only the offending element from the private work copy of the theme
 * ({@code cache/SubstratumBuilder}), then the overlay is compiled again, up to
 * {@value #MAX_ROUNDS} rounds, stopping as soon as a round prunes nothing new:
 * <ul>
 * <li>an {@code <item>} of a {@code <style>} that names a missing value: that item;</li>
 * <li>a value ({@code <color>}, {@code <drawable>}, {@code <dimen>}...) that is a missing
 * reference: that value;</li>
 * <li>a style whose parent is missing: the parent ({@code parent=""}); if it still fails, the
 * style (the LSPosed engine never applies styles, they only have to compile);</li>
 * <li>a layout or drawable attribute that references a missing resource: that attribute, or the
 * file when the attribute is one it cannot do without.</li>
 * </ul>
 * Every pruned line is recorded ({@link Pruned}) for the apply summary and the Theme Doctor.
 * Parsing and pruning are pure: unit tested.
 */
final class CompileRepair {

    static final int MAX_ROUNDS = 8;
    private static final String TAG = "SubstratumRepair";
    private static final Pattern LOCATED = Pattern.compile("^(/\\S+?\\.xml):(\\d+):\\s*error:?(.*)$");
    private static final Pattern AT_VALUE = Pattern.compile(
            "\\(at '([^']+)' with value '([^']*)'\\)");
    private static final Pattern PARENT = Pattern.compile(
            "Error retrieving parent for item: No resource found that matches the given name " +
                    "'([^']+)'");
    private static final Pattern AAPT2_RESOURCE = Pattern.compile(
            "resource '?([A-Za-z0-9_.:*/]+?)'? (?:\\(aka [^)]*\\) )?not found");
    private static final Pattern AAPT2_STYLE_ATTR = Pattern.compile(
            "style attribute '([^']+)' not found");
    private static final Pattern TAG_NAME = Pattern.compile("<([\\w:.-]+)");
    private static final Pattern NAME_ATTR = Pattern.compile("\\bname\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern PARENT_ATTR = Pattern.compile("\\sparent\\s*=\\s*\"([^\"]*)\"");

    /**
     * Attributes a file cannot do without, by resource folder: removing them would break it at
     * inflation, so the file goes instead
     */
    private static final Map<String, Set<String>> REQUIRED = new LinkedHashMap<>();

    static {
        REQUIRED.put("layout", new HashSet<>(Arrays.asList("layout_width", "layout_height")));
        REQUIRED.put("color", new HashSet<>(Collections.singletonList("color")));
        Set<String> drawable = new HashSet<>(Arrays.asList("pathData", "src", "drawable",
                "viewportWidth", "viewportHeight", "width", "height", "animation"));
        REQUIRED.put("drawable", drawable);
        REQUIRED.put("mipmap", drawable);
    }

    private CompileRepair() {
    }

    /**
     * One aapt error that points at a line
     */
    static final class Problem {
        final String file;
        final int line;
        /**
         * The attribute or resource name aapt names, or null
         */
        final String attr;
        /**
         * The reference that does not resolve, or null
         */
        final String ref;
        /**
         * A style's parent is missing
         */
        final boolean parent;

        Problem(String file, int line, String attr, String ref, boolean parent) {
            this.file = file;
            this.line = line;
            this.attr = attr;
            this.ref = ref;
            this.parent = parent;
        }
    }

    /**
     * One line of the theme left out of the build
     */
    static final class Pruned {
        /**
         * The file within the overlay ({@code values/styles.xml})
         */
        final String file;
        /**
         * What was removed: a value's or style item's name, an attribute, or the file
         */
        final String name;
        /**
         * The reference this system no longer has, or null
         */
        final String ref;

        Pruned(String file, String name, String ref) {
            this.file = file;
            this.name = name;
            this.ref = ref;
        }

        @Override
        public String toString() {
            return file + ": " + name + (ref == null || ref.isEmpty() ? "" : " (" + ref + ')');
        }
    }

    /**
     * aapt's errors that point at a line of a file. Pure.
     */
    static List<Problem> parse(List<String> errors) {
        List<Problem> problems = new ArrayList<>();
        for (String raw : errors) {
            Matcher m = LOCATED.matcher(raw.trim());
            if (!m.find()) continue;
            String file = m.group(1);
            int line = Integer.parseInt(m.group(2));
            String text = m.group(3);
            Matcher parent = PARENT.matcher(text);
            if (parent.find()) {
                problems.add(new Problem(file, line, null, parent.group(1), true));
                continue;
            }
            Matcher at = AT_VALUE.matcher(text);
            if (at.find()) {
                problems.add(new Problem(file, line, at.group(1), at.group(2), false));
                continue;
            }
            Matcher styleAttr = AAPT2_STYLE_ATTR.matcher(text);
            if (styleAttr.find()) {
                problems.add(new Problem(file, line, styleAttr.group(1), null, false));
                continue;
            }
            Matcher missing = AAPT2_RESOURCE.matcher(text);
            if (missing.find()) {
                problems.add(new Problem(file, line, null, missing.group(1), false));
                continue;
            }
            problems.add(new Problem(file, line, null, null, false));
        }
        return problems;
    }

    /**
     * @return Number of entries, attributes or files removed; 0 means nothing could be repaired
     */
    static int repair(List<String> aaptErrors) {
        return repair(aaptErrors, null);
    }

    /**
     * @param out Receives what was pruned, may be null
     * @return Number of entries, attributes or files removed; 0 means nothing could be repaired
     */
    static int repair(List<String> aaptErrors, List<Pruned> out) {
        Map<String, List<Problem>> byFile = new LinkedHashMap<>();
        for (Problem p : parse(aaptErrors)) {
            byFile.computeIfAbsent(p.file, k -> new ArrayList<>()).add(p);
        }
        int repaired = 0;
        for (Map.Entry<String, List<Problem>> entry : byFile.entrySet()) {
            File file = new File(entry.getKey());
            if (!file.isFile()) continue;
            String folder = file.getParentFile() != null ? file.getParentFile().getName() : "";
            String label = folder + '/' + file.getName();
            try {
                List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
                List<Pruned> pruned = new ArrayList<>();
                List<String> result = prune(lines, folder, entry.getValue(), label, pruned);
                if (result == null) {
                    if (file.delete()) {
                        Log.w(TAG, "Dropped " + file);
                        if (out != null) out.add(new Pruned(label, "(file)", firstRef(entry.getValue())));
                        repaired++;
                    }
                } else if (!pruned.isEmpty()) {
                    Files.write(file.toPath(), result, StandardCharsets.UTF_8);
                    Log.w(TAG, "Removed " + pruned.size() + " entries from " + file);
                    if (out != null) out.addAll(pruned);
                    repaired += pruned.size();
                }
            } catch (IOException | RuntimeException e) {
                Log.w(TAG, "Could not repair " + file, e);
            }
        }
        return repaired;
    }

    private static String firstRef(List<Problem> problems) {
        for (Problem p : problems) if (p.ref != null) return p.ref;
        return null;
    }

    /**
     * Remove what the problems point at from one file's lines. Pure.
     *
     * @param folder The resource folder ({@code values-v27}, {@code layout}...)
     * @param out    Receives what was pruned
     * @return The new lines (line numbers kept: attributes are cut out of their line), or null
     * when the file has to go
     */
    static List<String> prune(List<String> lines, String folder, List<Problem> problems,
                              String label, List<Pruned> out) {
        String type = folder.contains("-") ? folder.substring(0, folder.indexOf('-')) : folder;
        String[] text = lines.toArray(new String[0]);
        boolean[] drop = new boolean[text.length];
        for (Problem p : problems) {
            int index = Math.min(p.line, text.length) - 1;
            if (index < 0) continue;
            if (type.equals("values")) {
                pruneValue(text, drop, index, p, label, out);
            } else if (!pruneAttribute(text, index, p, type, label, out)) {
                return null;
            }
        }
        List<String> kept = new ArrayList<>(text.length);
        for (int i = 0; i < text.length; i++) if (!drop[i]) kept.add(text[i]);
        return kept;
    }

    private static void pruneValue(String[] text, boolean[] drop, int index, Problem p,
                                   String label, List<Pruned> out) {
        int start = elementStart(text, index);
        if (start < 0 || drop[start]) return;
        String tag = tagName(text[start]);
        if (tag.equals("resources")) return;
        String name = attr(text, start, NAME_ATTR);
        String parentValue = tag.equals("style") ? attr(text, start, PARENT_ATTR) : null;
        boolean parentMissing = p.parent ||
                (p.attr == null && p.ref != null && parentValue != null &&
                        lastSegment(parentValue).equals(lastSegment(p.ref)));
        if (parentMissing && tag.equals("style")) {
            int end = startTagEnd(text, start);
            for (int i = start; i <= end; i++) {
                Matcher m = PARENT_ATTR.matcher(text[i]);
                if (!m.find()) continue;
                if (m.group(1).isEmpty()) break; // no parent already: the style goes
                text[i] = text[i].substring(0, m.start()) + " parent=\"\"" +
                        text[i].substring(m.end());
                out.add(new Pruned(label, "style " + name + " parent", p.ref));
                return;
            }
            if (!hasParentAttr(text, start, end)) {
                // An implicit (dotted) parent: say there is none
                text[start] = text[start].replaceFirst("<style", "<style parent=\"\"");
                out.add(new Pruned(label, "style " + name + " parent", p.ref));
                return;
            }
            markElement(text, drop, start);
            out.add(new Pruned(label, "style " + name, p.ref));
            return;
        }
        if (tag.equals("style") && p.attr != null) {
            // aapt pointed at the style: drop only the item that names the attribute
            int end = elementEnd(text, start, tag);
            for (int i = start + 1; i <= end; i++) {
                if (drop[i] || !tagName(text[i]).equals("item")) continue;
                if (p.attr.equals(attr(text, i, NAME_ATTR))) {
                    markElement(text, drop, i);
                    out.add(new Pruned(label, name + '/' + p.attr, p.ref));
                    return;
                }
            }
        }
        String style = tag.equals("item") ? enclosingStyle(text, start) : null;
        markElement(text, drop, start);
        String what = style != null ? style + '/' + (name != null ? name : p.attr) :
                (name != null ? name : p.attr != null ? p.attr : tag);
        out.add(new Pruned(label, what, p.ref));
    }

    private static String lastSegment(String ref) {
        String r = ref.trim();
        int cut = Math.max(r.lastIndexOf('/'), r.lastIndexOf(':'));
        return cut < 0 ? r : r.substring(cut + 1);
    }

    private static boolean hasParentAttr(String[] text, int start, int end) {
        for (int i = start; i <= end; i++) if (PARENT_ATTR.matcher(text[i]).find()) return true;
        return false;
    }

    /**
     * @return False when the file has to go
     */
    private static boolean pruneAttribute(String[] text, int index, Problem p, String type,
                                          String label, List<Pruned> out) {
        if (p.attr == null && p.ref == null) return false;
        int start = elementStart(text, index);
        if (start < 0) start = index;
        int end = startTagEnd(text, start);
        // The attribute aapt names (local name), preferably with the missing reference as value
        String local = p.attr == null ? null :
                p.attr.contains(":") ? p.attr.substring(p.attr.indexOf(':') + 1) : p.attr;
        int[] found = null;
        if (local != null) {
            found = findAttribute(text, start, end, local, p.ref);
            if (found == null) found = findAttribute(text, start, end, local, null);
        } else {
            found = findReference(text, start, end, p.ref);
        }
        if (found == null) return false;
        String attribute = text[found[0]].substring(found[1], found[2]).trim();
        String attrLocal = attribute.substring(0, attribute.indexOf('=')).trim();
        if (attrLocal.contains(":")) attrLocal = attrLocal.substring(attrLocal.indexOf(':') + 1);
        Set<String> required = REQUIRED.get(type);
        if (required != null && required.contains(attrLocal)) return false;
        text[found[0]] = text[found[0]].substring(0, found[1]) + text[found[0]].substring(found[2]);
        out.add(new Pruned(label, attribute.substring(0, attribute.indexOf('=')).trim(), p.ref));
        return true;
    }

    /**
     * {line, from, to} of {@code local="..."} (any namespace) in the start tag, or null
     */
    private static int[] findAttribute(String[] text, int start, int end, String local,
                                       String value) {
        Pattern pattern = Pattern.compile("\\s+(?:[\\w-]+:)?" + Pattern.quote(local) +
                "\\s*=\\s*\"" + (value == null ? "[^\"]*" : Pattern.quote(value)) + '"');
        for (int i = start; i <= end; i++) {
            Matcher m = pattern.matcher(text[i]);
            if (m.find()) return new int[]{i, m.start(), m.end()};
        }
        return null;
    }

    /**
     * {line, from, to} of an attribute whose value references {@code ref} (aapt2's
     * {@code pkg:type/name} or {@code type/name}), or null
     */
    private static int[] findReference(String[] text, int start, int end, String ref) {
        String pkg = null;
        String typeName = ref;
        int colon = ref.indexOf(':');
        if (colon > 0) {
            pkg = ref.substring(0, colon).replace("*", "");
            typeName = ref.substring(colon + 1);
        }
        Pattern pattern = Pattern.compile("\\s+[\\w:-]+\\s*=\\s*\"[@?]\\*?" +
                (pkg == null ? "" : "(?:" + Pattern.quote(pkg) + ":)?") +
                Pattern.quote(typeName) + '"');
        for (int i = start; i <= end; i++) {
            Matcher m = pattern.matcher(text[i]);
            if (m.find()) return new int[]{i, m.start(), m.end()};
        }
        return null;
    }

    private static void markElement(String[] text, boolean[] drop, int start) {
        int end = elementEnd(text, start, tagName(text[start]));
        for (int i = start; i <= end; i++) drop[i] = true;
    }

    /**
     * The line where the element covering {@code index} starts, or -1
     */
    private static int elementStart(String[] text, int index) {
        for (int i = index; i >= 0; i--) if (isElementStart(text[i])) return i;
        return -1;
    }

    private static int startTagEnd(String[] text, int start) {
        for (int i = start; i < text.length; i++) if (text[i].contains(">")) return i;
        return start;
    }

    private static int elementEnd(String[] text, int start, String tag) {
        // A start tag that closes itself
        int tagEnd = startTagEnd(text, start);
        String head = String.join("\n", Arrays.copyOfRange(text, start, tagEnd + 1));
        int close = head.indexOf('>');
        if (close > 0 && head.charAt(close - 1) == '/') return tagEnd;
        for (int i = start; i < text.length; i++) {
            if (text[i].contains("</" + tag + ">")) return i;
        }
        return tagEnd;
    }

    private static String enclosingStyle(String[] text, int item) {
        for (int i = item - 1; i >= 0; i--) {
            String t = text[i].trim();
            if (t.startsWith("</style")) return null;
            if (t.startsWith("<style")) return attr(text, i, NAME_ATTR);
        }
        return null;
    }

    private static String attr(String[] text, int start, Pattern pattern) {
        int end = startTagEnd(text, start);
        for (int i = start; i <= end; i++) {
            Matcher m = pattern.matcher(text[i]);
            if (m.find()) return m.group(1);
        }
        return null;
    }

    private static boolean isElementStart(String line) {
        String t = line.trim();
        return t.startsWith("<") && !t.startsWith("</") && !t.startsWith("<!--") &&
                !t.startsWith("<?");
    }

    private static String tagName(String line) {
        Matcher m = TAG_NAME.matcher(line.trim());
        return m.find() ? m.group(1) : "";
    }
}
