/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.xposed;

import android.app.AndroidAppHelper;
import android.app.Application;
import android.content.Context;
import android.provider.Settings;

import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import projekt.substratum.BuildConfig;
import projekt.substratum.common.platform.XposedBackend;

/**
 * System UI reports what the module did in it, in Settings.Global
 * {@link XposedBackend#STATUS_KEY}:
 * {@code v=<versionCode>;t=<millis>;qs=<0|1>;layout=<0|1>;battery=<0|1>;guard=<ok|tripped|off>}.
 * System UI holds WRITE_SECURE_SETTINGS. Written on a background thread; failures are only
 * logged.
 */
final class ModuleStatus {

    private static final String TAG = "SubstratumXposed: status: ";
    private static final AtomicBoolean REPORTED = new AtomicBoolean();

    private ModuleStatus() {
    }

    /**
     * The hooks are set up: report once System UI's application is created.
     */
    static void reportWhenCreated(boolean qs, boolean layout, boolean battery) {
        try {
            XposedHelpers.findAndHookMethod(Application.class, "onCreate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!REPORTED.compareAndSet(false, true)) return;
                    Context context = (Context) param.thisObject;
                    startThread(() -> write(context, qs, layout, battery, HookGuard.OK));
                }
            });
        } catch (Throwable t) {
            log("Application#onCreate not hooked: " + t);
        }
        // In case the application never calls through to Application#onCreate
        whenApplicationExists(20_000, qs, layout, battery, HookGuard.OK);
    }

    /**
     * The kill switch or the crash loop guard skipped the hooks: report without hooking
     * anything, once the application exists.
     */
    static void reportSkipped(String guard) {
        whenApplicationExists(0, false, false, false, guard);
    }

    private static void whenApplicationExists(long delayMs, boolean qs, boolean layout,
                                              boolean battery, String guard) {
        startThread(() -> {
            try {
                if (delayMs > 0) Thread.sleep(delayMs);
                for (int i = 0; i < 240 && !REPORTED.get(); i++) {
                    Context app = AndroidAppHelper.currentApplication();
                    if (app != null) {
                        if (REPORTED.compareAndSet(false, true)) {
                            write(app, qs, layout, battery, guard);
                        }
                        return;
                    }
                    Thread.sleep(500);
                }
            } catch (Throwable t) {
                log("not reported: " + t);
            }
        });
    }

    private static void startThread(Runnable task) {
        try {
            Thread thread = new Thread(task, "RebornStatus");
            thread.setDaemon(true);
            thread.start();
        } catch (Throwable t) {
            log("not reported: " + t);
        }
    }

    private static void write(Context context, boolean qs, boolean layout, boolean battery,
                              String guard) {
        try {
            String status = "v=" + BuildConfig.VERSION_CODE +
                    ";t=" + System.currentTimeMillis() +
                    ";qs=" + (qs ? 1 : 0) +
                    ";layout=" + (layout ? 1 : 0) +
                    ";battery=" + (battery ? 1 : 0) +
                    ";guard=" + guard;
            Settings.Global.putString(context.getContentResolver(), XposedBackend.STATUS_KEY,
                    status);
            log(status);
        } catch (Throwable t) {
            log("not reported: " + t);
        }
    }

    private static void log(String message) {
        try {
            XposedBridge.log(TAG + message);
        } catch (Throwable ignored) {
            // nothing to log to
        }
    }
}
