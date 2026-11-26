/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.xposed;

import android.content.pm.ApplicationInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Paint;
import android.text.TextPaint;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.TypedValue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import projekt.substratum.common.platform.XposedBackend;

/**
 * AMOLED colors for apps an overlay cannot reach (obfuscated resource names such as Play Store,
 * or apps that refuse overlays), applied inside the app's own process. Only in dark mode.
 * <ul>
 * <li>By resource id, where Android turns a color resource into a value: plain lookups
 * ({@code ResourcesImpl.getValue}), styled attributes ({@code TypedArray.getColor}) and the
 * drawable / color state list loaders. (XResources' by-id value replacement does not reach
 * these paths.) Only in the app's own resources: a library or another package loaded into the
 * process can use the same ids for something else.</li>
 * <li>Color state lists the rules name a color for become that single color.</li>
 * <li>By color, where it is drawn ({@code Paint.setColor}), in apps with Compose: Compose
 * screens hold their palette as constants in code, which never pass through resources. The
 * app's own dark surface colors (opaque stock dark values of its color resources) are drawn as
 * their AMOLED values, for filled shapes only (never text or outlines), and only where the stock
 * value has a single AMOLED value.</li>
 * </ul>
 * The color list is written by "AMOLED for every app": a {@code # key=} line with the app version
 * it was read from (a list for another version is ignored, so an updated app stays stock until
 * AMOLED is applied again), then one line per color resource,
 * {@code id light night [stockNight|csl]}.
 */
final class AppAmoledHook {

    private static final String TAG = "SubstratumXposed: AMOLED: ";
    private static final String KEY = "# key=";
    private static final String STATE_LIST = "csl";
    private static final String[] COMPOSE_VIEWS = {
            "androidx.compose.ui.platform.AndroidComposeView",
            "androidx.compose.ui.platform.ComposeView"};
    private static String target;
    private static SparseArray<int[]> byId; // id -> {light, night}
    private static SparseBooleanArray stateLists; // color state lists with a rule color
    private static Map<Integer, Integer> byColor; // stock dark color -> AMOLED color
    // Resources or ResourcesImpl -> whether it holds the target app's own resources
    private static final Map<Object, Boolean> OWNED = new WeakHashMap<>();

    private AppAmoledHook() {
    }

    static void hook(XC_LoadPackage.LoadPackageParam lpparam) throws Exception {
        if (byId != null) return; // once per process
        String packageName = lpparam.packageName;
        File file = new File(XposedBackend.DIR, packageName + XposedBackend.AMOLED_IDS);
        if (!file.exists()) return;
        SparseArray<int[]> ids = new SparseArray<>();
        SparseBooleanArray lists = new SparseBooleanArray();
        Map<Integer, Integer> surfaces = new HashMap<>();
        Set<Integer> ambiguous = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            if (!sameVersion(reader.readLine(), lpparam.appInfo)) {
                XposedBridge.log(TAG + "stale ids for " + packageName +
                        ", left stock until AMOLED is applied again");
                return;
            }
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                String[] parts = line.split(" ");
                if (parts.length < 3) continue;
                try {
                    int id = hex(parts[0]);
                    int night = hex(parts[2]);
                    ids.put(id, new int[]{hex(parts[1]), night});
                    if (parts.length < 4) continue;
                    if (STATE_LIST.equals(parts[3])) {
                        lists.put(id, true);
                        continue;
                    }
                    int stock = hex(parts[3]);
                    // Only opaque surfaces: translucent layers and text keep their colors
                    if (stock >>> 24 != 0xff || ambiguous.contains(stock)) continue;
                    // Material's outline greys drawn as filled shapes (Compose borders of chips
                    // and outlined buttons) never turn into the page (lists of older versions)
                    if (outline(stock) && brightest(night) <= 0x20) continue;
                    Integer previous = surfaces.put(stock, night);
                    if (previous != null && previous != night) {
                        // Two resources share this stock value but get different AMOLED
                        // values: which one a drawn color stands for is unknown, keep it
                        surfaces.remove(stock);
                        ambiguous.add(stock);
                    }
                } catch (RuntimeException ignored) {
                    // malformed line
                }
            }
        }
        if (ids.size() == 0) return;
        surfaces.entrySet().removeIf(e -> e.getKey().equals(e.getValue()));
        target = packageName;
        byId = ids;
        stateLists = lists;
        byColor = surfaces;
        Class<?> impl = XposedHelpers.findClass("android.content.res.ResourcesImpl", null);

        // Resources.getColor / getColorStateList / getDrawable for plain colors
        XposedHelpers.findAndHookMethod(impl, "getValue", int.class, TypedValue.class,
                boolean.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        swap(param.thisObject, (int) param.args[0], (TypedValue) param.args[1]);
                    }
                });
        // Colors set through styles and layout attributes
        XposedHelpers.findAndHookMethod(TypedArray.class, "getColor", int.class, int.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        TypedArray array = (TypedArray) param.thisObject;
                        int id = array.getResourceId((int) param.args[0], 0);
                        int[] c = byId.get(id);
                        if (c == null) return;
                        Resources res = array.getResources();
                        if (night(res.getConfiguration()) && owned(res, id)) {
                            param.setResult(c[1]);
                        }
                    }
                });
        // android:background="@color/..." and color state lists read from attributes
        XC_MethodHook loader = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                for (Object arg : param.args) {
                    if (arg instanceof TypedValue) {
                        TypedValue value = (TypedValue) arg;
                        swap(param.thisObject, value.resourceId, value);
                        return;
                    }
                }
            }
        };
        hookAll(impl, "loadDrawable", loader);
        hookAll(impl, "loadColorStateList", loader);
        // Color state lists with a rule color: that color in every state
        if (lists.size() > 0) hookAll(impl, "loadColorStateList", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (param.getThrowable() != null) return;
                int argId = 0, valueId = 0;
                Resources wrapper = null;
                for (Object arg : param.args) {
                    if (arg instanceof Resources) {
                        wrapper = (Resources) arg;
                    } else if (arg instanceof Integer) {
                        argId = (Integer) arg;
                    } else if (arg instanceof TypedValue) {
                        valueId = ((TypedValue) arg).resourceId;
                    }
                }
                int id = argId != 0 ? argId : valueId;
                if (id == 0 || !stateLists.get(id)) return;
                int[] c = byId.get(id);
                if (c == null) return;
                Configuration config = wrapper != null ? wrapper.getConfiguration() :
                        configuration(param.thisObject);
                if (night(config) && owned(param.thisObject, id)) {
                    param.setResult(ColorStateList.valueOf(c[1]));
                }
            }
        });
        // Compose and other code-defined colors, where they are drawn
        boolean compose = hasCompose(lpparam.classLoader);
        if (compose && !surfaces.isEmpty()) {
            XposedHelpers.findAndHookMethod(Paint.class, "setColor", int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Integer mapped = byColor.get((Integer) param.args[0]);
                            if (mapped != null && surface(param.thisObject) && systemNight()) {
                                param.args[0] = mapped;
                            }
                        }
                    });
            try {
                XposedHelpers.findAndHookMethod(Paint.class, "setColor", long.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                long color = (long) param.args[0];
                                if ((color & 0x3fL) != 0) return; // not sRGB
                                Integer mapped = byColor.get((int) (color >>> 32));
                                if (mapped != null && surface(param.thisObject) &&
                                        systemNight()) {
                                    param.args[0] = ((long) mapped) << 32;
                                }
                            }
                        });
            } catch (Throwable ignored) {
                // Before Android 10
            }
        }
        XposedBridge.log(TAG + ids.size() + " colors by id, " + lists.size() +
                " color state lists, " + (compose ? surfaces.size() + " surface colors" :
                "no Compose, no surface colors") + " in " + packageName);
    }

    /**
     * Whether the app contains Compose. ComposeView keeps its name when a layout names it, even
     * in apps that shrink and rename their classes.
     */
    private static boolean hasCompose(ClassLoader loader) {
        for (String name : COMPOSE_VIEWS) {
            try {
                if (XposedHelpers.findClassIfExists(name, loader) != null) return true;
            } catch (Throwable ignored) {
                // a class it needs is missing: not usable
            }
        }
        return false;
    }

    /**
     * Whether the list's {@code # key=} header matches the running app. Without a readable
     * version code, only the APK path is compared.
     */
    private static boolean sameVersion(String header, ApplicationInfo appInfo) {
        if (header == null || appInfo == null || appInfo.sourceDir == null) return false;
        header = header.trim();
        if (!header.startsWith(KEY)) return false;
        String key = header.substring(KEY.length()).trim();
        Long version = versionCode(appInfo);
        if (version != null) return key.equals(XposedBackend.versionKey(appInfo.sourceDir,
                version));
        int bar = key.lastIndexOf('|');
        return bar > 0 && key.substring(0, bar).equals(appInfo.sourceDir);
    }

    /**
     * ApplicationInfo's hidden version code, or null if it cannot be read.
     */
    private static Long versionCode(ApplicationInfo appInfo) {
        try {
            return XposedHelpers.getLongField(appInfo, "longVersionCode");
        } catch (Throwable ignored) {
            // below
        }
        try {
            return (long) XposedHelpers.getIntField(appInfo, "versionCode");
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Material's dark outline (#8E918F) and outline variant (#444746), or a wallpaper-tinted grey
     * near them
     */
    private static boolean outline(int color) {
        return near(color, 0x444746) || near(color, 0x8e918f);
    }

    private static boolean near(int color, int rgb) {
        return Math.abs(((color >> 16) & 0xff) - ((rgb >> 16) & 0xff)) <= 0x0c &&
                Math.abs(((color >> 8) & 0xff) - ((rgb >> 8) & 0xff)) <= 0x0c &&
                Math.abs((color & 0xff) - (rgb & 0xff)) <= 0x0c;
    }

    private static int brightest(int color) {
        return Math.max((color >> 16) & 0xff, Math.max((color >> 8) & 0xff, color & 0xff));
    }

    private static int hex(String value) {
        return (int) Long.parseLong(value.substring(2), 16);
    }

    private static void swap(Object res, int id, TypedValue value) {
        if (value == null || value.type < TypedValue.TYPE_FIRST_COLOR_INT ||
                value.type > TypedValue.TYPE_LAST_COLOR_INT) return;
        int[] c = byId.get(id);
        if (c != null && night(configuration(res)) && owned(res, id)) value.data = c[1];
    }

    /**
     * Whether a Resources or ResourcesImpl holds the target app's own resources, decided at its
     * first matching id and remembered for the object.
     */
    private static boolean owned(Object res, int id) {
        synchronized (OWNED) {
            Boolean known = OWNED.get(res);
            if (known != null) return known;
        }
        boolean own;
        try {
            Object pkg = res instanceof Resources ?
                    ((Resources) res).getResourcePackageName(id) :
                    XposedHelpers.callMethod(res, "getResourcePackageName", id);
            own = target.equals(pkg);
        } catch (Throwable t) {
            own = false;
        }
        synchronized (OWNED) {
            OWNED.put(res, own);
        }
        return own;
    }

    private static Configuration configuration(Object res) {
        try {
            return res instanceof Resources ? ((Resources) res).getConfiguration() :
                    (Configuration) XposedHelpers.callMethod(res, "getConfiguration");
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Filled shapes only: text and outlines keep their colors.
     */
    private static boolean surface(Object paint) {
        return !(paint instanceof TextPaint) &&
                ((Paint) paint).getStyle() == Paint.Style.FILL;
    }

    private static boolean systemNight() {
        return night(Resources.getSystem().getConfiguration());
    }

    private static boolean night(Configuration config) {
        return config != null && (config.uiMode & Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES;
    }

    private static void hookAll(Class<?> owner, String name, XC_MethodHook hook) {
        try {
            XposedBridge.hookAllMethods(owner, name, hook);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "could not hook " + name + ": " + t);
        }
    }
}
