/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.util.helpers;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import projekt.substratum.R;
import projekt.substratum.services.system.SystemAmoledService;

/**
 * Apply or remove AMOLED for every app, with live progress. The work runs in
 * {@link SystemAmoledService}, so it continues (with its notification) if this screen is left.
 */
public final class SystemAmoledDialog {

    private SystemAmoledDialog() {
    }

    /**
     * @param themePackage A rules theme (assets/amoled/rules.json), or null for the built-in rules
     */
    public static void show(Activity activity, String themePackage, CharSequence themeName) {
        if (SystemAmoledService.isRunning()) {
            showProgress(activity, true);
            return;
        }
        if (!Root.checkRootAccess()) {
            Toast.makeText(activity, R.string.system_amoled_needs_root, Toast.LENGTH_LONG).show();
            return;
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle(themeName != null ? themeName :
                        activity.getString(R.string.system_amoled_title))
                .setMessage(R.string.system_amoled_message)
                .setPositiveButton(R.string.system_amoled_apply, (d, w) -> {
                    SystemAmoledService.start(activity, true, themePackage);
                    showProgress(activity, true);
                })
                .setNeutralButton(R.string.system_amoled_remove, (d, w) -> {
                    SystemAmoledService.start(activity, false, null);
                    showProgress(activity, false);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public static void show(Activity activity) {
        show(activity, null, null);
    }

    private static void showProgress(Activity activity, boolean apply) {
        View view = LayoutInflater.from(activity).inflate(R.layout.system_amoled_progress, null);
        LinearProgressIndicator bar = view.findViewById(R.id.amoled_progress);
        TextView text = view.findViewById(R.id.amoled_progress_text);
        text.setText(apply ? R.string.system_amoled_working : R.string.system_amoled_removing);
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.system_amoled_title)
                .setView(view)
                .setPositiveButton(R.string.system_amoled_background, null)
                .show();
        LocalBroadcastManager broadcasts = LocalBroadcastManager.getInstance(activity);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getBooleanExtra(SystemAmoledService.EXTRA_FINISHED, false)) {
                    String result = intent.getStringExtra(SystemAmoledService.EXTRA_RESULT);
                    Toast.makeText(context, result, Toast.LENGTH_LONG).show();
                    dialog.dismiss();
                    return;
                }
                int done = intent.getIntExtra(SystemAmoledService.EXTRA_DONE, 0);
                int total = intent.getIntExtra(SystemAmoledService.EXTRA_TOTAL, 0);
                bar.setIndeterminate(false);
                bar.setMax(Math.max(total, 1));
                bar.setProgressCompat(done, true);
                text.setText(context.getString(R.string.system_amoled_progress, done, total,
                        intent.getStringExtra(SystemAmoledService.EXTRA_PACKAGE)));
            }
        };
        broadcasts.registerReceiver(receiver, new IntentFilter(SystemAmoledService.PROGRESS));
        dialog.setOnDismissListener(d -> broadcasts.unregisterReceiver(receiver));
    }
}
