/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.util.helpers;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import projekt.substratum.R;
import projekt.substratum.common.Broadcasts;

/**
 * Installs the starter theme (Reborn AMOLED) that ships inside the Substratum APK, so a fresh
 * install has something to apply. The build copies the theme module's APK to
 * assets/starter/reborn_amoled.apk.
 */
public final class StarterTheme {

    private static final String ASSET = "starter/reborn_amoled.apk";

    private StarterTheme() {
    }

    public static void install(Context context) {
        Context app = context.getApplicationContext();
        Toast.makeText(app, R.string.install_starter_theme_running, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            boolean installed = false;
            try {
                if (Root.checkRootAccess()) {
                    File apk = copyAsset(app, new File(app.getCacheDir(), "reborn_amoled.apk"));
                    String result = Root.runCommand("pm install -r '" + apk.getAbsolutePath() + "'");
                    installed = result != null && result.contains("Success");
                    //noinspection ResultOfMethodCallIgnored
                    apk.delete();
                } else {
                    // Without root, hand the APK to the system installer
                    File dir = app.getExternalCacheDir();
                    if (dir != null) {
                        File apk = copyAsset(app, new File(dir, "reborn_amoled.apk"));
                        Uri uri = FileProvider.getUriForFile(app,
                                app.getPackageName() + ".provider", apk);
                        Intent intent = new Intent(Intent.ACTION_VIEW)
                                .setDataAndType(uri, "application/vnd.android.package-archive")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                                        Intent.FLAG_ACTIVITY_NEW_TASK);
                        app.startActivity(intent);
                        return;
                    }
                }
            } catch (IOException | RuntimeException e) {
                installed = false;
            }
            final boolean success = installed;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (success) {
                    Broadcasts.sendRefreshMessage(app);
                } else {
                    Toast.makeText(app, R.string.install_starter_theme_failed,
                            Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private static File copyAsset(Context context, File target) throws IOException {
        try (InputStream in = context.getAssets().open(ASSET);
             OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        }
        return target;
    }
}
