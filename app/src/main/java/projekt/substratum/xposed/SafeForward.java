/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.xposed;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.XModuleResources;
import android.content.res.XResources;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;
import android.os.Process;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.robv.android.xposed.XposedBridge;
import projekt.substratum.common.platform.CrashAttribution;
import projekt.substratum.common.platform.ForwardCheck;
import projekt.substratum.common.platform.XposedBackend;

/**
 * Forwarding a theme's files without taking the app down, and telling which part did when one
 * does.
 * <ul>
 * <li>Drawables are forwarded through a {@link XResources.DrawableLoader}: on the first lookup of
 * each name the theme's drawable is loaded inside a try/catch and its class compared with the
 * app's own ({@link ForwardCheck}); one that does not load, or that the app would cast to another
 * class, is left stock. The verdict is kept for the process, so it costs one load per name.</li>
 * <li>Layouts and colour state lists are parsed or inflated once before they are forwarded.</li>
 * <li>Every name left stock is logged once per process and listed in
 * {@code <de data dir>/reborn_skipped_<overlay>}, which the app reads.</li>
 * <li>Whatever was forwarded is remembered; if the process then dies of an uncaught exception,
 * {@link CrashAttribution} names the overlay it points at, in
 * {@code <de data dir>/reborn_guard_crash}, so the app can pause that part.</li>
 * </ul>
 * Nothing here throws into the host process.
 */
final class SafeForward {

    private static final String TAG = "SubstratumXposed: ";
    /**
     * Stands for "no replacement": XResources' getters only use replacements of the types they
     * know and fall back to the app's own resource for anything else. Used to switch a dark mode
     * only replacement off in light mode (a replacement cannot be removed).
     */
    static final Object STOCK = new Object();
    private static final List<CrashAttribution.Forwarded> FORWARDED =
            Collections.synchronizedList(new ArrayList<>());
    private static final Set<String> SKIPPED = Collections.synchronizedSet(new HashSet<>());
    // Skipped lists written by this process: the first write starts the file afresh
    private static final Set<String> WRITTEN = Collections.synchronizedSet(new HashSet<>());
    private static final ThreadLocal<Boolean> BUSY = new ThreadLocal<>();
    private static volatile boolean recorderInstalled;
    private static volatile String ownDir;

    private SafeForward() {
    }

    static boolean isNight(Resources res) {
        try {
            return (res.getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES;
        } catch (Throwable t) {
            return true;
        }
    }

    /**
     * Remember a forwarded resource for crash attribution
     */
    static void forwarded(String overlay, String overlayPackage, String resource,
                          String replacementClass, String stockClass) {
        if (FORWARDED.size() > 20000) return;
        FORWARDED.add(new CrashAttribution.Forwarded(overlay, overlayPackage, resource,
                replacementClass, stockClass));
    }

    /**
     * A drawable (or mipmap) of the theme, checked on its first lookup
     */
    static final class Drawables extends XResources.DrawableLoader {
        private final XModuleResources modRes;
        private final int repId;
        private final String overlay;
        private final String overlayPackage;
        private final String resource;
        private final boolean darkOnly;
        private final boolean systemWide;
        private volatile Boolean allowed;

        /**
         * @param systemWide Set system-wide (framework), else for one app's resources
         */
        Drawables(XModuleResources modRes, int repId, String overlay, String overlayPackage,
                  String resource, boolean darkOnly, boolean systemWide) {
            this.modRes = modRes;
            this.repId = repId;
            this.overlay = overlay;
            this.overlayPackage = overlayPackage;
            this.resource = resource;
            this.darkOnly = darkOnly;
            this.systemWide = systemWide;
        }

        @Override
        public Drawable newDrawable(XResources res, int id) {
            try {
                // Re-entered from the stock read below: the app's own
                if (Boolean.TRUE.equals(BUSY.get())) return null;
                if (darkOnly && !isNight(res)) return null;
                Boolean ok = allowed;
                if (ok == null) {
                    ok = allowed = preflight(res, id);
                    // Checked and fine: from now on forwarded as before, with the app's theme.
                    // Dark mode only keeps this loader, which follows the configuration
                    if (ok && !darkOnly) {
                        if (systemWide) {
                            XResources.setSystemWideReplacement(id, modRes.fwd(repId));
                        } else {
                            res.setReplacement(id, modRes.fwd(repId));
                        }
                    }
                }
                if (!ok) return null;
                return modRes.getDrawable(repId, null);
            } catch (Throwable t) {
                allowed = false;
                skip(overlay, resource, "failed: " + t);
                return null;
            }
        }

        private boolean preflight(XResources res, int id) {
            BUSY.set(true);
            try {
                Drawable replacement;
                try {
                    replacement = modRes.getDrawable(repId, null);
                } catch (Throwable t) {
                    skip(overlay, resource, "does not load: " + t);
                    return false;
                }
                Drawable stock = null;
                try {
                    stock = res.getDrawable(id, null);
                } catch (Throwable ignored) {
                    // The app has none here: nothing to compare with
                }
                String stockClass = stock != null ? stock.getClass().getName() : null;
                String repClass = replacement != null ? replacement.getClass().getName() : null;
                String verdict = ForwardCheck.drawable(stockClass, repClass,
                        stock != null && replacement != null &&
                                stock.getClass().isInstance(replacement));
                if (verdict != null) {
                    skip(overlay, resource, verdict);
                    return false;
                }
                forwarded(overlay, overlayPackage, resource, repClass, stockClass);
                return true;
            } finally {
                BUSY.set(false);
            }
        }
    }

    /**
     * Whether a layout, color state list or other file of the theme opens at all; parsed once,
     * before it is forwarded
     */
    static boolean fileOpens(XModuleResources modRes, String overlay, String type, String name,
                             int repId) {
        try {
            switch (type) {
                case "layout":
                case "xml":
                case "anim":
                case "animator":
                case "interpolator": {
                    XmlResourceParser parser = modRes.getXml(repId);
                    try {
                        int guard = 0;
                        while (parser.next() != XmlResourceParser.END_DOCUMENT &&
                                guard++ < 100000) {
                            // read to the end: a broken file throws here
                        }
                    } finally {
                        parser.close();
                    }
                    return true;
                }
                case "color":
                    modRes.getColorStateList(repId, null);
                    return true;
                default:
                    return true;
            }
        } catch (Throwable t) {
            skip(overlay, type + '/' + name, "does not open: " + t);
            return false;
        }
    }

    /**
     * Log a name left stock once per process and list it for the app
     */
    static void skip(String overlay, String resource, String why) {
        if (!SKIPPED.add(overlay + '|' + resource)) return;
        try {
            XposedBridge.log(TAG + overlay + ": " + resource + " left stock, " + why);
        } catch (Throwable ignored) {
            // nothing to log to
        }
        String dir = ownDir();
        if (dir == null) return;
        boolean append = !WRITTEN.add(overlay);
        try (FileWriter writer = new FileWriter(
                new File(dir, XposedBackend.SKIPPED_PREFIX + overlay), append)) {
            writer.write(resource + ' ' + why.replace('\n', ' ') + '\n');
        } catch (Throwable ignored) {
            // the log above has it
        }
    }

    /**
     * Records what a crash of this process points at (chained before the system's own handler)
     */
    static void installRecorder(String deDataDir) {
        if (deDataDir != null) ownDir = deDataDir;
        if (recorderInstalled) return;
        recorderInstalled = true;
        try {
            Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
                try {
                    record(error);
                } catch (Throwable ignored) {
                    // never in the way of the crash itself
                }
                if (previous != null) previous.uncaughtException(thread, error);
            });
        } catch (Throwable t) {
            try {
                XposedBridge.log(TAG + "crash recorder not installed: " + t);
            } catch (Throwable ignored) {
                // nothing to log to
            }
        }
    }

    private static void record(Throwable error) {
        List<CrashAttribution.Forwarded> forwarded;
        synchronized (FORWARDED) {
            forwarded = new ArrayList<>(FORWARDED);
        }
        if (forwarded.isEmpty()) return;
        StringWriter text = new StringWriter();
        error.printStackTrace(new PrintWriter(text));
        CrashAttribution.Result result = CrashAttribution.attribute(text.toString(), forwarded);
        String dir = ownDir();
        if (result == null || dir == null) return;
        try (FileWriter writer = new FileWriter(new File(dir, XposedBackend.CRASH_FILE))) {
            writer.write(CrashAttribution.Recorded.format(result, System.currentTimeMillis(),
                    String.valueOf(error)));
        } catch (Throwable ignored) {
            // the guard still trips; the app offers to pause every classic part then
        }
    }

    /**
     * This process' app's device-protected data folder, or null
     */
    private static String ownDir() {
        if (ownDir != null) return ownDir;
        try {
            String process = HookGuard.processName();
            if (process == null || process.startsWith("<")) return null;
            int colon = process.indexOf(':');
            String pkg = colon > 0 ? process.substring(0, colon) : process;
            ownDir = "/data/user_de/" + Process.myUid() / 100000 + '/' + pkg;
        } catch (Throwable t) {
            return null;
        }
        return ownDir;
    }
}
