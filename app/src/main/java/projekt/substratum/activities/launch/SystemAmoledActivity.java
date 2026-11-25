/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.activities.launch;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import projekt.substratum.common.platform.SystemAmoled;

/**
 * Not exported: lets a root shell apply or remove "AMOLED for every app" without the UI, e.g.
 * su -c am start -n projekt.substratum/.activities.launch.SystemAmoledActivity --ez apply true
 */
public class SystemAmoledActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean apply = getIntent().getBooleanExtra("apply", true);
        new Thread(() -> {
            if (apply) {
                int[] done = SystemAmoled.applyAll(getApplicationContext(), (pkg, i, total) ->
                        Log.d("SubstratumAmoled", (i + 1) + "/" + total + " " + pkg));
                Log.i("SubstratumAmoled", "applied to " + done[0] + " apps, " + done[1] + " colors");
            } else {
                SystemAmoled.removeAll(getApplicationContext());
                Log.i("SubstratumAmoled", "removed");
            }
        }).start();
        finish();
    }
}
