/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.common.platform;

import android.content.Context;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import projekt.substratum.R;
import projekt.substratum.common.References;
import projekt.substratum.common.Resources;

/**
 * The parts of a classic theme on this phone: its stored overlays (one per target or
 * sub-target), each on, off or held (installed but not loaded, after it crashed System UI).
 * Turning a whole theme off and back on, pausing the part a crash points at. Blocking: run on a
 * background thread; changes need root.
 */
public final class ClassicParts {

    private static final String FRAMEWORK = "android";
    private static final String SYSTEMUI = "com.android.systemui";

    private ClassicParts() {
    }

    /**
     * One stored overlay of a theme
     */
    public static final class Part {
        public final String overlay;
        public final String target;
        /**
         * The theme package it came from, or null when unknown
         */
        public final String themePkg;
        public final String label;
        public final boolean enabled;
        public final boolean held;
        /**
         * Resources the module left stock in this part's app, because they did not load or the
         * app would have cast them to another class ({@link #withSkipped})
         */
        public int skipped;

        Part(String overlay, String target, String themePkg, String label, boolean enabled,
             boolean held) {
            this.overlay = overlay;
            this.target = target;
            this.themePkg = themePkg;
            this.label = label;
            this.enabled = enabled;
            this.held = held;
        }
    }

    /**
     * Fills {@link Part#skipped} from the lists the module writes in each app's
     * device-protected data, with one root call. Root.
     */
    public static List<Part> withSkipped(List<Part> parts) {
        if (parts.isEmpty()) return parts;
        String out = projekt.substratum.util.helpers.Root.runCommand(
                "grep -c '' /data/user_de/0/*/" + XposedBackend.SKIPPED_PREFIX + "* " +
                        "2>/dev/null");
        if (out == null) return parts;
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        for (String line : out.split("\n")) {
            int slash = line.lastIndexOf('/');
            int colon = line.lastIndexOf(':');
            if (slash < 0 || colon < slash) continue;
            String file = line.substring(slash + 1, colon);
            if (!file.startsWith(XposedBackend.SKIPPED_PREFIX)) continue;
            try {
                counts.merge(file.substring(XposedBackend.SKIPPED_PREFIX.length()),
                        Integer.parseInt(line.substring(colon + 1).trim()), Integer::sum);
            } catch (NumberFormatException ignored) {
                // not a count
            }
        }
        for (Part part : parts) {
            Integer count = counts.get(part.overlay);
            if (count != null) part.skipped = count;
        }
        return parts;
    }

    /**
     * What turning a theme off changed, to undo it
     */
    public static final class TurnedOff {
        public final List<String> enabled = new ArrayList<>();
        public final List<String> held = new ArrayList<>();

        public int count() {
            return enabled.size() + held.size();
        }
    }

    /**
     * The theme an overlay came from (its Substratum_Parent), or null
     */
    public static String themeOf(Context context, String overlay) {
        try {
            return XposedBackend.getMetadata(context, overlay, References.metadataOverlayParent);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static Part part(Context context, String overlay) {
        String target = XposedBackend.getTarget(overlay);
        if (target == null) return null;
        return new Part(overlay, target, themeOf(context, overlay),
                label(context, overlay, target), XposedBackend.isEnabled(overlay),
                XposedBackend.isHeld(overlay));
    }

    /**
     * Every stored overlay of a theme, in its order of targets
     */
    public static List<Part> ofTheme(Context context, String themePkg) {
        List<Part> parts = new ArrayList<>();
        if (themePkg == null) return parts;
        List<String> names = XposedBackend.listAll();
        Collections.sort(names);
        for (String name : names) {
            if (!themePkg.equals(themeOf(context, name))) continue;
            Part part = part(context, name);
            if (part != null) parts.add(part);
        }
        return parts;
    }

    /**
     * Enabled (not held) overlays of a target
     */
    public static List<String> enabledFor(String target) {
        List<String> names = new ArrayList<>();
        for (String name : XposedBackend.listAll()) {
            if (XposedBackend.isEnabled(name) && target.equals(XposedBackend.getTarget(name))) {
                names.add(name);
            }
        }
        return names;
    }

    /**
     * What a part changes, in a few words: "navigation bar icons", "Settings", "Android system"
     */
    public static String label(Context context, String overlay, String target) {
        String name = overlay.toLowerCase(Locale.ROOT);
        if (name.startsWith(Resources.SYSTEMUI_NAVBARS + '.')) {
            return context.getString(R.string.reborn_part_navbars);
        } else if (name.startsWith(Resources.SYSTEMUI_STATUSBARS + '.')) {
            return context.getString(R.string.reborn_part_statusbars);
        } else if (name.startsWith(Resources.SYSTEMUI_QSTILES + '.')) {
            return context.getString(R.string.reborn_part_tiles);
        } else if (name.startsWith(Resources.SYSTEMUI_HEADERS + '.')) {
            return context.getString(R.string.reborn_part_headers);
        } else if (name.startsWith(Resources.SETTINGS_ICONS + '.')) {
            return context.getString(R.string.reborn_part_settings_icons);
        } else if (FRAMEWORK.equals(target)) {
            return context.getString(R.string.reborn_applied_android);
        } else if (SYSTEMUI.equals(target)) {
            return context.getString(R.string.reborn_part_systemui);
        }
        try {
            PackageManager pm = context.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(target, 0)).toString();
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return target;
        }
    }

    /**
     * A theme's name (its Substratum_Name, else its app name), or the package
     */
    public static String themeName(Context context, String themePkg) {
        if (themePkg == null) return null;
        try {
            android.content.pm.ApplicationInfo info = context.getPackageManager()
                    .getApplicationInfo(themePkg, PackageManager.GET_META_DATA);
            String name = info.metaData != null ?
                    info.metaData.getString(References.metadataName) : null;
            return name != null ? name : info.loadLabel(context.getPackageManager()).toString();
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return themePkg;
        }
    }

    /**
     * Turn a theme off: every overlay and module part of it is switched off (held ones are
     * released from their hold too), then each target restarts once. Root.
     */
    public static TurnedOff turnOff(Context context, String themePkg) {
        TurnedOff off = new TurnedOff();
        for (Part part : ofTheme(context, themePkg)) {
            if (part.enabled) off.enabled.add(part.overlay);
            if (part.held) off.held.add(part.overlay);
        }
        if (off.count() == 0) return off;
        XposedBackend.disable(off.enabled);
        XposedBackend.forgetHeld(off.held);
        if (!off.enabled.isEmpty()) XposedBackend.restartTargets(context, off.enabled);
        return off;
    }

    /**
     * Undo {@link #turnOff}: what was on is on again, what was held is held again. Root.
     */
    public static void undo(Context context, TurnedOff off) {
        List<String> all = new ArrayList<>(off.enabled);
        all.addAll(off.held);
        if (all.isEmpty()) return;
        XposedBackend.enable(context, all);
        if (!off.held.isEmpty()) XposedBackend.hold(off.held);
        if (!off.enabled.isEmpty()) XposedBackend.restartTargets(context, off.enabled);
    }

    /**
     * One part on or off. Releasing a held part loads it again. Root.
     */
    public static boolean setEnabled(Context context, String overlay, boolean on) {
        List<String> one = Collections.singletonList(overlay);
        if (on) {
            if (XposedBackend.isHeld(overlay)) {
                XposedBackend.release(context, one);
            } else {
                XposedBackend.enable(context, one);
            }
        } else {
            XposedBackend.disable(one);
            XposedBackend.forgetHeld(one);
        }
        XposedBackend.restartTargets(context, one);
        return XposedBackend.isEnabled(overlay) == on;
    }

    /**
     * Pause parts that crashed System UI: they are held (installed, not loaded), System UI's
     * guard is cleared and it restarts without them. Root.
     *
     * @return The parts held
     */
    public static List<String> pauseForSystemUi(List<String> overlays) {
        List<String> held = XposedBackend.hold(overlays);
        XposedBackend.resetGuard(SYSTEMUI);
        QsOptions.restartSystemUi();
        return held;
    }
}
