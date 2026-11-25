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
     * Mount script shipped with the module, used when the root solution mounts nothing itself.
     * <p>
     * Magisk mounts module files on its own, and so does KernelSU/APatch when a metamodule is
     * installed. Recent KernelSU builds without a metamodule leave module files unmounted, and
     * overlayfs refuses /data (casefolded f2fs) as a lower layer. So /system/app is rebuilt on a
     * tmpfs: every stock entry is bind mounted back and each overlay APK is bind mounted next to
     * them. Running it again adds new overlays and drops the ones that were deleted.
     */
    private static final String MOUNT_SCRIPT =
            "#!/system/bin/sh\n" +
                    "MODDIR=${0%/*}\n" +
                    "SRC=$MODDIR/system/app\n" +
                    "T=/dev/substratum_app\n" +
                    "[ -e /data/adb/magisk ] && exit 0\n" +
                    "[ -e /data/adb/metamodule ] && exit 0\n" +
                    "chcon -R u:object_r:system_file:s0 \"$MODDIR/system\"\n" +
                    "if ! grep -q \" /system/app tmpfs \" /proc/mounts; then\n" +
                    "  ls \"$SRC\"/*.apk >/dev/null 2>&1 || exit 0\n" +
                    "  mkdir -p $T\n" +
                    "  mount -t tmpfs -o mode=755 tmpfs $T || exit 1\n" +
                    "  chcon u:object_r:system_file:s0 $T\n" +
                    "  for d in /system/app/*; do\n" +
                    "    n=${d##*/}\n" +
                    "    if [ -d \"$d\" ]; then mkdir \"$T/$n\"; else touch \"$T/$n\"; fi\n" +
                    "    mount --bind \"$d\" \"$T/$n\" || { umount -l $T; exit 2; }\n" +
                    "  done\n" +
                    "  mount --rbind $T /system/app || { umount -l $T; exit 3; }\n" +
                    "fi\n" +
                    "for m in /system/app/_*.apk; do\n" +
                    "  [ -e \"$m\" ] || continue\n" +
                    "  [ -f \"$SRC/${m##*/}\" ] && continue\n" +
                    "  umount -l \"$m\" 2>/dev/null\n" +
                    "  rm -f \"$m\"\n" +
                    "done\n" +
                    "for f in \"$SRC\"/*.apk; do\n" +
                    "  [ -f \"$f\" ] || continue\n" +
                    "  n=${f##*/}\n" +
                    "  grep -q \" /system/app/$n \" /proc/mounts && continue\n" +
                    "  touch \"/system/app/$n\"\n" +
                    "  mount --bind \"$f\" \"/system/app/$n\"\n" +
                    "done\n" +
                    "exit 0\n";

    private static final String POST_FS_DATA =
            "#!/system/bin/sh\n" +
                    "sh \"${0%/*}/mount-overlays.sh\"\n";

    private static void writeModule() {
        String command =
                String.format("mkdir -p %s/system/app\n", MAGISK_DIR) +
                        String.format(
                                "printf 'id=substratum\\nname=substratum\\nversion=%s\\n" +
                                        "versionCode=%s\\n" +
                                        "author=substratum development team\\n" +
                                        "description=Systemless overlays for Substratum\\n' " +
                                        "> %s/module.prop\n",
                                BuildConfig.VERSION_NAME,
                                BuildConfig.VERSION_CODE,
                                MAGISK_DIR
                        ) +
                        String.format("touch %s/auto_mount\n", MAGISK_DIR) +
                        String.format("cat > %s/mount-overlays.sh <<'SUBS_EOF'\n%sSUBS_EOF\n",
                                MAGISK_DIR, MOUNT_SCRIPT) +
                        String.format("cat > %s/post-fs-data.sh <<'SUBS_EOF'\n%sSUBS_EOF\n",
                                MAGISK_DIR, POST_FS_DATA) +
                        String.format("chmod 755 %s/mount-overlays.sh %s/post-fs-data.sh\n",
                                MAGISK_DIR, MAGISK_DIR) +
                        String.format("chcon -R u:object_r:system_file:s0 %s/system\n",
                                MAGISK_DIR);
        Root.runCommand(command);
    }

    public static void handleModule(final Context context) {
        // When the directory is "/" there is no module support and we're falling back to
        // modifying system.
        if (MAGISK_DIR.equals("/"))
            return;
        boolean existed = moduleExists();
        // Always rewrite so an existing module picks up script changes
        writeModule();
        if (!existed) {
            Substratum.log(TAG, "Root module did not exist, created!");
            Toast.makeText(context, R.string.module_placed_reboot_message, Toast.LENGTH_LONG)
                    .show();
        }
    }

    /**
     * Bring /system/app in line with the overlays currently in the module. A no-op when the root
     * solution mounts module files itself.
     */
    public static void syncMount() {
        if (MAGISK_DIR.equals("/"))
            return;
        Root.runCommand(String.format(
                "[ -f %s/mount-overlays.sh ] && sh %s/mount-overlays.sh", MAGISK_DIR, MAGISK_DIR));
    }

    private static boolean moduleExists() {
        return "1".equals(Root.runCommand(
                String.format("test -f %s/module.prop && echo '1'", MAGISK_DIR)));
    }

}
