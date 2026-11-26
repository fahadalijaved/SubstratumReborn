/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn

import android.content.Context
import android.content.Intent
import projekt.substratum.MainActivity
import projekt.substratum.activities.launch.AmoledSettingsActivity
import projekt.substratum.activities.launch.RebornSettingsActivity
import projekt.substratum.common.Packages
import projekt.substratum.common.commands.ElevatedCommands
import projekt.substratum.common.platform.AmoledState
import projekt.substratum.common.platform.ClassicParts
import projekt.substratum.common.platform.QsOptions
import projekt.substratum.common.platform.RebornStatus
import projekt.substratum.common.platform.XposedBackend
import projekt.substratum.reborn.applied.AppliedRepository
import projekt.substratum.reborn.components.onIo

/** What the Reborn screens do to the device. Root work always runs on the IO dispatcher. */
object RebornActions {

    const val AMOLED_THEME = "projekt.substratum.theme.amoled"

    suspend fun restartSystemUi() = onIo {
        QsOptions.restartSystemUi()
        // Quick Settings options written in Applied are picked up now
        AppliedRepository.restartDone()
    }

    /** Kill switch on or off, then a System UI restart so it takes effect there at once. */
    suspend fun setHooksPaused(paused: Boolean): Boolean = onIo {
        val done = XposedBackend.setHooksPaused(paused)
        QsOptions.restartSystemUi()
        AppliedRepository.restartDone()
        done
    }

    suspend fun resetSystemUiGuard() = onIo { RebornStatus.resetSystemUiGuard() }

    /**
     * Hold classic parts that crashed System UI (installed, not loaded), clear its guard and
     * restart it. The rest of their themes stays on.
     *
     * @return How many parts are held now
     */
    suspend fun pauseClassicParts(overlays: List<String>): Int = onIo {
        val held = ClassicParts.pauseForSystemUi(overlays).size
        AppliedRepository.restartDone()
        AppliedRepository.changedNow()
        held
    }

    suspend fun softReboot() = onIo { ElevatedCommands.softReboot() }

    suspend fun useLsposedEngine() = onIo { RebornStatus.useLsposedEngine() }

    /** KernelSU's manager, if one is installed. */
    suspend fun openRootManager(context: Context): Boolean {
        val intent = onIo {
            RebornStatus.ROOT_MANAGERS.firstNotNullOfOrNull {
                runCatching { context.packageManager.getLaunchIntentForPackage(it) }.getOrNull()
            }
        } ?: return false
        return start(context, intent)
    }

    /**
     * The LSPosed manager: its app if installed, otherwise the hidden manager LSPosed opens
     * through the shell (root).
     */
    suspend fun openLsposed(context: Context): Boolean {
        val intent = onIo {
            runCatching { context.packageManager.getLaunchIntentForPackage(RebornStatus.LSPOSED_MANAGER) }.getOrNull()
        }
        if (intent != null && start(context, intent)) return true
        return onIo { runCatching { RebornStatus.openLsposedWithRoot() }.getOrDefault(false) }
    }

    /**
     * Android's "allow unrestricted battery use" dialog for Reborn, else the list of battery
     * optimizations.
     */
    fun openBatteryOptimization(context: Context): Boolean {
        @Suppress("BatteryLife")
        val ask = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            android.net.Uri.parse("package:" + context.packageName))
        return start(context, ask) ||
            start(context, Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    fun openClassic(context: Context, manager: Boolean = false) {
        start(context, Intent(context, MainActivity::class.java).apply {
            if (manager) putExtra("launch_manager_fragment", true)
        })
    }

    fun openSettings(context: Context) {
        start(context, Intent(context, RebornSettingsActivity::class.java))
    }

    /**
     * The package the engine theme in use applies with: null when the engine is off or uses the
     * built-in rules (Reborn AMOLED). Reads the state file on IO.
     */
    suspend fun engineThemeInUse(context: Context): String? = onIo {
        runCatching { AmoledState.read(context).takeIf { it.enabled }?.rulesTheme }.getOrNull()
    }

    /** The engine theme in use (or the bundled one) and its name, read on IO. */
    fun amoledTheme(context: Context, state: AmoledState?): Pair<String?, CharSequence?> {
        val theme = state?.rulesTheme?.takeIf { Packages.isPackageInstalled(context, it) }
            ?: AMOLED_THEME.takeIf { Packages.isPackageInstalled(context, it) }
        val name = theme?.let { Packages.getPackageName(context, it) }
        return theme to name
    }

    /** The Studio (through AmoledSettingsActivity): a rules theme's page, or with none the Studio of the theme in use. */
    fun openAmoledSettings(context: Context, theme: String?, name: CharSequence?) {
        AmoledSettingsActivity.launch(context, theme, name)
    }

    /** The engine screen of earlier versions, kept as the Studio menu's "Classic screen". */
    fun openClassicAmoled(context: Context, theme: String?, name: CharSequence?) {
        AmoledSettingsActivity.launchClassic(context, theme, name)
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }
}
