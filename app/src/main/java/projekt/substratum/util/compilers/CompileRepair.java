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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Makes themes written for older One UI releases compile on newer ones.
 * <p>
 * aapt fails a whole overlay when a single file references a resource the system no longer has
 * (for example a framework layout or dimen removed in Android 17). This reads aapt's
 * "file:line: error:" lines and removes just the offending entry from values files, or drops
 * the offending layout/drawable file, so the rest of the overlay still builds.
 */
final class CompileRepair {

    private static final String TAG = "SubstratumRepair";
    private static final Pattern ERROR = Pattern.compile("^(/\\S+?\\.xml):(\\d+): error:");

    private CompileRepair() {
    }

    /**
     * @return Number of entries or files removed; 0 means nothing could be repaired
     */
    static int repair(List<String> aaptErrors) {
        Map<String, TreeSet<Integer>> byFile = new HashMap<>();
        for (String line : aaptErrors) {
            Matcher m = ERROR.matcher(line.trim());
            if (!m.find()) continue;
            byFile.computeIfAbsent(m.group(1), k -> new TreeSet<>())
                    .add(Integer.parseInt(m.group(2)));
        }
        int repaired = 0;
        for (Map.Entry<String, TreeSet<Integer>> entry : byFile.entrySet()) {
            File file = new File(entry.getKey());
            if (!file.isFile()) continue;
            try {
                if (file.getParentFile() != null &&
                        file.getParentFile().getName().startsWith("values")) {
                    repaired += removeEntries(file, entry.getValue());
                } else if (file.delete()) {
                    Log.w(TAG, "Dropped " + file);
                    repaired++;
                }
            } catch (IOException e) {
                Log.w(TAG, "Could not repair " + file, e);
            }
        }
        return repaired;
    }

    /**
     * Remove the top-level resource elements (children of resources) covering the given lines.
     */
    private static int removeEntries(File file, TreeSet<Integer> errorLines) throws IOException {
        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        boolean[] drop = new boolean[lines.size()];
        int removed = 0;
        for (int errorLine : errorLines) {
            int index = Math.min(errorLine, lines.size()) - 1;
            if (index < 0) continue;
            // Walk up to the line where this top-level element starts
            int start = index;
            while (start > 0 && !isElementStart(lines.get(start))) start--;
            if (lines.get(start).trim().startsWith("<resources")) continue;
            // ...and down to where it ends
            int end = start;
            String tag = tagName(lines.get(start));
            while (end < lines.size() - 1 && !closes(lines.get(end), tag)) end++;
            for (int i = start; i <= end; i++) drop[i] = true;
            removed++;
        }
        if (removed == 0) return 0;
        List<String> kept = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) if (!drop[i]) kept.add(lines.get(i));
        Files.write(file.toPath(), kept, StandardCharsets.UTF_8);
        Log.w(TAG, "Removed " + removed + " entries from " + file);
        return removed;
    }

    private static boolean isElementStart(String line) {
        String t = line.trim();
        return t.startsWith("<") && !t.startsWith("</") && !t.startsWith("<!--") &&
                !t.startsWith("<?");
    }

    private static String tagName(String line) {
        Matcher m = Pattern.compile("<([\\w:-]+)").matcher(line);
        return m.find() ? m.group(1) : "";
    }

    private static boolean closes(String line, String tag) {
        String t = line.trim();
        return t.endsWith("/>") || t.contains("</" + tag + ">");
    }
}
