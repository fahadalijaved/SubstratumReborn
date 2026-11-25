/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.activities.launch;

import android.app.Activity;
import android.os.Bundle;


/**
 * Not exported: lets a root shell apply or remove "AMOLED for every app" without the UI, e.g.
 * su -c am start -n projekt.substratum/.activities.launch.SystemAmoledActivity --ez apply true
 */
public class SystemAmoledActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startForegroundService(new android.content.Intent(this,
                projekt.substratum.services.system.SystemAmoledService.class)
                .putExtras(getIntent()));
        finish();
    }
}
