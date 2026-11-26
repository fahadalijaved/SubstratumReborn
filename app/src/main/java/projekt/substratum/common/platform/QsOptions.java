/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.common.platform;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Locale;

import projekt.substratum.util.helpers.Root;

/**
 * One UI 9 quick settings options carried out by the LSPosed module in System UI, shared through
 * {@code /data/resource-cache/substratum/qs-options} (readable by System UI, written as root):
 * <pre>
 * aspect=0.65       panel row height as a fraction of the cell width (0 = stock 0.917)
 * shade=1           solid black shade instead of see-through
 * track=#10ffffff   brightness / volume tracks in this color instead of 15% white (grey);
 *                   0 keeps them stock, 1 (older versions) means the default 6% white
 * </pre>
 * The shade and track options only act while AMOLED for every app is applied. The track color
 * comes from the rules ({@code "qsTrack"}); older modules read any value but 0 as "on".
 */
public final class QsOptions {

    public static final String FILE = "qs-options";
    public static final float STOCK_ASPECT = 0.917f;
    public static final float MIN_ASPECT = 0.5f;
    /**
     * Near-black tracks: 6% white
     */
    public static final int DEFAULT_TRACK = 0x10ffffff;

    public float aspect; // 0 = stock
    public boolean shade = true;
    public boolean track = true;
    /**
     * The track color while {@link #track} is on
     */
    public int trackColor = DEFAULT_TRACK;

    public static QsOptions read() {
        QsOptions options = new QsOptions();
        File file = new File(XposedBackend.DIR, FILE);
        if (!file.exists()) return options;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                switch (key) {
                    case "aspect":
                        float aspect = Float.parseFloat(value);
                        options.aspect = aspect >= 0.3f && aspect <= 1.5f ? aspect : 0;
                        break;
                    case "shade":
                        options.shade = !"0".equals(value);
                        break;
                    case "track":
                        options.track = !"0".equals(value);
                        Integer color = color(value);
                        if (color != null) options.trackColor = color;
                        break;
                    default:
                        break;
                }
            }
        } catch (IOException | NumberFormatException ignored) {
            // stock
        }
        return options;
    }

    /**
     * {@code #AARRGGBB}, {@code #RRGGBB} or {@code 0xAARRGGBB}, or null (0 and 1 are switches)
     */
    static Integer color(String value) {
        String hex = value.startsWith("#") ? value.substring(1) :
                value.startsWith("0x") ? value.substring(2) : null;
        if (hex == null || hex.length() != 6 && hex.length() != 8) return null;
        try {
            long v = Long.parseLong(hex, 16);
            return (int) (hex.length() == 6 ? v | 0xff000000L : v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Write the options for System UI (root). They take effect when System UI restarts.
     */
    public boolean write(Context context) {
        File local = new File(context.getCacheDir(), FILE);
        try (FileWriter writer = new FileWriter(local)) {
            writer.write(String.format(Locale.US, "aspect=%.3f\nshade=%d\ntrack=%s\n",
                    aspect, shade ? 1 : 0, track ? String.format(Locale.US, "#%08x", trackColor) :
                            "0"));
        } catch (IOException e) {
            return false;
        }
        String dest = XposedBackend.DIR + '/' + FILE;
        String out = Root.runCommand("mkdir -p " + XposedBackend.DIR + " && chmod 755 " +
                XposedBackend.DIR + " && cp -f '" + local.getAbsolutePath() + "' " + dest +
                " && chmod 644 " + dest + " && restorecon " + dest + "; echo rc=$?");
        //noinspection ResultOfMethodCallIgnored
        local.delete();
        return out != null && out.trim().endsWith("rc=0");
    }

    public static void restartSystemUi() {
        // A restart on purpose does not count towards the module's crash loop guard
        Root.runCommand(XposedBackend.forgetStartsCommand("com.android.systemui") +
                "; kill $(pidof com.android.systemui)");
    }
}
