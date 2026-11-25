/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.util.helpers;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import projekt.substratum.R;
import projekt.substratum.common.platform.SystemAmoled;

/**
 * Menu entry for {@link SystemAmoled}: apply or remove AMOLED for every app, with progress.
 */
public final class SystemAmoledDialog {

    private SystemAmoledDialog() {
    }

    public static void show(Activity activity) {
        if (!Root.checkRootAccess()) {
            Toast.makeText(activity, R.string.system_amoled_needs_root, Toast.LENGTH_LONG).show();
            return;
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.system_amoled_title)
                .setMessage(R.string.system_amoled_message)
                .setPositiveButton(R.string.system_amoled_apply, (d, w) -> run(activity, true))
                .setNeutralButton(R.string.system_amoled_remove, (d, w) -> run(activity, false))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void run(Activity activity, boolean apply) {
        AlertDialog progress = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.system_amoled_title)
                .setMessage(apply ? R.string.system_amoled_working : R.string.system_amoled_removing)
                .setCancelable(false)
                .show();
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            String result;
            if (apply) {
                int[] done = SystemAmoled.applyAll(activity.getApplicationContext(),
                        (pkg, i, total) -> main.post(() -> progress.setMessage(
                                activity.getString(R.string.system_amoled_progress, i + 1, total,
                                        pkg))));
                result = activity.getString(R.string.system_amoled_done, done[0], done[1]);
            } else {
                SystemAmoled.removeAll(activity.getApplicationContext());
                result = activity.getString(R.string.system_amoled_removed);
            }
            main.post(() -> {
                progress.dismiss();
                Toast.makeText(activity.getApplicationContext(), result, Toast.LENGTH_LONG).show();
            });
        }).start();
    }
}
