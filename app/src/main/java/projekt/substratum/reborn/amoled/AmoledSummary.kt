/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.amoled

import android.content.Context
import projekt.substratum.common.platform.AmoledState
import projekt.substratum.common.platform.QsOptions
import projekt.substratum.common.platform.XposedBackend
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

/** Everything applied right now, for Home. */
data class AppliedNow(
    val amoled: AmoledSummary,
    val overlays: Int,
    val qs: QsOptions,
)

/** Blocking (files, and root on the state file's first read). */
fun readAmoledSummary(context: Context): AmoledSummary {
    val state = AmoledState.read(context)
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

/** Blocking. */
fun readAppliedNow(context: Context): AppliedNow = AppliedNow(
    amoled = readAmoledSummary(context),
    overlays = XposedBackend.listAll().count { XposedBackend.isEnabled(it) },
    qs = QsOptions.read(),
)
