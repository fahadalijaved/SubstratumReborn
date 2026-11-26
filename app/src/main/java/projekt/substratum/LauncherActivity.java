/*
 * Copyright (c) 2016-2019 Projekt Substratum
 * This file is part of Substratum.
 *
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import projekt.substratum.reborn.RebornActivity;

public class LauncherActivity extends AppCompatActivity {

    /**
     * The main launcher activity: straight to Reborn Home, no splash screen or intro. Kept as the
     * exported entry so pinned icons and shortcuts keep working.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = new Intent(this, RebornActivity.class);
        Intent from = getIntent();
        if (from != null) {
            // A tab, a theme and what of it to open (RebornLinks resolves them)
            for (String extra : new String[]{RebornActivity.EXTRA_TAB, RebornActivity.EXTRA_THEME,
                    RebornActivity.EXTRA_OPEN}) {
                if (from.hasExtra(extra)) intent.putExtra(extra, from.getStringExtra(extra));
            }
        }
        // A link into a running Reborn (a pinned theme icon, the Customize shortcut) goes to that
        // instance (onNewIntent) instead of stacking a second shell over it
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        this.startActivity(intent);
        this.overridePendingTransition(0, 0);
        this.finish();
    }
}