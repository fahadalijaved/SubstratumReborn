/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.home

import android.content.Context
import android.content.Intent
import android.util.Log
import projekt.substratum.Substratum
import projekt.substratum.common.platform.AmoledRules
import projekt.substratum.common.platform.AmoledState

/**
 * Blocking (the engine's state file, the package manager): the apps with a launcher entry that
 * were installed after the engine theme was last applied, that its rules cover and that it has
 * not read yet, newest first. Empty while the engine is off. The update watcher re-themes them on
 * its own when automatic re-theme is on; until then Home counts them under "Since last apply".
 */
fun readNewApps(context: Context): List<String> {
    val state = AmoledState.read(context)
    if (!state.enabled || state.appliedAt <= 0L) return emptyList()
    val pm = context.packageManager
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val launchable = try {
        pm.queryIntentActivities(launcher, 0).mapTo(LinkedHashSet()) { it.activityInfo.packageName }
    } catch (e: RuntimeException) {
        Log.w("RebornHome", "no launcher apps: $e")
        return emptyList()
    }
    val fresh = launchable
        .filter { it != context.packageName && !state.perApp.containsKey(it) }
        .mapNotNull { pkg ->
            val installed = try {
                pm.getPackageInfo(pkg, 0).firstInstallTime
            } catch (e: Exception) {
                return@mapNotNull null
            }
            if (installed > state.appliedAt) pkg to installed else null
        }
    if (fresh.isEmpty()) return emptyList()
    val rules = try {
        AmoledRules.effective(context, state.rulesTheme, Substratum.getPreferences())
    } catch (e: Exception) {
        null
    }
    return fresh
        .filter { (pkg, _) -> rules == null || rules.covers(pkg) }
        .sortedByDescending { it.second }
        .map { it.first }
}
