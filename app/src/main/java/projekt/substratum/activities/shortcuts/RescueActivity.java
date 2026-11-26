/*
 * Copyright (c) 2016-2019 Projekt Substratum
 * This file is part of Substratum.
 *
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.activities.shortcuts;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import projekt.substratum.R;
import projekt.substratum.Substratum;
import projekt.substratum.common.Systems;
import projekt.substratum.common.commands.SamsungOverlayCacher;
import projekt.substratum.common.platform.QsOptions;
import projekt.substratum.common.platform.ThemeManager;
import projekt.substratum.common.platform.XposedBackend;
import projekt.substratum.util.helpers.Root;

import static projekt.substratum.common.References.SUBSTRATUM_PACKAGE;
import static projekt.substratum.common.Resources.FRAMEWORK;

public class RescueActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pauseModule();
        if (Systems.isSamsungDevice(Substratum.getInstance())) {
            if (Systems.isNewSamsungDeviceAndromeda(Substratum.getInstance()) ||
                    Systems.isNewSamsungDevice()) {
                SamsungOverlayCacher samsungOverlayCacher =
                        new SamsungOverlayCacher(Substratum.getInstance());
                List<String> listOfOverlays = samsungOverlayCacher.getOverlays(false);
                ArrayList<String> toUninstall = listOfOverlays.stream()
                        .filter(t -> t.startsWith(FRAMEWORK) || t.startsWith(SUBSTRATUM_PACKAGE))
                        .collect(Collectors.toCollection(ArrayList::new));
                ThemeManager.uninstallOverlay(Substratum.getInstance(), toUninstall);
            } else {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS));
            }
        } else {
            Toast.makeText(this, getString(R.string.rescue_toast), Toast.LENGTH_LONG).show();
            new Handler().postDelayed(() -> {
                List<String> frameworkOverlays = ThemeManager.listEnabledOverlaysForTarget(
                        Substratum.getInstance(),
                        FRAMEWORK);
                List<String> substratumOverlays = ThemeManager.listEnabledOverlaysForTarget(
                        Substratum.getInstance(),
                        SUBSTRATUM_PACKAGE);

                ArrayList<String> toBeDisabled = new ArrayList<>(frameworkOverlays);
                toBeDisabled.addAll(substratumOverlays);
                ThemeManager.disableOverlay(
                        Substratum.getInstance(),
                        toBeDisabled);
            }, 500L);
        }
        finish();
    }

    /**
     * Reborn's part of the rescue, off the main thread: the module's kill switch on, then System
     * UI restarted so it comes back without the hooks (the Engine screen's Resume undoes it).
     */
    private static void pauseModule() {
        new Thread(() -> {
            try {
                if (!Root.checkRootAccess()) return;
                XposedBackend.setHooksPaused(true);
                QsOptions.restartSystemUi();
            } catch (Throwable t) {
                Log.w("RebornRescue", "the module was not paused", t);
            }
        }, "reborn-rescue").start();
    }
}