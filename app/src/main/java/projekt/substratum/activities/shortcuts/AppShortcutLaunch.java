/*
 * Copyright (c) 2016-2019 Projekt Substratum
 * This file is part of Substratum.
 *
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.activities.shortcuts;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import projekt.substratum.LauncherActivity;
import projekt.substratum.reborn.RebornActivity;
import projekt.substratum.reborn.nav.RebornLinks;
import projekt.substratum.reborn.nav.RebornTab;

import static projekt.substratum.common.Internal.THEME_PID;

public class AppShortcutLaunch extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // A pinned theme icon: the theme's page in Reborn (any kind; Classic's theme screen is
        // one tap away there)
        String theme = getIntent().getStringExtra(THEME_PID);
        Intent open = new Intent(this, LauncherActivity.class)
                .putExtra(RebornActivity.EXTRA_TAB, RebornTab.Themes.getKey())
                .putExtra(RebornActivity.EXTRA_THEME, theme)
                .putExtra(RebornActivity.EXTRA_OPEN, RebornLinks.OPEN_DETAIL);
        startActivity(open);
        finish();
    }
}