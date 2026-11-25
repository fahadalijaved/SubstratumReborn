/*
 * Copyright (c) 2016-2019 Projekt Substratum
 * This file is part of Substratum.
 *
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.xposed;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Lets a theme replace the One UI battery, which SystemUI draws in code rather than from
 * resources.
 * <p>
 * Theme contract, inside a {@code com.android.systemui} overlay:
 * <ul>
 * <li>{@code drawable/substratum_battery}: drawn in place of the battery. Its level is set to
 * battery percent x 100 (0..10000, the standard drawable level range), so level-list and clip
 * drawables follow the charge.</li>
 * <li>{@code drawable/substratum_battery_charging} (optional): used while charging.</li>
 * <li>{@code bool/substratum_battery_tint} (optional, default true): tint with the status bar
 * icon color so it follows light and dark status bars.</li>
 * </ul>
 * Without {@code substratum_battery} the stock battery is left alone.
 */
final class BatteryHook {

    private static final String TAG = "SubstratumXposed: ";
    private static final String METER =
            "com.android.systemui.battery.SamsungBatteryMeterDrawable";
    private static final String COMPOSE_DRAW =
            "com.android.systemui.battery.view.SamsungBatteryComposeViewKt$$ExternalSyntheticLambda0";

    private static volatile Resources sourceRes;
    private static volatile int batteryId;
    private static volatile int chargingId;
    private static volatile boolean tint = true;
    private static volatile boolean loggedFirstDraw;
    private static Drawable battery;
    private static Drawable charging;

    private BatteryHook() {
    }

    /**
     * Remember the themed battery of an enabled overlay, if it ships one.
     */
    static void offer(Resources overlayRes, String overlayPackage) {
        int id = overlayRes.getIdentifier("substratum_battery", "drawable", overlayPackage);
        if (id == 0) return;
        int tintId = overlayRes.getIdentifier("substratum_battery_tint", "bool", overlayPackage);
        synchronized (BatteryHook.class) {
            sourceRes = overlayRes;
            batteryId = id;
            chargingId = overlayRes.getIdentifier(
                    "substratum_battery_charging", "drawable", overlayPackage);
            tint = tintId == 0 || overlayRes.getBoolean(tintId);
            battery = null;
            charging = null;
        }
        XposedBridge.log(TAG + "themed battery from " + overlayPackage);
    }

    static void hook(ClassLoader classLoader) {
        hookCompose(classLoader);
        hookDrawable(classLoader);
    }

    /**
     * One UI 9 status bar: the battery chip is drawn by a Compose draw lambda that reads a
     * BatteryLayoutState. Draw the themed battery onto the underlying canvas instead.
     */
    private static void hookCompose(ClassLoader classLoader) {
        Class<?> lambda = XposedHelpers.findClassIfExists(COMPOSE_DRAW, classLoader);
        if (lambda == null) {
            XposedBridge.log(TAG + COMPOSE_DRAW + " not found, Compose battery left alone");
            return;
        }
        Class<?> unit = XposedHelpers.findClassIfExists("kotlin.Unit", classLoader);
        final Object unitInstance = unit != null ?
                XposedHelpers.getStaticObjectField(unit, "INSTANCE") : null;
        XposedHelpers.findAndHookMethod(lambda, "invoke", Object.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (batteryId == 0 || unitInstance == null) return;
                try {
                    Object state = XposedHelpers.getObjectField(param.thisObject, "f$1");
                    Drawable themed = pick(XposedHelpers.getBooleanField(state, "isCharging"));
                    if (themed == null) return;
                    Object scope = param.args[0];
                    long size = (long) XposedHelpers.callMethod(scope, "getSize-NH-jbRc");
                    int width = Math.round(Float.intBitsToFloat((int) (size >>> 32)));
                    int height = Math.round(Float.intBitsToFloat((int) size));
                    Canvas canvas = nativeCanvas(scope);
                    if (canvas == null || width <= 0 || height <= 0) return;
                    int level = XposedHelpers.getIntField(state, "displayLevel");
                    int color = XposedHelpers.getIntField(state, "frameColor");
                    draw(themed, canvas, new Rect(0, 0, width, height), level, color);
                    param.setResult(unitInstance);
                } catch (Throwable t) {
                    XposedBridge.log(TAG + "themed Compose battery failed, drawing stock: " + t);
                }
            }
        });
        XposedBridge.log(TAG + "Compose battery hook installed");
    }

    /**
     * Compose DrawScope -> android.graphics.Canvas (AndroidCanvas.internalCanvas).
     */
    private static Canvas nativeCanvas(Object drawScope) {
        Object drawContext = XposedHelpers.callMethod(drawScope, "getDrawContext");
        Object composeCanvas = XposedHelpers.callMethod(drawContext, "getCanvas");
        Object canvas = XposedHelpers.getObjectField(composeCanvas, "internalCanvas");
        return canvas instanceof Canvas ? (Canvas) canvas : null;
    }

    /**
     * Older layouts draw SamsungBatteryMeterDrawable directly.
     */
    private static void hookDrawable(ClassLoader classLoader) {
        Class<?> meter = XposedHelpers.findClassIfExists(METER, classLoader);
        if (meter == null) {
            XposedBridge.log(TAG + METER + " not found, battery drawable left alone");
            return;
        }
        XposedHelpers.findAndHookMethod(meter, "draw", Canvas.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (batteryId == 0) return;
                try {
                    Drawable meterDrawable = (Drawable) param.thisObject;
                    Drawable themed = pick(isCharging(meterDrawable));
                    if (themed == null) return;
                    Rect bounds = new Rect(meterDrawable.getBounds());
                    if (bounds.isEmpty()) return;
                    int level = (int) XposedHelpers.callMethod(meterDrawable, "getBatteryLevel");
                    int color = XposedHelpers.getIntField(meterDrawable, "iconTint");
                    draw(themed, (Canvas) param.args[0], bounds, level, color);
                    param.setResult(null);
                } catch (Throwable t) {
                    // Fall back to the stock battery rather than break the status bar
                    XposedBridge.log(TAG + "themed battery failed, drawing stock: " + t);
                }
            }
        });
        XposedBridge.log(TAG + "battery drawable hook installed");
    }

    private static void draw(Drawable themed, Canvas canvas, Rect bounds, int level, int color) {
        if (!loggedFirstDraw) {
            loggedFirstDraw = true;
            XposedBridge.log(TAG + "themed battery drawn in " + bounds + " at " + level + "%");
        }
        themed.setLevel(Math.max(0, Math.min(100, level)) * 100);
        themed.setBounds(bounds);
        if (tint) themed.setTintList(ColorStateList.valueOf(color));
        themed.draw(canvas);
    }

    private static boolean isCharging(Object meterDrawable) {
        try {
            Object state = XposedHelpers.getObjectField(meterDrawable, "batteryState");
            return state != null && XposedHelpers.getBooleanField(state, "charging");
        } catch (Throwable t) {
            return false;
        }
    }

    private static synchronized Drawable pick(boolean isCharging) {
        if (sourceRes == null) return null;
        if (isCharging && chargingId != 0) {
            if (charging == null) charging = sourceRes.getDrawable(chargingId, null).mutate();
            return charging;
        }
        if (battery == null) battery = sourceRes.getDrawable(batteryId, null).mutate();
        return battery;
    }
}
