/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.home

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import projekt.substratum.BuildConfig
import projekt.substratum.R
import projekt.substratum.common.platform.RebornStatus
import projekt.substratum.reborn.components.ChipState

enum class HeroLevel { Ok, Warn, Error }

enum class Fix { RootManager, Lsposed, Engine, RestartSystemUi, ResetGuard, ResumeHooks }

data class Issue(
    val key: String,
    val title: String,
    val text: String,
    val fix: Fix,
    val warning: Boolean = false,
)

data class StatusRow(
    @DrawableRes val icon: Int,
    @StringRes val label: Int,
    val value: String,
    val state: ChipState,
)

/**
 * The hooks work, one build behind: System UI started before this version of the app was
 * installed. Not a problem, just a restart of System UI away.
 */
data class HooksUpdate(val running: Int, val installed: Int)

data class HomeStatus(
    val level: HeroLevel,
    @StringRes val headline: Int,
    val supporting: String,
    val rows: List<StatusRow>,
    val issues: List<Issue>,
    val rootGranted: Boolean,
    val hooksPaused: Boolean,
    /** An updated module waits for a System UI restart, or null */
    val hooksUpdate: HooksUpdate? = null,
)

/** Plain words for what [RebornStatus] read. */
fun describe(context: Context, s: RebornStatus.Snapshot): HomeStatus {
    val root = s.root
    val lsp = s.lsposed
    val hb = s.heartbeat
    val engine = s.engine
    val res = context.resources

    // The rows of the hero
    val rows = ArrayList<StatusRow>()
    rows += StatusRow(
        R.drawable.ic_reborn_root, R.string.reborn_row_root,
        if (root.granted) root.version ?: "root" else context.getString(R.string.reborn_value_not_granted),
        if (root.granted) ChipState.OK else ChipState.Error,
    )
    rows += when (lsp.level) {
        RebornStatus.Level.OK -> StatusRow(
            R.drawable.ic_reborn_module, R.string.reborn_row_lsposed,
            res.getQuantityString(R.plurals.reborn_value_lsposed_enabled, lsp.scope.size, lsp.scope.size),
            ChipState.OK,
        )
        RebornStatus.Level.ERROR -> StatusRow(
            R.drawable.ic_reborn_module, R.string.reborn_row_lsposed,
            context.getString(if (lsp.registered) R.string.reborn_value_disabled else R.string.reborn_value_not_added),
            ChipState.Error,
        )
        else -> StatusRow(
            R.drawable.ic_reborn_module, R.string.reborn_row_lsposed,
            context.getString(if (root.granted) R.string.reborn_value_unreadable else R.string.reborn_value_needs_root),
            ChipState.Unknown,
        )
    }
    rows += when {
        s.hooksPaused || hb.guardOff() -> StatusRow(
            R.drawable.ic_reborn_systemui, R.string.reborn_row_systemui,
            context.getString(R.string.reborn_value_paused), ChipState.Warn,
        )
        hb.guardTripped() -> StatusRow(
            R.drawable.ic_reborn_systemui, R.string.reborn_row_systemui,
            context.getString(R.string.reborn_value_guard_tripped), ChipState.Error,
        )
        !hb.present || !hb.sinceBoot -> StatusRow(
            R.drawable.ic_reborn_systemui, R.string.reborn_row_systemui,
            context.getString(R.string.reborn_value_no_heartbeat), ChipState.Error,
        )
        // Working, one build behind until System UI restarts: not a problem
        !hb.current -> StatusRow(
            R.drawable.ic_reborn_systemui, R.string.reborn_row_systemui,
            context.getString(R.string.reborn_value_update_ready), ChipState.OK,
        )
        else -> StatusRow(
            R.drawable.ic_reborn_systemui, R.string.reborn_row_systemui,
            context.getString(R.string.reborn_value_hooked), ChipState.OK,
        )
    }
    rows += StatusRow(
        R.drawable.ic_reborn_engine, R.string.reborn_row_engine, engine.name,
        if (engine.xposed) ChipState.OK else ChipState.Warn,
    )
    rows += StatusRow(
        R.drawable.ic_reborn_info, R.string.reborn_row_system,
        listOfNotNull(s.oneUi, s.android).joinToString(" · "),
        if (s.oneUi != null) ChipState.OK else ChipState.Unknown,
    )

    // What needs fixing
    val issues = ArrayList<Issue>()
    if (!root.granted) {
        issues += Issue("root", context.getString(R.string.reborn_issue_root_title),
            context.getString(R.string.reborn_issue_root_text), Fix.RootManager)
    }
    if (s.hooksPaused) {
        issues += Issue("paused", context.getString(R.string.reborn_issue_paused_title),
            context.getString(R.string.reborn_issue_paused_text), Fix.ResumeHooks, warning = true)
    }
    if (lsp.level == RebornStatus.Level.ERROR) {
        issues += Issue("module", context.getString(R.string.reborn_issue_module_title),
            context.getString(if (lsp.registered) R.string.reborn_issue_module_text
            else R.string.reborn_issue_module_missing_text), Fix.Lsposed)
    }
    if (root.granted && !engine.xposed) {
        issues += Issue("engine", context.getString(R.string.reborn_issue_engine_title),
            context.getString(if (engine.mismatch) R.string.reborn_issue_engine_mismatch_text
            else R.string.reborn_issue_engine_text, engine.name), Fix.Engine)
    }
    if (root.granted && !s.hooksPaused && lsp.level != RebornStatus.Level.ERROR) {
        when {
            hb.guardTripped() -> issues += Issue("guard",
                context.getString(R.string.reborn_issue_guard_title),
                context.getString(R.string.reborn_issue_guard_text), Fix.ResetGuard)
            !hb.present || !hb.sinceBoot -> issues += Issue("heartbeat",
                context.getString(R.string.reborn_issue_heartbeat_title),
                context.getString(R.string.reborn_issue_heartbeat_text), Fix.RestartSystemUi)
            hb.guardOff() -> issues += Issue("stock",
                context.getString(R.string.reborn_issue_stock_title),
                context.getString(R.string.reborn_issue_stock_text), Fix.RestartSystemUi)
        }
    }
    // Hooks loaded and guarded, only from an older build (the app was updated since System UI
    // started): a calm notice with a restart, never an issue
    val hooksUpdate = if (root.granted && !s.hooksPaused && lsp.level != RebornStatus.Level.ERROR &&
        hb.present && hb.sinceBoot && !hb.guardTripped() && !hb.guardOff() && !hb.current) {
        HooksUpdate(hb.version, BuildConfig.VERSION_CODE)
    } else null

    // The headline
    val heartbeatSinceBoot = hb.present && hb.sinceBoot
    val (level, headline, supporting) = when {
        !root.granted -> Triple(HeroLevel.Error, R.string.reborn_hero_root_missing,
            context.getString(R.string.reborn_hero_root_missing_text))
        s.hooksPaused -> Triple(HeroLevel.Warn, R.string.reborn_hero_paused,
            context.getString(R.string.reborn_hero_paused_text))
        lsp.level == RebornStatus.Level.ERROR ||
            (lsp.level == RebornStatus.Level.UNKNOWN && !heartbeatSinceBoot) ->
            Triple(HeroLevel.Error, R.string.reborn_hero_inactive,
                context.getString(R.string.reborn_hero_inactive_text))
        issues.isNotEmpty() -> Triple(HeroLevel.Warn, R.string.reborn_hero_attention,
            res.getQuantityString(R.plurals.reborn_hero_attention_text, issues.size, issues.size))
        hooksUpdate != null -> Triple(HeroLevel.Ok, R.string.reborn_hero_ok,
            context.getString(R.string.reborn_hero_ok_update_text))
        else -> Triple(HeroLevel.Ok, R.string.reborn_hero_ok,
            context.getString(R.string.reborn_hero_ok_text))
    }
    return HomeStatus(level, headline, supporting, rows, issues, root.granted, s.hooksPaused, hooksUpdate)
}
