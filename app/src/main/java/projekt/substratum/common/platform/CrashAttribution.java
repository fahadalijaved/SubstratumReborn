/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.common.platform;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which theme part a crash comes from. The module records, in each process it changes, what it
 * forwarded; when the process dies of an uncaught exception, the stack text is matched against
 * that: resource names in the text ({@code Drawable <overlay package>:drawable/<name>}, or the
 * target's own name of a forwarded resource), a class cast from a forwarded drawable's class, and
 * forwarded names the text mentions. The overlay with the most evidence is the one; the app then
 * offers to pause that part ({@link XposedBackend#hold}). Pure: unit tested.
 */
public final class CrashAttribution {

    private static final Pattern RESOURCE = Pattern.compile(
            "([A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)*):([a-z]+)/([A-Za-z0-9_.]+)");
    private static final Pattern CAST = Pattern.compile(
            "(?:class )?([A-Za-z0-9_.$]+) cannot be cast to (?:class )?([A-Za-z0-9_.$]+)");

    private CrashAttribution() {
    }

    /**
     * One resource the module forwarded in a process
     */
    public static final class Forwarded {
        public final String overlay;
        public final String overlayPackage;
        /**
         * {@code type/name}
         */
        public final String resource;
        public final String replacementClass;
        public final String stockClass;

        public Forwarded(String overlay, String overlayPackage, String resource,
                         String replacementClass, String stockClass) {
            this.overlay = overlay;
            this.overlayPackage = overlayPackage;
            this.resource = resource;
            this.replacementClass = replacementClass;
            this.stockClass = stockClass;
        }
    }

    /**
     * The overlay a crash is put down to, and the resources that point at it
     */
    public static final class Result {
        public final String overlay;
        public final Set<String> names;

        Result(String overlay, Set<String> names) {
            this.overlay = overlay;
            this.names = names;
        }
    }

    /**
     * @param stackText The crash's stack trace, causes included
     * @return The overlay with the most evidence, or null when nothing points at one
     */
    public static Result attribute(String stackText, Collection<Forwarded> forwarded) {
        if (stackText == null || forwarded == null || forwarded.isEmpty()) return null;
        Map<String, Integer> score = new LinkedHashMap<>();
        Map<String, Set<String>> names = new LinkedHashMap<>();

        // 1. Resource names: the overlay's own package, or the target's name of a forwarded one
        Matcher m = RESOURCE.matcher(stackText);
        while (m.find()) {
            String pkg = m.group(1);
            String resource = m.group(2) + '/' + m.group(3);
            for (Forwarded f : forwarded) {
                if (pkg.equals(f.overlayPackage)) {
                    hit(score, names, f.overlay, resource, 3);
                } else if (resource.equals(f.resource)) {
                    hit(score, names, f.overlay, f.resource, 2);
                }
            }
        }
        // 2. A class cast from a forwarded drawable's class to the app's own
        Matcher cast = CAST.matcher(stackText);
        while (cast.find()) {
            String from = cast.group(1);
            String to = cast.group(2);
            for (Forwarded f : forwarded) {
                if (!from.equals(f.replacementClass)) continue;
                boolean stock = to.equals(f.stockClass);
                if (f.stockClass != null && !stock) continue;
                hit(score, names, f.overlay, f.resource, stock ? 3 : 1);
            }
        }
        // 3. Forwarded names the text mentions
        for (Forwarded f : forwarded) {
            int slash = f.resource.indexOf('/');
            String name = f.resource.substring(slash + 1);
            if (name.length() < 5) continue;
            if (Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(name) + "(?![A-Za-z0-9_])")
                    .matcher(stackText).find()) {
                hit(score, names, f.overlay, f.resource, 1);
            }
        }
        String best = null;
        int bestScore = 0;
        for (Map.Entry<String, Integer> e : score.entrySet()) {
            if (e.getValue() > bestScore) {
                best = e.getKey();
                bestScore = e.getValue();
            }
        }
        return best == null ? null : new Result(best, names.get(best));
    }

    private static void hit(Map<String, Integer> score, Map<String, Set<String>> names,
                            String overlay, String resource, int weight) {
        score.merge(overlay, weight, Integer::sum);
        names.computeIfAbsent(overlay, k -> new LinkedHashSet<>()).add(resource);
    }

    /**
     * What the module wrote to {@link XposedBackend#CRASH_FILE}
     */
    public static final class Recorded {
        public String overlay;
        public final List<String> names = new ArrayList<>();
        public long time;
        public String error;

        /**
         * The file's content for a result
         */
        public static String format(Result result, long time, String error) {
            StringBuilder out = new StringBuilder();
            out.append("overlay=").append(result.overlay).append('\n');
            out.append("names=").append(String.join(",", result.names)).append('\n');
            out.append("time=").append(time).append('\n');
            if (error != null) {
                String line = error.replace('\n', ' ').replace('\r', ' ');
                if (line.length() > 300) line = line.substring(0, 300);
                out.append("error=").append(line).append('\n');
            }
            return out.toString();
        }

        /**
         * @return The record, or null when the text holds none
         */
        public static Recorded parse(String text) {
            if (text == null) return null;
            Recorded r = new Recorded();
            for (String line : text.split("\n")) {
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                switch (key) {
                    case "overlay":
                        r.overlay = value.isEmpty() ? null : value;
                        break;
                    case "names":
                        for (String name : value.split(",")) {
                            if (!name.trim().isEmpty()) r.names.add(name.trim());
                        }
                        break;
                    case "time":
                        try {
                            r.time = Long.parseLong(value);
                        } catch (NumberFormatException ignored) {
                            // unknown
                        }
                        break;
                    case "error":
                        r.error = value;
                        break;
                    default:
                        break;
                }
            }
            return r.overlay == null ? null : r;
        }
    }
}
