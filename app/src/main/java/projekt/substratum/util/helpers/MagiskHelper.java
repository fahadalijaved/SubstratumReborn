/*
 * Copyright (c) 2016-2019 Projekt Substratum
 * This file is part of Substratum.
 *
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.util.helpers;

import android.content.Context;
import android.widget.Toast;

import projekt.substratum.BuildConfig;
import projekt.substratum.R;
import projekt.substratum.Substratum;
import projekt.substratum.common.References;

public class MagiskHelper {

    private static final String TAG = MagiskHelper.class.getSimpleName();
    private static final String MAGISK_DIR = References.getMagiskDirectory();

    /**
     * post-fs-data script shipped with the module.
     * <p>
     * Magisk mounts module files itself, and so does KernelSU/APatch when a metamodule is
     * installed. Recent KernelSU builds without a metamodule mount nothing, so in that case the
     * script overlays the module's app directory onto /system/app on its own.
     */
    private static final String POST_FS_DATA =
            "#!/system/bin/sh\n" +
                    "MODDIR=${0%/*}\n" +
                    "# Magisk and metamodules mount module files on their own\n" +
                    "[ -e /data/adb/magisk ] && exit 0\n" +
                    "[ -e /data/adb/metamodule ] && exit 0\n" +
                    "ls \"$MODDIR\"/system/app/*.apk >/dev/null 2>&1 || exit 0\n" +
                    "chcon -R u:object_r:system_file:s0 \"$MODDIR/system\"\n" +
                    "mount -t overlay -o ro,lowerdir=\"$MODDIR/system/app\":/system/app " +
                    "KSU /system/app\n";

    private static void installModule(final Context context) {
        // When the directory is "/" there is no module support and we're falling back to
        // modifying system.
        if (MAGISK_DIR.equals("/"))
            return;

        Substratum.log(TAG, "Root module does not exist, creating!");
        String command = "set -e \n" +
                String.format("mkdir -p %s/system/app\n", MAGISK_DIR) +
                String.format(
                        "printf 'id=substratum\\nname=substratum\\nversion=%s\\nversionCode=%s\\n" +
                                "author=substratum development team\\n" +
                                "description=Systemless overlays for Substratum\\n' " +
                                "> %s/module.prop\n",
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE,
                        MAGISK_DIR
                ) +
                String.format("touch %s/auto_mount\n", MAGISK_DIR) +
                String.format("cat > %s/post-fs-data.sh <<'SUBS_EOF'\n%sSUBS_EOF\n",
                        MAGISK_DIR, POST_FS_DATA) +
                String.format("chmod 755 %s/post-fs-data.sh\n", MAGISK_DIR) +
                String.format("chcon -R u:object_r:system_file:s0 %s/system\n", MAGISK_DIR);
        Root.runCommand(command);
        Toast.makeText(context, R.string.module_placed_reboot_message, Toast.LENGTH_LONG).show();
    }

    public static void handleModule(final Context context) {
        if (!moduleExists())
            installModule(context);
    }

    private static boolean moduleExists() {
        return "1".equals(Root.runCommand(
                String.format("test -f %s/module.prop && echo '1'", MAGISK_DIR)));
    }

}
