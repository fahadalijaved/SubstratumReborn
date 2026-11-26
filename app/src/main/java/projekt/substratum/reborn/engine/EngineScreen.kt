/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.engine

import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.format.DateUtils
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import projekt.substratum.R
import projekt.substratum.activities.shortcuts.RescueActivity
import projekt.substratum.common.platform.RebornStatus
import projekt.substratum.reborn.RebornCache
import projekt.substratum.reborn.applied.AppliedRepository
import projekt.substratum.reborn.components.ActionTile
import projekt.substratum.reborn.components.ChipState
import projekt.substratum.reborn.components.FixBanner
import projekt.substratum.reborn.components.HeroShape
import projekt.substratum.reborn.components.MorphingShape
import projekt.substratum.reborn.components.SectionCard
import projekt.substratum.reborn.components.SectionHeader
import projekt.substratum.reborn.components.ShapedIcon
import projekt.substratum.reborn.components.StatusChip
import projekt.substratum.reborn.components.onIo
import projekt.substratum.reborn.components.rememberResumeCount
import projekt.substratum.reborn.components.rememberRoot
import projekt.substratum.reborn.components.rememberSnackbar
import projekt.substratum.reborn.components.design.ActionIcon
import projekt.substratum.reborn.components.design.BackIcon
import projekt.substratum.reborn.components.design.BusyLabel
import projekt.substratum.reborn.components.design.HeroCard
import projekt.substratum.reborn.components.design.RebornFlexibleScaffold
import projekt.substratum.reborn.components.design.RebornPullToRefresh
import projekt.substratum.reborn.components.design.overline
import projekt.substratum.reborn.components.design.rememberRefreshableRoot
import projekt.substratum.reborn.home.HeroLevel
import projekt.substratum.reborn.home.HomeStatus
import projekt.substratum.reborn.home.HooksUpdate
import projekt.substratum.reborn.home.describe
import projekt.substratum.reborn.theme.LocalRebornColors

/** "One UI 9 · Android 17" (only Android elsewhere), or null before the first read. */
fun platformLine(status: RebornStatus.Snapshot?): String? {
    if (status == null) return null
    val oneUi = status.oneUi?.removeSuffix(".0")
    return listOfNotNull(oneUi, status.android).joinToString(" · ")
}

/**
 * Home › Engine: whether root, the LSPosed module and the System UI hooks work (the status hero),
 * what needs fixing, what the engine can do on this phone, the quick actions (restart System UI,
 * pause the module, soft reboot, the setup checklist, undo everything) and the rescue shortcut.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EngineScreen(onBack: () -> Unit, onOpenSetup: () -> Unit) {
    val context = LocalContext.current
    val snackbar = rememberSnackbar()
    val scope = rememberCoroutineScope()
    val resume = rememberResumeCount()
    var refresh by remember { mutableIntStateOf(0) }
    val engine = rememberRefreshableRoot(resume, refresh, initial = RebornCache.status) {
        RebornStatus.read(context).also { RebornCache.status = it }
    }
    val status = engine.value
    val appliedVersion by AppliedRepository.version.collectAsStateWithLifecycle()
    val applied by rememberRoot(resume, refresh, appliedVersion, initial = RebornCache.applied) {
        AppliedRepository.read(context).also { RebornCache.applied = it }
    }
    val model = remember(status) { status?.let { describe(context, it) } }
    val actions = rememberEngineActions(model) { refresh++ }
    val busy = actions.busy
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    RebornFlexibleScaffold(
        title = stringResource(R.string.reborn_engine_title),
        subtitle = platformLine(status) ?: stringResource(R.string.reborn_checking),
        large = false,
        scrollBehavior = scrollBehavior,
        connectScroll = false,
        navigationIcon = { BackIcon(onBack) },
        actions = {
            ActionIcon(R.drawable.ic_reborn_refresh, stringResource(R.string.reborn_recheck), {
                engine.refresh()
                refresh++
            })
        },
    ) { padding ->
        RebornPullToRefresh(
            refreshing = engine.refreshing,
            onRefresh = {
                engine.refresh()
                refresh++
            },
            modifier = Modifier.padding(top = padding.calculateTopPadding()),
            scrollConnection = scrollBehavior.nestedScrollConnection,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = padding.calculateBottomPadding() + 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "hero") { StatusHero(model, status?.readAt ?: 0L) }
                items(model?.issues.orEmpty(), key = { "issue_" + it.key }) { issue ->
                    FixBanner(
                        title = issue.title,
                        text = issue.text,
                        fixLabel = issue.fixLabel ?: stringResource(R.string.reborn_fix),
                        onFix = { actions.fix(issue) },
                        warning = issue.warning,
                        busy = busy != null,
                        // Only the banner whose fix is running shows the indicator
                        working = busy != null && busy == actions.actionKey(issue.fix),
                        modifier = Modifier.animateItem(),
                    )
                }
                model?.hooksUpdate?.let { update ->
                    item(key = "hooks_update") {
                        HooksUpdateCard(
                            update = update,
                            busy = busy != null,
                            working = busy == "restart",
                            onRestart = { actions.confirm = Confirm.RestartSystemUi },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                item(key = "can_header") { SectionHeader(stringResource(R.string.reborn_engine_can)) }
                item(key = "can") { Capabilities(status, onOpenLsposed = actions::openLsposed) }
                item(key = "actions_header") { SectionHeader(stringResource(R.string.reborn_quick_actions)) }
                item(key = "actions") {
                    QuickActions(
                        paused = model?.hooksPaused == true,
                        busy = busy,
                        onRestart = actions::restartSystemUi,
                        onPause = actions::askPause,
                        onSoftReboot = { actions.confirm = Confirm.SoftReboot },
                        onSetup = onOpenSetup,
                        onUndoAll = {
                            val snapshot = applied
                            if (snapshot == null) snackbar(context.getString(R.string.reborn_reading))
                            else AppliedRepository.askUndo(snapshot.undoPlan())
                        },
                    )
                }
                item(key = "rescue_header") { SectionHeader(stringResource(R.string.reborn_engine_rescue)) }
                item(key = "rescue") {
                    RescueCard(onAdd = {
                        scope.launch {
                            val asked = onIo { pinRescueShortcut(context) }
                            if (!asked) snackbar(context.getString(R.string.reborn_engine_rescue_unsupported))
                        }
                    })
                }
            }
        }
    }
    EngineDialogs(actions)
}

/** Asks the launcher to pin "Rescue Me!"; false when it cannot pin shortcuts. */
private fun pinRescueShortcut(context: Context): Boolean = try {
    if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
        false
    } else {
        val intent = Intent(context, RescueActivity::class.java).setAction(Intent.ACTION_VIEW)
        val shortcut = ShortcutInfoCompat.Builder(context, "rescue")
            .setShortLabel(context.getString(R.string.shortcut_rescue))
            .setLongLabel(context.getString(R.string.shortcut_rescue))
            .setIcon(IconCompat.createWithResource(context, R.drawable.app_shortcut_rescue))
            .setIntent(intent)
            .build()
        ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }
} catch (e: Exception) {
    Log.w("RebornEngine", "rescue shortcut not pinned", e)
    false
}

private class Capability(val title: Int, val value: String, val state: ChipState, val lsposed: Boolean = false)

/** What the engine can do on this phone: fabricated overlays, other signers' overlay APKs, the module. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Capabilities(status: RebornStatus.Snapshot?, onOpenLsposed: () -> Unit) {
    val colors = LocalRebornColors.current
    SectionCard {
        if (status == null) {
            Text(stringResource(R.string.reborn_checking), style = MaterialTheme.typography.bodyLarge)
            return@SectionCard
        }
        val root = status.root.granted
        val lsp = status.lsposed
        val oneUi = status.oneUi?.removeSuffix(".0")
        val rows = listOf(
            if (Build.VERSION.SDK_INT >= 34 && root) {
                Capability(R.string.reborn_engine_can_fabricated, stringResource(R.string.reborn_engine_can_fabricated_yes), ChipState.OK)
            } else {
                Capability(R.string.reborn_engine_can_fabricated, stringResource(R.string.reborn_engine_can_fabricated_no), ChipState.Warn)
            },
            if (oneUi != null) {
                Capability(R.string.reborn_engine_can_signers, stringResource(R.string.reborn_engine_can_signers_oneui, oneUi), ChipState.Off)
            } else {
                Capability(R.string.reborn_engine_can_signers, stringResource(R.string.reborn_engine_can_signers_rom), ChipState.Unknown)
            },
            when (lsp.level) {
                RebornStatus.Level.OK -> Capability(
                    R.string.reborn_engine_can_module,
                    pluralStringResource(R.plurals.reborn_engine_can_module_scope, lsp.scope.size, lsp.scope.size),
                    ChipState.OK, lsposed = true,
                )
                RebornStatus.Level.ERROR -> Capability(
                    R.string.reborn_engine_can_module,
                    stringResource(if (lsp.registered) R.string.reborn_value_disabled else R.string.reborn_value_not_added),
                    ChipState.Error, lsposed = true,
                )
                else -> Capability(
                    R.string.reborn_engine_can_module,
                    stringResource(if (root) R.string.reborn_value_unreadable else R.string.reborn_value_needs_root),
                    ChipState.Unknown, lsposed = true,
                )
            },
        )
        rows.forEachIndexed { i, row ->
            if (i > 0) {
                HorizontalDivider(
                    color = if (colors.dark) colors.cardBorder else MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(row.title),
                    style = MaterialTheme.typography.bodyLargeEmphasized,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                StatusChip(row.value, row.state, Modifier.widthIn(max = 200.dp))
            }
            if (row.lsposed) {
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = onOpenLsposed,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Icon(painterResource(R.drawable.ic_reborn_open), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.reborn_engine_open_lsposed))
                }
            }
        }
    }
}

/** "Rescue Me!" on the app icon, and a way to put it on the home screen. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RescueCard(onAdd: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShapedIcon(
                icon = R.drawable.ic_rescue_me,
                container = scheme.errorContainer,
                content = scheme.onErrorContainer,
                size = 52.dp,
                shape = MaterialShapes.SoftBurst.toShape(),
            )
            Spacer(Modifier.width(16.dp))
            Text(
                stringResource(R.string.reborn_engine_rescue_text),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(onClick = onAdd, shapes = ButtonDefaults.shapes(), modifier = Modifier.align(Alignment.End)) {
            Icon(painterResource(R.drawable.ic_reborn_shortcut), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.reborn_engine_rescue_add))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatusHero(model: HomeStatus?, readAt: Long) {
    val colors = LocalRebornColors.current
    val status = when (model?.level) {
        HeroLevel.Ok -> colors.ok
        HeroLevel.Warn -> colors.warn
        HeroLevel.Error -> colors.error
        null -> colors.unknown
    }
    val accent = if (model == null) MaterialTheme.colorScheme.primary else status.accent
    HeroCard(Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Crossfade(
                targetState = model?.level,
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                modifier = Modifier.size(108.dp),
                label = "heroShape",
            ) { level ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (level == null) {
                        ContainedLoadingIndicator(Modifier.size(84.dp))
                    } else {
                        MorphingShape(
                            shape = when (level) {
                                HeroLevel.Ok -> HeroShape.Calm
                                HeroLevel.Warn -> HeroShape.Alert
                                HeroLevel.Error -> HeroShape.Alarm
                            },
                            color = accent,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Icon(
                            painter = painterResource(
                                when (level) {
                                    HeroLevel.Ok -> R.drawable.ic_reborn_check
                                    HeroLevel.Warn -> R.drawable.ic_reborn_warning
                                    HeroLevel.Error -> R.drawable.ic_reborn_error
                                }
                            ),
                            contentDescription = stringResource(
                                when (level) {
                                    HeroLevel.Ok -> R.string.reborn_state_ok
                                    HeroLevel.Warn -> R.string.reborn_state_warn
                                    HeroLevel.Error -> R.string.reborn_state_error
                                }
                            ),
                            tint = status.onAccent,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.reborn_hero_overline),
                    style = MaterialTheme.typography.overline,
                    color = accent,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (model == null) stringResource(R.string.reborn_checking)
                    else stringResource(R.string.reborn_checked_at, DateUtils.formatDateTime(
                        LocalContext.current, readAt, DateUtils.FORMAT_SHOW_TIME)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(22.dp))
        val motion = MaterialTheme.motionScheme
        AnimatedContent(
            targetState = model?.headline ?: R.string.reborn_checking_headline,
            transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
            label = "headline",
        ) { headline ->
            Text(
                text = stringResource(headline),
                style = MaterialTheme.typography.displaySmallEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = model?.supporting ?: stringResource(R.string.reborn_checking_text),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (model != null) {
            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = if (colors.dark) colors.cardBorder else MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(6.dp))
            model.rows.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(row.icon),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = stringResource(row.label),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    StatusChip(row.value, row.state, Modifier.widthIn(max = 220.dp))
                }
            }
        }
    }
}

/**
 * The module was updated and System UI still runs the hooks it loaded at its start: all is well,
 * a restart of System UI loads the new ones. Tonal, not a warning.
 */
@Composable
internal fun HooksUpdateCard(
    update: HooksUpdate,
    busy: Boolean,
    working: Boolean,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = scheme.secondaryContainer,
    ) {
        Column(Modifier.padding(start = 18.dp, end = 14.dp, top = 16.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    painterResource(R.drawable.ic_reborn_info),
                    contentDescription = null,
                    tint = scheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.reborn_update_ready_title),
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        color = scheme.onSecondaryContainer,
                    )
                    Text(
                        stringResource(R.string.reborn_update_ready_text, update.running, update.installed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSecondaryContainer.copy(alpha = 0.86f),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onRestart()
                    },
                    enabled = !busy,
                    shapes = ButtonDefaults.shapes(),
                    colors = if (working) {
                        ButtonDefaults.buttonColors(
                            containerColor = scheme.onSecondaryContainer,
                            contentColor = scheme.secondaryContainer,
                            // Working keeps the colours: the in-button indicator says it is working
                            disabledContainerColor = scheme.onSecondaryContainer,
                            disabledContentColor = scheme.secondaryContainer,
                        )
                    } else {
                        ButtonDefaults.buttonColors(
                            containerColor = scheme.onSecondaryContainer,
                            contentColor = scheme.secondaryContainer,
                        )
                    },
                ) {
                    BusyLabel(working) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_reborn_restart), contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.reborn_update_ready_action))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QuickActions(
    paused: Boolean,
    busy: String?,
    onRestart: () -> Unit,
    onPause: () -> Unit,
    onSoftReboot: () -> Unit,
    onSetup: () -> Unit,
    onUndoAll: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val colors = LocalRebornColors.current
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ActionTile(
                icon = R.drawable.ic_reborn_restart,
                title = stringResource(R.string.reborn_action_restart),
                subtitle = stringResource(R.string.reborn_action_restart_text),
                onClick = onRestart,
                container = scheme.primaryContainer,
                content = scheme.onPrimaryContainer,
                shape = MaterialShapes.Cookie6Sided.toShape(),
                busy = busy == "restart" || busy == "guard",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            ActionTile(
                icon = if (paused) R.drawable.ic_reborn_play else R.drawable.ic_reborn_pause,
                title = stringResource(if (paused) R.string.reborn_action_resume else R.string.reborn_action_pause),
                subtitle = stringResource(if (paused) R.string.reborn_action_resume_text else R.string.reborn_action_pause_text),
                onClick = onPause,
                container = if (paused) colors.warn.container else scheme.tertiaryContainer,
                content = if (paused) colors.warn.onContainer else scheme.onTertiaryContainer,
                shape = MaterialShapes.Clover4Leaf.toShape(),
                busy = busy == "pause",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ActionTile(
                icon = R.drawable.ic_reborn_power,
                title = stringResource(R.string.reborn_action_soft_reboot),
                subtitle = stringResource(R.string.reborn_action_soft_reboot_text),
                onClick = onSoftReboot,
                container = scheme.errorContainer,
                content = scheme.onErrorContainer,
                shape = MaterialShapes.Sunny.toShape(),
                busy = busy == "reboot",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            ActionTile(
                icon = R.drawable.ic_reborn_setup,
                title = stringResource(R.string.reborn_action_setup),
                subtitle = stringResource(R.string.reborn_action_setup_text),
                onClick = onSetup,
                container = scheme.secondaryContainer,
                content = scheme.onSecondaryContainer,
                shape = MaterialShapes.Cookie4Sided.toShape(),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
        WideActionTile(
            icon = R.drawable.ic_reborn_undo,
            title = stringResource(R.string.reborn_action_undo_all),
            subtitle = stringResource(R.string.reborn_action_undo_all_text),
            onClick = onUndoAll,
            container = colors.warn.container,
            content = colors.warn.accent,
        )
    }
}

/** A full-width quick action: the shape and icon, title and detail side by side. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WideActionTile(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    container: Color,
    content: Color,
) {
    val haptics = LocalHapticFeedback.current
    SectionCard(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            onClick()
        },
        contentPadding = PaddingValues(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShapedIcon(icon, container, content, size = 52.dp, shape = MaterialShapes.Burst.toShape())
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                painterResource(R.drawable.ic_reborn_chevron),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
