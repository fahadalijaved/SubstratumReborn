/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.theme.amoled;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Hands Substratum the theme details. The theme is not encrypted, so no keys are returned.
 */
public class SubstratumLauncher extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent result = new Intent();
        result.putExtra("theme_name", getString(R.string.theme_name));
        result.putExtra("theme_author", getString(R.string.theme_author));
        result.putExtra("theme_pid", getPackageName());
        result.putExtra("theme_hash", 0);
        result.putExtra("theme_launch_type", false);
        result.putExtra("theme_debug", false);
        result.putExtra("theme_piracy_check", false);
        setResult(RESULT_OK, result);
        finish();
    }
}
