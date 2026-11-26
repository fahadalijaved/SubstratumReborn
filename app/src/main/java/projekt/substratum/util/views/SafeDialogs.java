/*
 * Copyright (c) 2016-2019 Projekt Substratum
 * This file is part of Substratum.
 *
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.util.views;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.util.Log;

/**
 * Dialog helpers for the classic screens' background tasks. A task can finish after its screen
 * was recreated or closed: applying a theme changes the framework's resources, which recreates
 * the open activity. Dismissing the old screen's dialog then throws "not attached to window
 * manager", which used to kill the app before the theme was enabled.
 */
public final class SafeDialogs {

    private static final String TAG = "SafeDialogs";

    private SafeDialogs() {
    }

    /**
     * Dismisses the dialog if it is still showing. A dialog whose window is already gone is
     * ignored.
     */
    public static void dismiss(Dialog dialog) {
        if (dialog == null) return;
        try {
            if (dialog.isShowing()) dialog.dismiss();
        } catch (RuntimeException e) {
            // Its activity was recreated or finished: the window went with it
            Log.w(TAG, "Dialog window already gone: " + e.getMessage());
        }
    }

    /**
     * The same for Reborn's progress dialog wrapper.
     */
    public static void dismiss(RebornProgressDialog dialog) {
        if (dialog != null) dialog.dismiss();
    }

    /**
     * Whether a dialog or a snackbar can still be shown for this context: it belongs to an
     * activity that is neither finishing nor destroyed.
     */
    public static boolean canShow(Context context) {
        Activity activity = activityOf(context);
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }

    private static Activity activityOf(Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) return (Activity) current;
            current = ((ContextWrapper) current).getBaseContext();
        }
        return null;
    }
}
