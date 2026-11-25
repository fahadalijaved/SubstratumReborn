/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.common.platform;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

import projekt.substratum.util.helpers.Root;

/**
 * The single source of truth of "AMOLED for every app", kept in
 * {@code files/amoled/state.json}:
 * <pre>
 * {"version":1, "enabled":true, "rulesTheme":"...", "appliedAt":ms, "restoredAt":ms,
 *  "bootCount":n, "appliedBootCount":n,
 *  "perApp":{"pkg":{"route":"OVERLAY|MODULE|UNSUPPORTED|NONE", "userEnabled":true,
 *                   "versionKey":"...", "colors":n, "restartNeeded":false, "error":"..."}}}
 * </pre>
 * {@code bootCount} is Settings.Global.BOOT_COUNT at the last apply or restore and
 * {@code appliedBootCount} the one at the last apply, so a restore after a reboot shows.
 * <p>
 * Every change goes through {@link #update}: read, change and an atomic write (temporary file,
 * then rename) under one lock, so the job queue and the screens never lose each other's changes.
 * The first read migrates the switches of older versions (a preference and
 * {@code *.amoled.ids.off} files).
 */
public final class AmoledState {

    public static final int VERSION = 1;
    static final String FOLDER = "amoled";
    static final String FILE = "state.json";
    static final String IDS = "ids";
    static final String OFF = ".off";
    private static final String TAG = "SubstratumAmoled";
    private static final Object LOCK = new Object();

    /**
     * How an app gets its AMOLED colors: a fabricated overlay, the LSPosed module (by resource
     * id), neither (it refuses both), or nothing applied.
     */
    public enum Route {
        OVERLAY, MODULE, UNSUPPORTED, NONE;

        static Route parse(String value) {
            for (Route route : values()) if (route.name().equals(value)) return route;
            return NONE;
        }
    }

    public static final class App {
        public Route route = Route.NONE;
        /**
         * The user's switch: off keeps the app stock through applies and reboots
         */
        public boolean userEnabled = true;
        /**
         * {@link XposedBackend#versionKey} of the app version the colors were read from
         */
        public String versionKey;
        public int colors;
        /**
         * Module colors changed but the app was not safe to restart: they show from its next
         * start
         */
        public boolean restartNeeded;
        public String error;

        public App copy() {
            App app = new App();
            app.route = route;
            app.userEnabled = userEnabled;
            app.versionKey = versionKey;
            app.colors = colors;
            app.restartNeeded = restartNeeded;
            app.error = error;
            return app;
        }

        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("route", route.name());
            o.put("userEnabled", userEnabled);
            if (versionKey != null) o.put("versionKey", versionKey);
            o.put("colors", colors);
            o.put("restartNeeded", restartNeeded);
            if (error != null) o.put("error", error);
            return o;
        }

        static App fromJson(JSONObject o) {
            App app = new App();
            app.route = Route.parse(o.optString("route", Route.NONE.name()));
            app.userEnabled = o.optBoolean("userEnabled", true);
            app.versionKey = string(o, "versionKey");
            app.colors = o.optInt("colors", 0);
            app.restartNeeded = o.optBoolean("restartNeeded", false);
            app.error = string(o, "error");
            return app;
        }
    }

    public boolean enabled;
    public String rulesTheme;
    public long appliedAt;
    public long restoredAt;
    public int bootCount;
    public int appliedBootCount;
    public final Map<String, App> perApp = new TreeMap<>();

    /**
     * The entry of an app, created (switched on, nothing applied) if there is none.
     */
    public App app(String pkg) {
        App app = perApp.get(pkg);
        if (app == null) perApp.put(pkg, app = new App());
        return app;
    }

    public boolean isUserEnabled(String pkg) {
        App app = perApp.get(pkg);
        return app == null || app.userEnabled;
    }

    /**
     * Apps that have AMOLED colors (an overlay or module colors) and are switched on
     */
    public int countThemed() {
        int n = 0;
        for (App app : perApp.values()) {
            if (app.userEnabled && (app.route == Route.OVERLAY || app.route == Route.MODULE)) n++;
        }
        return n;
    }

    /**
     * AMOLED was restored after a reboot that followed the last apply
     */
    public boolean survivedReboot() {
        return enabled && appliedAt > 0 && restoredAt > appliedAt && bootCount > appliedBootCount;
    }

    // Reading and writing

    public static AmoledState read(Context context) {
        synchronized (LOCK) {
            return load(context);
        }
    }

    /**
     * Read, change and write the state as one step.
     *
     * @return The state as written
     */
    public static AmoledState update(Context context, Consumer<AmoledState> change) {
        synchronized (LOCK) {
            AmoledState state = load(context);
            change.accept(state);
            write(context, state);
            return state;
        }
    }

    /**
     * Whether a state file exists (it does after the first read)
     */
    public static boolean exists(Context context) {
        return file(context).exists();
    }

    /**
     * Where the module colors of a switched-off app are kept, to be put back when it is switched
     * on again.
     */
    public static File idsCopy(Context context, String pkg) {
        return new File(idsDir(context), pkg + ".ids");
    }

    static File idsDir(Context context) {
        return new File(new File(context.getFilesDir(), FOLDER), IDS);
    }

    public static int currentBootCount(Context context) {
        try {
            return Settings.Global.getInt(context.getContentResolver(),
                    Settings.Global.BOOT_COUNT, 0);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static File file(Context context) {
        return new File(new File(context.getFilesDir(), FOLDER), FILE);
    }

    private static AmoledState load(Context context) {
        File file = file(context);
        if (file.exists()) {
            try {
                return fromJson(new String(readAll(file), StandardCharsets.UTF_8));
            } catch (IOException | JSONException | RuntimeException e) {
                Log.w(TAG, "Unreadable " + file + ", starting over: " + e);
                //noinspection ResultOfMethodCallIgnored
                file.renameTo(new File(file.getParentFile(), FILE + ".bad"));
            }
        }
        return migrate(context);
    }

    private static boolean write(Context context, AmoledState state) {
        File file = file(context);
        File dir = file.getParentFile();
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File temp = new File(dir, FILE + ".tmp");
        try {
            byte[] data = state.toJson().getBytes(StandardCharsets.UTF_8);
            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write(data);
                out.getFD().sync();
            }
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException | JSONException | RuntimeException e) {
            Log.e(TAG, "Could not write " + file, e);
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            return false;
        }
    }

    // JSON

    String toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("version", VERSION);
        o.put("enabled", enabled);
        if (rulesTheme != null) o.put("rulesTheme", rulesTheme);
        o.put("appliedAt", appliedAt);
        o.put("restoredAt", restoredAt);
        o.put("bootCount", bootCount);
        o.put("appliedBootCount", appliedBootCount);
        JSONObject apps = new JSONObject();
        for (Map.Entry<String, App> entry : perApp.entrySet()) {
            apps.put(entry.getKey(), entry.getValue().toJson());
        }
        o.put("perApp", apps);
        return o.toString(1);
    }

    static AmoledState fromJson(String json) throws JSONException {
        JSONObject o = new JSONObject(json);
        AmoledState state = new AmoledState();
        state.enabled = o.optBoolean("enabled", false);
        state.rulesTheme = string(o, "rulesTheme");
        state.appliedAt = o.optLong("appliedAt", 0);
        state.restoredAt = o.optLong("restoredAt", 0);
        state.bootCount = o.optInt("bootCount", 0);
        state.appliedBootCount = o.optInt("appliedBootCount", 0);
        JSONObject apps = o.optJSONObject("perApp");
        if (apps != null) {
            for (Iterator<String> keys = apps.keys(); keys.hasNext(); ) {
                String pkg = keys.next();
                JSONObject app = apps.optJSONObject(pkg);
                if (app != null) state.perApp.put(pkg, App.fromJson(app));
            }
        }
        return state;
    }

    private static String string(JSONObject o, String key) {
        return o.has(key) && !o.isNull(key) ? o.optString(key) : null;
    }

    // Migration from the switches of older versions

    /**
     * The state of an older install: apps switched off in the preference and apps whose module
     * colors were parked as {@code <pkg>.amoled.ids.off} are off; apps with module colors are on.
     *
     * @param cacheFiles Names in {@link XposedBackend#DIR}
     * @param applied    Whether the AMOLED flag file exists
     */
    static AmoledState migrate(Collection<String> disabled, Collection<String> cacheFiles,
                               boolean applied) {
        AmoledState state = new AmoledState();
        state.enabled = applied;
        for (String name : cacheFiles) {
            String pkg = offPackage(name);
            if (pkg != null) {
                App app = state.app(pkg);
                app.route = Route.MODULE;
                app.userEnabled = false;
            } else if (name.endsWith(XposedBackend.AMOLED_IDS)) {
                state.app(name.substring(0, name.length() - XposedBackend.AMOLED_IDS.length()))
                        .route = Route.MODULE;
            }
        }
        for (String pkg : disabled) state.app(pkg).userEnabled = false;
        return state;
    }

    /**
     * The package of a parked {@code <pkg>.amoled.ids.off} file, or null
     */
    static String offPackage(String fileName) {
        String suffix = XposedBackend.AMOLED_IDS + OFF;
        if (!fileName.endsWith(suffix) || fileName.length() == suffix.length()) return null;
        return fileName.substring(0, fileName.length() - suffix.length());
    }

    private static AmoledState migrate(Context context) {
        SharedPreferences prefs = projekt.substratum.Substratum.getPreferences();
        if (prefs == null) prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> disabled = new HashSet<>(prefs.getStringSet(AmoledRules.PREF_DISABLED_APPS,
                Collections.emptySet()));
        List<String> files = listCache();
        boolean applied = new File(XposedBackend.DIR, XposedBackend.AMOLED_FLAG).exists();
        AmoledState state = migrate(disabled, files, applied);
        // Keep the parked module colors, so switching such an app on needs no apply
        List<String> parked = new ArrayList<>();
        for (String name : files) {
            String pkg = offPackage(name);
            if (pkg == null) continue;
            parked.add(XposedBackend.DIR + '/' + name);
            try {
                File copy = idsCopy(context, pkg);
                //noinspection ResultOfMethodCallIgnored
                copy.getParentFile().mkdirs();
                Files.write(copy.toPath(), readAll(new File(XposedBackend.DIR, name)));
            } catch (IOException | RuntimeException e) {
                Log.w(TAG, "Could not keep the colors of " + pkg + ": " + e);
            }
        }
        if (!write(context, state)) return state;
        prefs.edit().remove(AmoledRules.PREF_DISABLED_APPS).apply();
        if (!parked.isEmpty()) {
            StringBuilder command = new StringBuilder("rm -f");
            for (String path : parked) command.append(" '").append(path).append('\'');
            Runnable delete = () -> Root.runCommand(command.toString());
            if (Looper.myLooper() == Looper.getMainLooper()) {
                new Thread(delete, "AmoledStateMigration").start();
            } else {
                delete.run();
            }
        }
        Log.i(TAG, "state migrated: " + state.perApp.size() + " apps, " + parked.size() +
                " parked color lists");
        return state;
    }

    private static List<String> listCache() {
        List<String> names = new ArrayList<>();
        String[] files = new File(XposedBackend.DIR).list();
        if (files != null) {
            Collections.addAll(names, files);
        } else if (Looper.myLooper() != Looper.getMainLooper()) {
            String out = Root.runCommand("ls -1 " + XposedBackend.DIR + " 2>/dev/null");
            if (out != null) {
                for (String name : out.split("\\s+")) if (!name.isEmpty()) names.add(name);
            }
        }
        return names;
    }

    private static byte[] readAll(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return out.toByteArray();
        }
    }
}
