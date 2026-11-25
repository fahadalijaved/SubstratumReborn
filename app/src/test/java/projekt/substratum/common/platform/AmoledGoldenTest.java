/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.common.platform;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Locks the colors "AMOLED for every app" computes, against the One UI 9 reference
 * (docs/oneui9/reference/&lt;pkg&gt;/values/color.csv): for every color, its dark-mode value
 * (night, else default, following references), then the rules exactly as compute() applies them.
 * One line per changed color, {@code name #stock -> #amoled rule}, in
 * src/test/resources/golden/&lt;pkg&gt;.pure.txt.
 * <p>
 * After an intended change: {@code ./gradlew :app:testDebugUnitTest -Dgolden.update=true}.
 */
public class AmoledGoldenTest {

    private static final int MAX_HOPS = 8;
    private static final String SUFFIX = ".pure.txt";

    @Test
    public void colorsMatchGoldenSnapshots() throws IOException {
        File reference = existing("../docs/oneui9/reference", "docs/oneui9/reference");
        File golden = existing("src/test/resources/golden", "app/src/test/resources/golden");
        boolean update = Boolean.parseBoolean(System.getProperty("golden.update", "false"));
        File[] packages = reference.listFiles(f -> new File(f, "values/color.csv").isFile());
        assertNotNull("No One UI reference at " + reference.getAbsolutePath(), packages);
        assertTrue("No packages in " + reference.getAbsolutePath(), packages.length > 0);
        Arrays.sort(packages);
        Map<String, Map<String, String>> android =
                table(new File(reference, "android/values/color.csv"));
        AmoledRules rules = AmoledRules.defaults();

        List<String> failures = new ArrayList<>();
        Set<String> names = new TreeSet<>();
        int lines = 0;
        for (File dir : packages) {
            String pkg = dir.getName();
            List<String> actual = snapshot(pkg, table(new File(dir, "values/color.csv")),
                    android, rules);
            lines += actual.size();
            File file = new File(golden, pkg + SUFFIX);
            names.add(file.getName());
            if (update) {
                //noinspection ResultOfMethodCallIgnored
                golden.mkdirs();
                Files.write(file.toPath(), (String.join("\n", actual) + "\n")
                        .getBytes(StandardCharsets.UTF_8));
                continue;
            }
            if (!file.isFile()) {
                failures.add(pkg + ": no " + file.getPath());
                continue;
            }
            List<String> expected = new ArrayList<>();
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                if (!line.isEmpty()) expected.add(line);
            }
            if (!expected.equals(actual)) failures.add(pkg + ": " + diff(expected, actual));
        }
        String[] existing = golden.list();
        if (existing != null) {
            for (String name : existing) {
                if (!name.endsWith(SUFFIX) || names.contains(name)) continue;
                if (update) {
                    //noinspection ResultOfMethodCallIgnored
                    new File(golden, name).delete();
                } else {
                    failures.add(name + ": no reference package for it");
                }
            }
        }
        System.out.println("AMOLED golden: " + packages.length + " packages, " + lines +
                " changed colors" + (update ? " (snapshots rewritten)" : ""));
        assertTrue(failures.size() + " AMOLED golden snapshots differ (if intended, run " +
                        "./gradlew :app:testDebugUnitTest -Dgolden.update=true):\n" +
                        String.join("\n", failures), failures.isEmpty());
    }

    /**
     * The changed colors of one package, as compute() decides them, sorted.
     */
    static List<String> snapshot(String pkg, Map<String, Map<String, String>> own,
                                 Map<String, Map<String, String>> android, AmoledRules rules) {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> entry : own.entrySet()) {
            String name = entry.getKey();
            Integer stock = resolve(dark(entry.getValue()), own, android, 0);
            if (stock == null) continue;
            AmoledPalette.Decision decision = AmoledPalette.forColor(pkg, name, stock, rules);
            if (decision.color == null || decision.color.intValue() == stock.intValue()) continue;
            lines.add(String.format(Locale.ROOT, "%s #%08x -> #%08x %s", name, stock,
                    decision.color, decision.rule));
        }
        Collections.sort(lines);
        return lines;
    }

    /**
     * A literal color, following {@code @color/x} in the same table and
     * {@code @android:color/x} or {@code @*android:color/x} in the framework's, up to 8 hops.
     * Null for anything else (attributes, numeric framework ids).
     */
    static Integer resolve(String value, Map<String, Map<String, String>> own,
                           Map<String, Map<String, String>> android, int hops) {
        if (value == null || hops > MAX_HOPS) return null;
        if (value.startsWith("#")) {
            try {
                return AmoledPalette.parseColor(value);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        Map<String, Map<String, String>> table;
        String name;
        if (value.startsWith("@color/")) {
            table = own;
            name = value.substring("@color/".length());
        } else if (value.startsWith("@android:color/")) {
            table = android;
            name = value.substring("@android:color/".length());
        } else if (value.startsWith("@*android:color/")) {
            table = android;
            name = value.substring("@*android:color/".length());
        } else {
            return null;
        }
        Map<String, String> configs = table.get(name);
        if (configs == null) return null;
        return resolve(dark(configs), table, android, hops + 1);
    }

    private static String dark(Map<String, String> configs) {
        return configs.containsKey("night") ? configs.get("night") : configs.get("default");
    }

    /**
     * name -> configuration -> value
     */
    static Map<String, Map<String, String>> table(File csv) throws IOException {
        Map<String, Map<String, String>> table = new TreeMap<>();
        List<String> lines = Files.readAllLines(csv.toPath(), StandardCharsets.UTF_8);
        for (int i = 1; i < lines.size(); i++) {
            String[] parts = lines.get(i).trim().split(",", 3);
            if (parts.length < 3) continue;
            table.computeIfAbsent(parts[0], k -> new HashMap<>()).put(parts[1], parts[2].trim());
        }
        return table;
    }

    private static String diff(List<String> expected, List<String> actual) {
        Set<String> gone = new LinkedHashSet<>(expected);
        gone.removeAll(actual);
        Set<String> added = new LinkedHashSet<>(actual);
        added.removeAll(expected);
        StringBuilder out = new StringBuilder();
        out.append(gone.size()).append(" lines gone, ").append(added.size()).append(" new");
        int shown = 0;
        for (String line : gone) {
            if (shown++ == 5) break;
            out.append("\n    - ").append(line);
        }
        shown = 0;
        for (String line : added) {
            if (shown++ == 5) break;
            out.append("\n    + ").append(line);
        }
        return out.toString();
    }

    private static File existing(String path, String fallback) {
        File file = new File(path);
        if (file.exists() || !new File(fallback).exists()) return file;
        return new File(fallback);
    }
}
