/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.common.platform;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import projekt.substratum.util.helpers.Root;

/**
 * Where a classic overlay's plain values go: the shell's fabricated overlay, or the LSPosed module.
 * <p>
 * idmap2 refuses a fabricated overlay owned by the shell as a whole when a single entry is
 * outside a PUBLIC {@code <overlayable>} of a target that is not signed like the shell
 * ({@code cmd overlay list} then shows {@code ---}, STATE_NO_IDMAP, "target resource has no
 * overlayable declaration"). Such a classic overlay used to lose all its colours: the index told
 * the module to leave them to the overlay. Now:
 * <ul>
 * <li>at build time, the entries {@link ArscOverlayable#refusedByShell} predicts, and those idmap2
 * refused for the same app version before, stay out of the definition and become {@code keep:}
 * lines the module applies by name (the AMOLED engine's prediction and learning);</li>
 * <li>after enabling, an overlay that is refused anyway is registered again with idmap2's own
 * rejections left out ({@link #healRefused}); what it refused is learned and moved to the module.
 * If nothing registers, the whole overlay goes to the module.</li>
 * </ul>
 */
public final class ClassicRoute {

    private static final String TAG = "SubstratumClassic";
    private static final String SHELL_PREFIX = "com.android.shell:";
    private static final Pattern ITEM = Pattern.compile(
            "<item\\s+target=\"([^\"]+)\"\\s+type=\"0x([0-9a-fA-F]+)\"\\s+value=\"([^\"]*)\"" +
                    "(?:\\s+config=\"([^\"]*)\")?\\s*/>");
    private static final Pattern STATE = Pattern.compile(
            "(?:\\[(.)\\]|---) com\\.android\\.shell:(\\S+)");

    private ClassicRoute() {
    }

    // Pure parts (unit tested)

    /**
     * The items a shell overlay may carry: every configuration of a refused {@code type/name} is
     * left out, and the name goes to {@code toModule}. Pure.
     */
    static List<String[]> withoutRefused(List<String[]> items, Set<String> refused,
                                         Set<String> toModule) {
        if (refused == null || refused.isEmpty()) return items;
        List<String[]> kept = new ArrayList<>(items.size());
        for (String[] item : items) {
            if (refused.contains(item[0])) {
                if (toModule != null) toModule.add(item[0]);
            } else {
                kept.add(item);
            }
        }
        return kept;
    }

    /**
     * A stored overlay's index. Pure.
     *
     * @param resources Every {@code type/name} the overlay carries
     * @param keep      Those the module applies although the overlay is fabricated
     */
    static String index(String target, String overlayPackage, boolean fabricated,
                        boolean darkOnly, List<String> resources, Set<String> keep) {
        StringBuilder out = new StringBuilder();
        out.append("target=").append(target).append('\n');
        out.append("package=").append(overlayPackage).append('\n');
        // Tells the module to leave colors, bools and integers to the fabricated overlay
        if (fabricated) out.append(XposedBackend.FABRICATED).append('\n');
        // ...and to apply what it carries only while the configuration is night
        if (darkOnly) out.append(XposedBackend.DARK_ONLY).append('\n');
        for (String resource : resources) {
            if (fabricated && keep != null && keep.contains(resource)) {
                out.append(XposedBackend.KEEP);
            }
            out.append(resource).append('\n');
        }
        return out.toString();
    }

    /**
     * An index with more names handed to the module: their lines get {@code keep:}, and with
     * {@code allToModule} the fabricated marker goes (the module then applies everything). Pure.
     */
    static String rerouted(String index, Set<String> keep, boolean allToModule) {
        StringBuilder out = new StringBuilder();
        for (String line : index.split("\n", -1)) {
            if (line.isEmpty()) continue;
            if (allToModule && line.equals(XposedBackend.FABRICATED)) continue;
            if (!line.contains("=") && !line.startsWith(XposedBackend.KEEP) &&
                    keep.contains(line) && !allToModule) {
                out.append(XposedBackend.KEEP);
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }

    /**
     * The state of every shell-owned fabricated overlay in {@code cmd overlay list}: its name to
     * {@code x} (enabled), a space (disabled) or {@code -} (refused, {@code ---}). Pure.
     */
    static Map<String, Character> shellStates(String out) {
        Map<String, Character> states = new HashMap<>();
        if (out == null) return states;
        Matcher m = STATE.matcher(out);
        while (m.find()) states.put(m.group(2), m.group(1) == null ? '-' : m.group(1).charAt(0));
        return states;
    }

    /**
     * Items {target, type (decimal), value, config} of a definition file. Pure.
     */
    static List<String[]> parseDefinition(String xml) {
        List<String[]> items = new ArrayList<>();
        if (xml == null) return items;
        Matcher m = ITEM.matcher(xml);
        while (m.find()) {
            items.add(new String[]{m.group(1), String.valueOf(Integer.parseInt(m.group(2), 16)),
                    m.group(3), m.group(4) == null ? "" : m.group(4)});
        }
        return items;
    }

    /**
     * The renames that hold overlays (their {@code .enabled} marker becomes
     * {@code .enabled.held}: installed, not loaded) or release them again. Pure.
     *
     * @param listing The folder's file names
     * @return {from, to} file names
     */
    static List<String[]> markerMoves(Collection<String> listing, Collection<String> names,
                                      boolean hold) {
        Set<String> files = new HashSet<>(listing);
        List<String[]> moves = new ArrayList<>();
        for (String name : new LinkedHashSet<>(names)) {
            String enabled = name + XposedBackend.ENABLED;
            String held = name + XposedBackend.HELD;
            if (hold && files.contains(enabled)) {
                moves.add(new String[]{enabled, held});
            } else if (!hold && files.contains(held) && !files.contains(enabled)) {
                moves.add(new String[]{held, enabled});
            }
        }
        return moves;
    }

    // On the device

    /**
     * Entries a shell overlay may not change in the target: predicted from its resource table,
     * plus those idmap2 refused for this version of it before. Blocking (the target's APK).
     */
    static Set<String> refusedFor(Context context, String target, List<String[]> items) {
        Set<String> refused = new LinkedHashSet<>();
        // The framework is signed like the shell
        if (context == null || items.isEmpty() || "android".equals(target)) return refused;
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo info = pm.getPackageInfo(target, 0);
            ApplicationInfo app = info.applicationInfo;
            Resources res = pm.getResourcesForApplication(app);
            Map<String, Integer> nameToId = new LinkedHashMap<>();
            for (String[] item : items) {
                if (nameToId.containsKey(item[0])) continue;
                int slash = item[0].indexOf('/');
                if (slash <= 0) continue;
                int id = res.getIdentifier(item[0].substring(slash + 1),
                        item[0].substring(0, slash), target);
                if (id != 0) nameToId.put(item[0], id);
            }
            refused.addAll(ArscOverlayable.refusedByShell(pm, app, nameToId));
            refused.addAll(learned(context, target, versionKey(info)));
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            Log.w(TAG, target + ": refusals not predicted: " + e);
        }
        if (!refused.isEmpty()) {
            Log.i(TAG, target + ": " + refused.size() + " values go to the module (not " +
                    "overlayable by a shell overlay)");
        }
        return refused;
    }

    private static String versionKey(PackageInfo info) {
        return XposedBackend.versionKey(info.applicationInfo.sourceDir,
                androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info));
    }

    private static File dir(Context context, String sub) {
        File dir = new File(context.getFilesDir(), "classic/" + sub);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    /**
     * What idmap2 refused for this version of the target before
     */
    static Set<String> learned(Context context, String target, String versionKey) {
        Set<String> names = new LinkedHashSet<>();
        File file = new File(dir(context, "refused"), target + ".txt");
        if (!file.isFile()) return names;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String key = XposedBackend.readKey(reader.readLine());
            if (key == null || !key.equals(versionKey)) return names;
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) names.add(line.trim());
            }
        } catch (IOException ignored) {
            // nothing learned
        }
        return names;
    }

    static void learn(Context context, String target, String versionKey, Set<String> more) {
        if (more.isEmpty() || versionKey == null) return;
        Set<String> all = new LinkedHashSet<>(learned(context, target, versionKey));
        if (!all.addAll(more)) return;
        File file = new File(dir(context, "refused"), target + ".txt");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(XposedBackend.KEY_HEADER + versionKey + '\n');
            for (String name : all) writer.write(name + '\n');
        } catch (IOException e) {
            Log.w(TAG, "could not remember the refusals for " + target, e);
        }
    }

    /**
     * Keeps a copy of a registered definition, to register it again after a refusal
     */
    static void saveDefinition(Context context, String name, List<String[]> items) {
        if (context == null) return;
        File file = new File(dir(context, "defs"), name + ".xml");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(FabricatedValues.definitionXml(items));
        } catch (IOException e) {
            Log.w(TAG, "could not keep the definition of " + name, e);
        }
    }

    static List<String[]> savedDefinition(Context context, String name) {
        File file = new File(dir(context, "defs"), name + ".xml");
        if (!file.isFile()) return null;
        try {
            return parseDefinition(new String(Files.readAllBytes(file.toPath()),
                    StandardCharsets.UTF_8));
        } catch (IOException e) {
            return null;
        }
    }

    static void forgetDefinition(Context context, String name) {
        //noinspection ResultOfMethodCallIgnored
        new File(dir(context, "defs"), name + ".xml").delete();
    }

    /**
     * After enabling: every one of these overlays whose fabricated half idmap2 refused as a whole
     * ({@code ---}) is registered again without what idmap2 rejected, which the module applies
     * by name from then on (and from the next build straight away). When nothing registers, the
     * module applies the whole overlay. Root, background thread.
     */
    public static void healRefused(Context context, Iterable<String> names) {
        List<String> fabricated = new ArrayList<>();
        for (String name : names) {
            if (XposedBackend.isFabricated(name)) fabricated.add(name);
        }
        if (fabricated.isEmpty() || context == null) return;
        long since = System.currentTimeMillis() - 2000;
        Map<String, Character> states = shellStates(Root.runCommand(
                "cmd overlay list 2>/dev/null | grep 'com.android.shell:'"));
        for (String name : fabricated) {
            Character state = states.get(name);
            if (state == null || state != '-') continue;
            try {
                heal(context, name, since);
            } catch (RuntimeException e) {
                Log.e(TAG, name + ": could not move the refused values to the module", e);
            }
        }
    }

    private static void heal(Context context, String name, long since) {
        String target = XposedBackend.getTarget(name);
        if (target == null) return;
        List<String[]> items = savedDefinition(context, name);
        Set<String> refused = new LinkedHashSet<>();
        List<String[]> kept = items == null || items.isEmpty() ? null :
                SystemAmoled.registerChecked(context, target, name, items, refused, since);
        boolean allToModule = kept == null;
        Set<String> toModule = new LinkedHashSet<>(refused);
        if (allToModule) {
            if (items != null) for (String[] item : items) toModule.add(item[0]);
            // Off, and not registered again by the restore after a reboot
            FabricatedValues.forget(name);
            forgetDefinition(context, name);
        } else {
            saveDefinition(context, name, kept);
        }
        String index = XposedBackend.readIndex(name);
        if (index != null) XposedBackend.writeIndex(context, name, rerouted(index, toModule,
                allToModule));
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(target, 0);
            learn(context, target, versionKey(info), refused);
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
            // the framework, or gone
        }
        ClassicReport report = ClassicReport.load(context, name);
        if (report != null && report.toModule.addAll(toModule)) report.save(context, name);
        Log.i(TAG, name + ": refused by idmap2, " + (allToModule ?
                "the module applies all of it now" :
                toModule.size() + " values moved to the module, " + kept.size() +
                        " stay native"));
    }
}
