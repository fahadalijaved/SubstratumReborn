/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.common.platform;

import android.content.Context;
import android.os.Build;

import java.io.File;
import java.lang.reflect.Method;

import projekt.substratum.R;

/**
 * Which Android the app runs on, for copy that names the stock look: One UI on Samsung builds,
 * stock Android everywhere else (AOSP-based ROMs). Unlike {@link RebornStatus}, nothing here
 * blocks: one system property read through reflection (no shell, no process), cached, so it is
 * safe on the main thread.
 */
public final class RebornPlatform {

    private static volatile Boolean oneUi;

    private RebornPlatform() {
    }

    /**
     * True on One UI (ro.build.version.oneui is set). Where the property cannot be read, a Samsung
     * build is recognised by its maker and its device permissions file.
     */
    public static boolean isOneUi() {
        Boolean cached = oneUi;
        if (cached != null) return cached;
        boolean result;
        String prop = property("ro.build.version.oneui");
        if (prop != null) {
            result = RebornStatus.oneUiName(prop) != null;
        } else {
            result = "samsung".equalsIgnoreCase(Build.MANUFACTURER) ||
                    new File("/system/etc/permissions/com.samsung.device.xml").exists();
        }
        oneUi = result;
        return result;
    }

    /**
     * "stock One UI" or "stock Android": the look the stock side of a preview shows.
     */
    public static String stockLook(Context context) {
        return context.getString(isOneUi() ? R.string.reborn_platform_stock_oneui
                : R.string.reborn_platform_stock_aosp);
    }

    /**
     * "One UI blue" or "the system accent": the accent apps keep when the look sets none.
     */
    public static String stockAccent(Context context) {
        return context.getString(isOneUi() ? R.string.reborn_platform_accent_oneui
                : R.string.reborn_platform_accent_aosp);
    }

    /**
     * A system property ("" when unset), or null when it cannot be read.
     */
    private static String property(String key) {
        try {
            Class<?> props = Class.forName("android.os.SystemProperties");
            Method get = props.getMethod("get", String.class, String.class);
            Object value = get.invoke(null, key, "");
            return value != null ? value.toString() : "";
        } catch (Throwable t) {
            return null;
        }
    }
}
