/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.xposed;

import android.app.Application;
import android.os.Build;
import android.os.Process;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.XposedBridge;
import projekt.substratum.common.platform.XposedBackend;

/**
 * Decides, once per process and package, whether the module may hook an app at all.
 * <ul>
 * <li><b>Kill switch</b>: while {@code /data/resource-cache/substratum/hooks.off} exists nothing
 * is hooked and no resource is forwarded; {@code hooks.off.<package>} does the same for one
 * package ({@code hooks.off.android} for framework resources).</li>
 * <li><b>Crash-loop guard</b>, for System UI and every app with module colors or enabled overlays:
 * the main process records its start time in {@code <de data dir>/reborn_guard_starts}. When the
 * last three starts fall within 90 seconds the guard writes {@code reborn_guard_tripped} and the
 * app stays stock until that file is removed. A process that survives 30 seconds empties the
 * list, so deliberate restarts never add up.</li>
 * </ul>
 * While the app a process belongs to is left stock, no other package is hooked in that process
 * either. Guard file I/O that fails lets the hooks run; nothing here throws into the host
 * process.
 */
final class HookGuard {

    static final String OK = "ok";
    static final String TRIPPED = "tripped";
    static final String OFF = "off";

    private static final String TAG = "RebornGuard: ";
    private static final String SYSTEMUI = "com.android.systemui";
    private static final String FRAMEWORK = "android";
    private static final String STARTS_FILE = XposedBackend.GUARD_FILES + "starts";
    private static final String TRIPPED_FILE = XposedBackend.GUARD_FILES + "tripped";
    private static final int KEEP = 5;
    private static final int LOOP_STARTS = 3;
    private static final long LOOP_WINDOW_MS = 90_000;
    private static final long SURVIVED_MS = 30_000;
    private static final int PER_USER_RANGE = 100000;

    // Package -> OK, TRIPPED or OFF, decided once per process: for the app the process belongs
    // to, and for other packages whose code or resources it loads
    private static final Map<String, String> STATES = new HashMap<>();
    private static final Map<String, String> OTHERS = new HashMap<>();
    // Set once the app this process belongs to is left stock: nothing else is hooked here then
    private static volatile String processState;

    private HookGuard() {
    }

    /**
     * Framework resources (initZygote): only the kill switch applies.
     */
    static boolean allowFramework() {
        if (!killed(FRAMEWORK)) return true;
        log("kill switch, " + FRAMEWORK + " left stock");
        return false;
    }

    /**
     * For handleLoadPackage.
     *
     * @param deDataDir   The package's device-protected data directory
     * @param processName This process, or null if unknown
     * @param own         True for the app this process belongs to (the first application).
     *                    Other packages only follow the kill switch: their guard files belong
     *                    to another app.
     * @return {@link #OK}, {@link #TRIPPED} or {@link #OFF}
     */
    static String state(String pkg, String deDataDir, String processName, boolean own) {
        String state;
        synchronized (STATES) {
            Map<String, String> cache = own ? STATES : OTHERS;
            state = cache.get(pkg);
            if (state == null) {
                state = decide(pkg, deDataDir, processName, own);
                cache.put(pkg, state);
            }
        }
        if (own && !OK.equals(state)) processState = state;
        return state;
    }

    /**
     * For handleInitPackageResources, where no application info is at hand.
     */
    static String state(String pkg) {
        String processName = processName();
        boolean own = processName == null || processName.startsWith("<") ||
                processName.equals(pkg) || processName.startsWith(pkg + ':');
        return state(pkg, "/data/user_de/" + Process.myUid() / PER_USER_RANGE + '/' + pkg,
                processName, own);
    }

    /**
     * False once the kill switch or the crash loop guard left the app this process belongs to
     * stock: then no other package is hooked in it either (an overlay for another package's
     * resources can take this process down as well).
     */
    static boolean processAllowed() {
        return processState == null;
    }

    private static String decide(String pkg, String deDataDir, String processName,
                                 boolean own) {
        if (killed(pkg)) {
            if (own) log("kill switch, " + pkg + " left stock");
            return OFF;
        }
        if (!own) return OK;
        try {
            if (!guarded(pkg)) return OK;
            return crashLoop(pkg, deDataDir, processName) ? TRIPPED : OK;
        } catch (Throwable t) {
            log("guard failed for " + pkg + ", hooking as usual: " + t);
            return OK;
        }
    }

    private static boolean killed(String pkg) {
        try {
            return new File(XposedBackend.DIR, XposedBackend.HOOKS_OFF).exists() ||
                    new File(XposedBackend.DIR, XposedBackend.HOOKS_OFF + '.' + pkg).exists();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * System UI, and apps the module changes: AMOLED colors by id or enabled overlays.
     */
    private static boolean guarded(String pkg) {
        if (SYSTEMUI.equals(pkg)) return true;
        if (new File(XposedBackend.DIR, pkg + XposedBackend.AMOLED_IDS).exists()) return true;
        String[] files = new File(XposedBackend.DIR).list();
        if (files == null) return false;
        String targetLine = "target=" + pkg;
        for (String file : files) {
            if (!file.endsWith(XposedBackend.ENABLED)) continue;
            String name = file.substring(0, file.length() - XposedBackend.ENABLED.length());
            try (BufferedReader reader = new BufferedReader(new FileReader(
                    new File(XposedBackend.DIR, name + XposedBackend.INDEX)))) {
                if (targetLine.equals(reader.readLine())) return true;
            } catch (IOException ignored) {
                // no index: not ours
            }
        }
        return false;
    }

    /**
     * @return True to leave the package stock
     */
    private static boolean crashLoop(String pkg, String deDataDir, String processName) {
        if (deDataDir == null) return false;
        try {
            File tripped = new File(deDataDir, TRIPPED_FILE);
            if (tripped.exists()) {
                log("crash loop guard tripped earlier, " + pkg + " left stock until " +
                        deDataDir + '/' + XposedBackend.GUARD_FILES + "* is removed");
                return true;
            }
            // Only the main process counts: an app's other processes start along with it and
            // would add up to a loop on their own
            if (processName != null && !processName.startsWith("<") &&
                    !processName.equals(pkg)) {
                return false;
            }
            File ledger = new File(deDataDir, STARTS_FILE);
            long now = System.currentTimeMillis();
            List<Long> starts = readStarts(ledger);
            starts.add(now);
            while (starts.size() > KEEP) starts.remove(0);
            int count = starts.size();
            if (count >= LOOP_STARTS) {
                long span = now - starts.get(count - LOOP_STARTS);
                if (span >= 0 && span <= LOOP_WINDOW_MS) {
                    write(tripped, now + "\n");
                    log("crash loop (" + LOOP_STARTS + " starts in " + span / 1000 + " s), " +
                            pkg + " left stock until " + deDataDir + '/' +
                            XposedBackend.GUARD_FILES + "* is removed");
                    return true;
                }
            }
            StringBuilder content = new StringBuilder();
            for (long start : starts) content.append(start).append('\n');
            write(ledger, content.toString());
            clearWhenSurvived(ledger);
            return false;
        } catch (Throwable t) {
            log("guard files unavailable for " + pkg + ", hooking as usual: " + t);
            return false;
        }
    }

    private static List<Long> readStarts(File ledger) {
        List<Long> starts = new ArrayList<>();
        if (!ledger.exists()) return starts;
        try (BufferedReader reader = new BufferedReader(new FileReader(ledger))) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    starts.add(Long.parseLong(line.trim()));
                } catch (NumberFormatException ignored) {
                    // skip
                }
            }
        } catch (IOException ignored) {
            // treat as empty
        }
        return starts;
    }

    private static void write(File file, String content) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }

    /**
     * The process is still alive after {@link #SURVIVED_MS}: not a crash loop, forget its start.
     */
    private static void clearWhenSurvived(File ledger) {
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(SURVIVED_MS);
                new FileOutputStream(ledger).close();
            } catch (Throwable ignored) {
                // the next start counts one more, nothing else
            }
        }, "RebornGuard");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * This process' name, or null if it cannot be told.
     */
    static String processName() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                String name = Application.getProcessName();
                if (name != null && !name.isEmpty()) return name;
            }
        } catch (Throwable ignored) {
            // below
        }
        try (FileInputStream in = new FileInputStream("/proc/self/cmdline")) {
            byte[] buffer = new byte[256];
            int read = in.read(buffer);
            int end = 0;
            while (end < read && buffer[end] != 0) end++;
            if (end > 0) return new String(buffer, 0, end, StandardCharsets.UTF_8).trim();
        } catch (Throwable ignored) {
            // unknown
        }
        return null;
    }

    private static void log(String message) {
        try {
            XposedBridge.log(TAG + message);
        } catch (Throwable ignored) {
            // nothing to log to
        }
    }
}
