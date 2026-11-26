/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.common.platform;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import projekt.substratum.util.helpers.Root;

/**
 * Registers what the engine prepared for a batch of apps with ONE root call: every definition and
 * module list is written to the cache folder first, then a single shell script copies them into
 * place (owner, mode and SELinux label per file), fabricates and enables each overlay, installs
 * each module list, cleans up apps that lose their colors, restarts module apps that are safe to
 * restart, and lists the AMOLED overlays at the end. Each step prints a marker:
 * {@code R|<overlay>|<rc>} (overlay), {@code M|<pkg>|<rc>} (module list), {@code C|<pkg>|<rc>}
 * (clean up) and {@code E|<pkg>|<rc>} (overlay enabled again), plus {@code S|<pkg>|1} for an app
 * a gentle run left running, then a separator and the list.
 */
final class AmoledCommit {

    private static final String TAG = "RebornAmoled";
    private static final String LABEL = "u:object_r:system_data_file:s0";
    private static final String FOLDER = "amoled-commit";
    private static final String LIST = "@@reborn-overlay-list@@";

    enum Type {
        /**
         * Register and enable the overlay (and drop module colors)
         */
        OVERLAY,
        /**
         * Install the module list (and drop the overlay)
         */
        MODULE,
        /**
         * No colors any more: drop the overlay, its definition and the module list
         */
        CLEAN,
        /**
         * Enable the overlay again (its colors could not be read: it keeps what it had)
         */
        ENABLE
    }

    static final class Op {
        final String pkg;
        final Type type;
        /**
         * The definition (OVERLAY) or the module list (MODULE)
         */
        final String content;
        /**
         * Force-stop the app once its module colors changed (only when safe to restart)
         */
        final boolean restart;

        Op(String pkg, Type type, String content, boolean restart) {
            this.pkg = pkg;
            this.type = type;
            this.content = content;
            this.restart = restart;
        }
    }

    static final class Outcome {
        /**
         * Marker exit codes by package; missing when the step never ran
         */
        final Map<String, Integer> codes = new HashMap<>();
        /**
         * {@link SystemAmoled#parseOverlayStates} of the list at the end
         */
        Map<String, Character> overlays = new HashMap<>();
        /**
         * Apps a gentle run did not force-stop because they were running: "restart needed"
         */
        final Set<String> running = new HashSet<>();
        /**
         * The script ran to the end
         */
        boolean complete;

        boolean ok(String pkg) {
            Integer code = codes.get(pkg);
            return code != null && code == 0;
        }
    }

    private AmoledCommit() {
    }

    /**
     * Run a batch (root). Blocking.
     *
     * @param gentle Nothing is force-stopped: an app to restart that is running prints
     *               {@code S|<pkg>|1} instead ({@link Outcome#running}), one that is not running
     *               loads its colors when it starts
     */
    static Outcome run(Context context, List<Op> ops, boolean gentle) {
        Outcome outcome = new Outcome();
        if (ops.isEmpty()) {
            outcome.complete = true;
            return outcome;
        }
        File dir = new File(context.getCacheDir(), FOLDER);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        List<File> written = new ArrayList<>();
        File script = new File(dir, "commit.sh");
        try {
            StringBuilder s = new StringBuilder();
            s.append("D='").append(FabricatedValues.DIR).append("'\n");
            s.append("X='").append(XposedBackend.DIR).append("'\n");
            s.append("L='").append(LABEL).append("'\n");
            s.append("mkdir -p \"$D\" && chown system:system \"$D\" && chcon \"$L\" \"$D\"\n");
            s.append("mkdir -p \"$X\" && chmod 755 \"$X\"\n");
            for (Op op : ops) {
                String pkg = op.pkg;
                if (!pkg.matches("[A-Za-z0-9_.]+")) continue;
                String name = pkg + SystemAmoled.SUFFIX;
                String ids = "\"$X/" + pkg + XposedBackend.AMOLED_IDS + "\"";
                String def = "\"$D/" + name + ".xml\"";
                switch (op.type) {
                    case OVERLAY: {
                        File source = write(dir, name + ".xml", op.content, written);
                        s.append("rm -f ").append(ids).append('\n');
                        s.append("cp -f '").append(source.getAbsolutePath()).append("' ")
                                .append(def).append(" && chown system:system ").append(def)
                                .append(" && chmod 644 ").append(def).append(" && chcon \"$L\" ")
                                .append(def).append(" && cmd overlay fabricate --target ")
                                .append(pkg).append(" --name ").append(name).append(" --file ")
                                .append(def).append(" >/dev/null 2>&1 && cmd overlay enable ")
                                .append("--user 0 com.android.shell:").append(name)
                                .append(" >/dev/null 2>&1; echo \"R|").append(pkg)
                                .append("|$?\"\n");
                        break;
                    }
                    case MODULE: {
                        File source = write(dir, pkg + XposedBackend.AMOLED_IDS, op.content,
                                written);
                        s.append(disable(name)).append("rm -f ").append(def).append('\n');
                        s.append("cp -f '").append(source.getAbsolutePath()).append("' ")
                                .append(ids).append(" && chmod 644 ").append(ids)
                                .append(" && restorecon ").append(ids)
                                .append(" 2>/dev/null; r=$?; echo \"M|").append(pkg)
                                .append("|$r\"\n");
                        if (op.restart) {
                            s.append("[ $r -eq 0 ] && { ").append(restart(pkg, gentle))
                                    .append("; }\n");
                        }
                        break;
                    }
                    case CLEAN:
                        s.append(disable(name)).append("rm -f ").append(def).append(' ')
                                .append(ids).append("; echo \"C|").append(pkg).append("|0\"\n");
                        if (op.restart) s.append(restart(pkg, gentle)).append('\n');
                        break;
                    case ENABLE:
                        s.append("cmd overlay enable --user 0 com.android.shell:").append(name)
                                .append(" >/dev/null 2>&1; echo \"E|").append(pkg)
                                .append("|$?\"\n");
                        break;
                    default:
                        break;
                }
            }
            s.append("echo '").append(LIST).append("'\ncmd overlay list 2>/dev/null | grep '")
                    .append(SystemAmoled.SUFFIX).append("'\ntrue\n");
            try (FileWriter writer = new FileWriter(script)) {
                writer.write(s.toString());
            }
        } catch (IOException e) {
            Log.e(TAG, "Could not write the commit script", e);
            cleanUp(written, script);
            return outcome;
        }
        // The one root call of the batch
        String out = Root.runCommand("sh '" + script.getAbsolutePath() + "'");
        cleanUp(written, script);
        if (out == null) {
            Log.e(TAG, "Commit of " + ops.size() + " apps failed: no root shell");
            return outcome;
        }
        int list = out.indexOf(LIST);
        String markers = list >= 0 ? out.substring(0, list) : out;
        for (String line : markers.split("\n")) {
            String[] parts = line.trim().split("\\|");
            if (parts.length != 3 || parts[0].length() != 1) continue;
            if ("S".equals(parts[0])) {
                outcome.running.add(parts[1]);
                continue;
            }
            try {
                outcome.codes.put(parts[1], Integer.parseInt(parts[2].trim()));
            } catch (NumberFormatException ignored) {
                // not a marker
            }
        }
        if (list >= 0) {
            outcome.complete = true;
            outcome.overlays = SystemAmoled.parseOverlayStates(out.substring(list));
        }
        return outcome;
    }

    /**
     * Restart an app so it loads its new module colors (only for apps safe to restart): a
     * force-stop, or in a gentle run (a silent re-theme after updates) nothing but a marker when
     * it is running, so the app in use is never closed and its playback never stopped.
     */
    private static String restart(String pkg, boolean gentle) {
        if (gentle) return "pidof " + pkg + " >/dev/null 2>&1 && echo \"S|" + pkg + "|1\"";
        return XposedBackend.forgetStartsCommand(pkg) + "; am force-stop " + pkg;
    }

    private static String disable(String name) {
        return "cmd overlay disable --user 0 com.android.shell:" + name + " >/dev/null 2>&1; ";
    }

    private static File write(File dir, String name, String content, List<File> written)
            throws IOException {
        File file = new File(dir, name);
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
        written.add(file);
        return file;
    }

    private static void cleanUp(List<File> written, File script) {
        //noinspection ResultOfMethodCallIgnored
        for (File file : written) file.delete();
        //noinspection ResultOfMethodCallIgnored
        script.delete();
    }
}
