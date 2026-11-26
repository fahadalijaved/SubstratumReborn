/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.common.platform;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.Build;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import projekt.substratum.BuildConfig;
import projekt.substratum.Substratum;
import projekt.substratum.common.References;
import projekt.substratum.common.Systems;
import projekt.substratum.util.helpers.Root;

/**
 * What the Reborn Home and Setup screens show: root, the LSPosed module and its scope, the
 * module's heartbeat from System UI, the theme engine and the device.
 * <p>
 * Every method blocks (root shell, SQLite, files): call them off the main thread. None of them
 * throws; whatever cannot be read is reported as {@link Level#UNKNOWN}.
 */
public final class RebornStatus {

    public static final String MODULE = "projekt.substratum";
    public static final String SYSTEMUI = "com.android.systemui";
    public static final String SETTINGS = "com.android.settings";
    /**
     * Packages the module must have in its LSPosed scope: System UI (quick settings, AMOLED) and
     * Settings. Reborn itself is not needed: LSPosed hides modules in its scope list, and
     * {@link #moduleEnabled} reads LSPosed's database instead of relying on the self-hook.
     */
    public static final String[] REQUIRED_SCOPE = {SYSTEMUI, SETTINGS};
    public static final String LSPOSED_MANAGER = "org.lsposed.manager";
    public static final String[] ROOT_MANAGERS = {"me.weishu.kernelsu", "com.rifsxd.ksunext"};
    static final String LSPOSED_DIR = "/data/adb/lspd/config";
    static final String LSPOSED_DB = "modules_config.db";
    private static final String[] LSPOSED_FILES =
            {LSPOSED_DB, LSPOSED_DB + "-wal", LSPOSED_DB + "-shm"};
    private static final String PREF_THEME_MODE = "current_theme_mode";
    private static final String TAG = "RebornStatus";
    /**
     * One LSPosed read at a time: Home and Setup (and a refresh while a read is still running)
     * share the copy folder, and one read's clean-up would delete the other's files
     */
    private static final Object LSPOSED_LOCK = new Object();
    /**
     * The module's enabled state from the last database read in this process, or null: the
     * main thread answers from it instead of reading the database
     */
    private static volatile Boolean lastEnabled;

    private RebornStatus() {
    }

    public enum Level {OK, WARN, ERROR, UNKNOWN}

    public static final class RootInfo {
        public boolean granted;
        /**
         * "KernelSU 12081", or "root" when the su build cannot be read
         */
        public String version;
    }

    public static final class LsposedInfo {
        public Level level = Level.UNKNOWN;
        /**
         * The module is known to LSPosed at all
         */
        public boolean registered;
        public boolean enabled;
        public final Set<String> scope = new TreeSet<>();
        /**
         * Why the database could not be read, when {@link #level} is UNKNOWN
         */
        public String error;

        public List<String> missingScope() {
            List<String> missing = new ArrayList<>();
            if (level == Level.UNKNOWN) return missing;
            for (String pkg : REQUIRED_SCOPE) if (!scope.contains(pkg)) missing.add(pkg);
            return missing;
        }
    }

    /**
     * {@code v=..;t=..;qs=..;layout=..;battery=..;guard=ok|tripped|off} written by System UI once
     * the module set up (or skipped) its hooks
     */
    public static final class Heartbeat {
        public boolean present;
        public int version;
        public long time;
        public String guard;
        public boolean qs;
        public boolean layout;
        public boolean battery;
        /**
         * Written by this build of the module: otherwise System UI runs an older build
         */
        public boolean current;
        /**
         * Written after the current boot
         */
        public boolean sinceBoot;

        public boolean guardOk() {
            return "ok".equals(guard);
        }

        public boolean guardTripped() {
            return "tripped".equals(guard);
        }

        public boolean guardOff() {
            return "off".equals(guard);
        }

        /**
         * System UI was hooked by this build since the last boot
         */
        public boolean fresh() {
            return present && sinceBoot && current && guardOk();
        }
    }

    public static final class Engine {
        public int mode;
        public String name;
        public boolean xposed;
        /**
         * The cached engine is not LSPosed although the module is active: offer to switch
         */
        public boolean mismatch;
    }

    public static final class Snapshot {
        public RootInfo root;
        public LsposedInfo lsposed;
        public Heartbeat heartbeat;
        public Engine engine;
        public String oneUi;
        public String android;
        public boolean hooksPaused;
        public long readAt;
        /**
         * When System UI's guard tripped: the classic part its last crash points at, or null
         */
        public ClassicParts.Part crashPart;
        /**
         * When System UI's guard tripped: the classic parts System UI loads (to pause them all
         * when the crash points at none)
         */
        public List<String> classicSystemUiParts = new ArrayList<>();
        /**
         * The theme name of {@link #crashPart}
         */
        public String crashTheme;
    }

    /**
     * Everything at once, in the order each check needs the one before it.
     */
    public static Snapshot read(Context context) {
        Snapshot s = new Snapshot();
        s.root = root();
        s.lsposed = lsposed(context, s.root.granted);
        s.heartbeat = moduleHeartbeat(context);
        s.engine = engine(context, s.lsposed, s.heartbeat);
        s.oneUi = oneUi();
        s.android = "Android " + Build.VERSION.RELEASE;
        s.hooksPaused = hooksPaused();
        if (s.root.granted && s.heartbeat.guardTripped()) readCrash(context, s);
        s.readAt = System.currentTimeMillis();
        return s;
    }

    /**
     * Which classic part System UI's last crash points at (the module's record), and which
     * classic parts it loads
     */
    private static void readCrash(Context context, Snapshot s) {
        try {
            s.classicSystemUiParts.addAll(ClassicParts.enabledFor(SYSTEMUI));
            CrashAttribution.Recorded crash = XposedBackend.readCrash(SYSTEMUI);
            if (crash == null || !XposedBackend.isStored(crash.overlay)) return;
            // The guard trips within 90 seconds of the crash loop's last crash
            if (!crash.explainsTrip(10 * 60_000L)) return;
            ClassicParts.Part part = ClassicParts.part(context, crash.overlay);
            if (part == null || part.held) return;
            s.crashPart = part;
            s.crashTheme = ClassicParts.themeName(context, part.themePkg);
        } catch (RuntimeException e) {
            Log.w(TAG, "crash record unreadable", e);
        }
    }

    // Root

    public static RootInfo root() {
        RootInfo info = new RootInfo();
        try {
            boolean granted = Root.checkRootAccess();
            if (granted && !Root.isAliveQuick()) {
                // A temporary root is gone after a full reboot: ask again next time
                SharedPreferences prefs = Substratum.getPreferences();
                if (prefs != null) prefs.edit().remove("root_access").apply();
                granted = false;
            }
            info.granted = granted;
            if (granted) {
                // From /dev/null: an su without -v must not keep the shell waiting
                info.version = suVersion(Root.runQuickCommand("su -v </dev/null 2>/dev/null"));
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "root check failed", e);
            info.granted = false;
        }
        return info;
    }

    /**
     * "12081:KernelSU" becomes "KernelSU 12081", "27.0:MAGISK:R" becomes "MAGISK 27.0"
     */
    static String suVersion(String out) {
        if (out == null) return "root";
        String line = out.trim();
        int nl = line.indexOf('\n');
        if (nl >= 0) line = line.substring(0, nl).trim();
        if (line.isEmpty() || line.length() > 64) return "root";
        String[] parts = line.split(":");
        if (parts.length >= 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
            return parts[1] + ' ' + parts[0];
        }
        return line;
    }

    // LSPosed

    /**
     * Whether LSPosed has the module enabled for user 0 and its scope, read from a copy of
     * LSPosed's own database (root).
     */
    public static LsposedInfo lsposed(Context context, boolean root) {
        if (!root) {
            LsposedInfo info = new LsposedInfo();
            info.error = "no root";
            return info;
        }
        synchronized (LSPOSED_LOCK) {
            return readLsposed(context);
        }
    }

    /**
     * Whether Reborn's LSPosed module is enabled, cheapest proof first: the self-hook (only when
     * Reborn is in its own scope, which is optional), System UI's heartbeat since this boot, then
     * LSPosed's database (root). Blocking; on the main thread the database is not read and the
     * last answer is used.
     */
    public static boolean moduleEnabled(Context context) {
        if (XposedBackend.isModuleActive()) return true;
        Heartbeat hb = moduleHeartbeat(context);
        if (hb.present && hb.sinceBoot) return true;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Boolean last = lastEnabled;
            return last != null && last;
        }
        if (!Root.checkRootAccess()) return false;
        LsposedInfo info = lsposed(context, true);
        return info.level != Level.UNKNOWN && info.enabled;
    }

    private static LsposedInfo readLsposed(Context context) {
        LsposedInfo info = new LsposedInfo();
        File dir = new File(context.getCacheDir(), "lspd");
        File raw = new File(dir, "raw");
        SQLiteDatabase db = null;
        try {
            if (!copyDatabase(dir, raw)) {
                info.error = "LSPosed database not found";
                return info;
            }
            db = openCopy(new File(dir, LSPOSED_DB));
            try (Cursor c = db.rawQuery(
                    "SELECT 1 FROM modules WHERE module_pkg_name = ?", new String[]{MODULE})) {
                info.registered = c.moveToFirst();
            }
            try (Cursor c = db.rawQuery("SELECT enabled FROM modules_state " +
                    "WHERE module_pkg_name = ? AND user_id = 0", new String[]{MODULE})) {
                info.enabled = c.moveToFirst() && c.getInt(0) == 1;
            }
            try (Cursor c = db.rawQuery("SELECT app_pkg_name FROM scope " +
                    "WHERE module_pkg_name = ? AND user_id = 0", new String[]{MODULE})) {
                while (c.moveToNext()) info.scope.add(c.getString(0));
            }
            info.level = info.enabled ? Level.OK : Level.ERROR;
            lastEnabled = info.enabled;
        } catch (Throwable t) {
            // Another LSPosed build, a changed schema or a locked file: say so, never crash
            Log.w(TAG, "LSPosed database unreadable", t);
            info.level = Level.UNKNOWN;
            info.registered = false;
            info.enabled = false;
            info.scope.clear();
            info.error = t.getClass().getSimpleName();
        } finally {
            if (db != null) {
                try {
                    db.close();
                } catch (Throwable ignored) {
                    // closing a copy
                }
            }
            deleteChildren(raw);
            //noinspection ResultOfMethodCallIgnored
            raw.delete();
            // LSPosed's whole module and scope list: no copy stays behind in the cache
            deleteChildren(dir);
        }
        return info;
    }

    /**
     * Root copies the database (and its write-ahead log) into {@code raw}, readable; the app then
     * copies those into files of its own, so SQLite can keep its shared memory file next to them.
     */
    private static boolean copyDatabase(File dir, File raw) throws IOException {
        deleteChildren(raw);
        deleteChildren(dir);
        if (!raw.mkdirs() && !raw.isDirectory()) return false;
        int uid = Process.myUid();
        String to = raw.getAbsolutePath();
        StringBuilder cmd = new StringBuilder();
        for (String name : LSPOSED_FILES) {
            cmd.append("if [ -f ").append(LSPOSED_DIR).append('/').append(name).append(" ]; then ")
                    .append("cp -f ").append(LSPOSED_DIR).append('/').append(name).append(' ')
                    .append(to).append('/').append(name).append("; fi; ");
        }
        cmd.append("chown ").append(uid).append(':').append(uid).append(' ').append(to)
                .append("/* 2>/dev/null; chmod 644 ").append(to).append("/* 2>/dev/null; ")
                .append("ctx=$(stat -c %C ").append(to).append(" 2>/dev/null); ")
                .append("[ -n \"$ctx\" ] && chcon \"$ctx\" ").append(to).append("/* 2>/dev/null; ")
                .append("[ -f ").append(to).append('/').append(LSPOSED_DB)
                .append(" ] && echo rc=0 || echo rc=1");
        String out = Root.runQuickCommand(cmd.toString());
        if (out == null || !out.trim().endsWith("rc=0")) return false;
        for (String name : LSPOSED_FILES) {
            File from = new File(raw, name);
            if (from.exists()) copy(from, new File(dir, name));
        }
        return new File(dir, LSPOSED_DB).length() > 0;
    }

    /**
     * Read-only first; the copy is the app's own, so if SQLite wants to write its write-ahead
     * log index (an older SQLite) it may open it read-write instead.
     */
    private static SQLiteDatabase openCopy(File file) {
        try {
            return SQLiteDatabase.openDatabase(file.getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
        } catch (SQLiteException e) {
            Log.w(TAG, "read-only open failed, opening the copy read-write: " + e);
            return SQLiteDatabase.openDatabase(file.getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
        }
    }

    private static void copy(File from, File to) throws IOException {
        try (InputStream in = new FileInputStream(from);
             OutputStream out = new FileOutputStream(to)) {
            byte[] buffer = new byte[16384];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        }
    }

    private static void deleteChildren(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isFile()) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }

    // Module heartbeat

    public static Heartbeat moduleHeartbeat(Context context) {
        String raw = null;
        try {
            raw = Settings.Global.getString(context.getContentResolver(),
                    XposedBackend.STATUS_KEY);
        } catch (RuntimeException e) {
            Log.w(TAG, "status unreadable", e);
        }
        long bootTime = System.currentTimeMillis() - SystemClock.elapsedRealtime();
        return parseHeartbeat(raw, BuildConfig.VERSION_CODE, bootTime);
    }

    static Heartbeat parseHeartbeat(String raw, int versionCode, long bootTime) {
        Heartbeat hb = new Heartbeat();
        if (raw == null || raw.trim().isEmpty()) return hb;
        for (String part : raw.split(";")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            String key = part.substring(0, eq).trim();
            String value = part.substring(eq + 1).trim();
            try {
                switch (key) {
                    case "v":
                        hb.version = Integer.parseInt(value);
                        break;
                    case "t":
                        hb.time = Long.parseLong(value);
                        break;
                    case "qs":
                        hb.qs = "1".equals(value);
                        break;
                    case "layout":
                        hb.layout = "1".equals(value);
                        break;
                    case "battery":
                        hb.battery = "1".equals(value);
                        break;
                    case "guard":
                        hb.guard = value;
                        break;
                    default:
                        break;
                }
            } catch (NumberFormatException ignored) {
                // left at 0
            }
        }
        hb.present = hb.version > 0 && hb.time > 0;
        hb.current = hb.version == versionCode;
        hb.sinceBoot = hb.present && hb.time >= bootTime;
        return hb;
    }

    // Engine

    public static Engine engine(Context context, LsposedInfo lsposed, Heartbeat heartbeat) {
        Engine engine = new Engine();
        try {
            engine.mode = Systems.checkThemeSystemModule(context);
        } catch (RuntimeException e) {
            Log.w(TAG, "engine check failed", e);
            engine.mode = References.NO_THEME_ENGINE;
        }
        engine.xposed = engine.mode == References.OVERLAY_MANAGER_SERVICE_XPOSED;
        engine.name = engineName(engine.mode);
        boolean moduleActive = (lsposed != null && lsposed.enabled) ||
                (heartbeat != null && heartbeat.present && heartbeat.sinceBoot);
        engine.mismatch = !engine.xposed && moduleActive;
        return engine;
    }

    /**
     * Switch Substratum to the LSPosed engine (fabricated overlays plus the module).
     */
    public static void useLsposedEngine() {
        SharedPreferences prefs = Substratum.getPreferences();
        if (prefs != null) {
            prefs.edit().putInt(PREF_THEME_MODE, References.OVERLAY_MANAGER_SERVICE_XPOSED)
                    .apply();
        }
    }

    public static String engineName(int mode) {
        switch (mode) {
            case References.OVERLAY_MANAGER_SERVICE_XPOSED:
                return "Fabricated + LSPosed";
            case References.OVERLAY_MANAGER_SERVICE_P_ROOTED:
                return "Overlays (root)";
            case References.OVERLAY_MANAGER_SERVICE_O_ROOTED:
                return "Overlays (root, Oreo)";
            case References.OVERLAY_MANAGER_SERVICE_SYSSERV:
                return "Substratum service";
            case References.OVERLAY_MANAGER_SERVICE_ANDROMEDA:
                return "Andromeda";
            case References.OVERLAY_MANAGER_SERVICE_INTERFACER:
                return "Theme Interfacer";
            case References.RUNTIME_RESOURCE_OVERLAY_N_ROOTED:
                return "Legacy overlays";
            case References.SAMSUNG_THEME_ENGINE_N:
                return "Samsung";
            default:
                return "None";
        }
    }

    // Device

    /**
     * "One UI 9.0" from ro.build.version.oneui (90000), or null on other devices
     */
    public static String oneUi() {
        String prop = null;
        try {
            Class<?> props = Class.forName("android.os.SystemProperties");
            Method get = props.getMethod("get", String.class, String.class);
            prop = (String) get.invoke(null, "ro.build.version.oneui", "");
        } catch (Throwable t) {
            // A hidden API this app's target may be refused: getprop below
        }
        if (prop == null || prop.trim().isEmpty()) {
            try {
                prop = Systems.getProp("ro.build.version.oneui");
            } catch (Throwable t) {
                return null;
            }
        }
        return oneUiName(prop);
    }

    static String oneUiName(String prop) {
        if (prop == null || prop.trim().isEmpty()) return null;
        try {
            int value = Integer.parseInt(prop.trim());
            if (value <= 0) return null;
            int major = value / 10000;
            int minor = (value % 10000) / 100;
            return String.format(Locale.US, "One UI %d.%d", major, minor);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static boolean hooksPaused() {
        try {
            return XposedBackend.areHooksPaused();
        } catch (RuntimeException e) {
            return false;
        }
    }

    // Actions (root, blocking)

    /**
     * Open the LSPosed manager without its launcher icon (root).
     *
     * @return True, if the manager started
     */
    public static boolean openLsposedWithRoot() {
        // From /dev/null: nothing may read the shared root shell's input (its end marker)
        String out = Root.runQuickCommand("am start -c org.lsposed.manager.LAUNCH_MANAGER " +
                "com.android.shell/.BugreportWarningActivity </dev/null 2>&1; echo rc=$?");
        if (out == null) return false;
        String trimmed = out.trim();
        return trimmed.endsWith("rc=0") && !trimmed.contains("Error");
    }

    /**
     * Clear System UI's crash loop guard and restart it, so the module hooks it again.
     */
    public static void resetSystemUiGuard() {
        XposedBackend.resetGuard(SYSTEMUI);
        QsOptions.restartSystemUi();
    }
}
