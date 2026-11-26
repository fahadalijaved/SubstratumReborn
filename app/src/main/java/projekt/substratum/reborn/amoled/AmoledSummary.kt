/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.amoled

import android.content.Context
import projekt.substratum.common.platform.AmoledState
import projekt.substratum.reborn.RebornActions

/** AMOLED for every app as the state file has it. */
data class AmoledSummary(
    val enabled: Boolean,
    val themed: Int,
    val total: Int,
    val appliedAt: Long,
    val restoredAt: Long,
    val survivedReboot: Boolean,
    val restartNeeded: Int,
    val unsupported: Int,
    val theme: String?,
    val themeName: CharSequence?,
)

/** Blocking (files, and root on the state file's first read). */
fun readAmoledSummary(context: Context): AmoledSummary = amoledSummaryOf(context, AmoledState.read(context))

/** Blocking (PackageManager, for the rules theme's name). */
fun amoledSummaryOf(context: Context, state: AmoledState): AmoledSummary {
    val (theme, name) = RebornActions.amoledTheme(context, state)
    return AmoledSummary(
        enabled = state.enabled,
        themed = state.countThemed(),
        total = state.perApp.size,
        appliedAt = state.appliedAt,
        restoredAt = state.restoredAt,
        survivedReboot = state.survivedReboot(),
        restartNeeded = state.perApp.values.count { it.userEnabled && it.restartNeeded },
        unsupported = state.perApp.values.count { it.route == AmoledState.Route.UNSUPPORTED },
        theme = theme,
        themeName = name,
    )
}
