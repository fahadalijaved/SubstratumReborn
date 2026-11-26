/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.home

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import projekt.substratum.R
import projekt.substratum.common.platform.RebornStatus
import projekt.substratum.reborn.RebornActions
import projekt.substratum.reborn.RebornCache
import projekt.substratum.reborn.amoled.AmoledSummary
import projekt.substratum.reborn.applied.AppliedRepository
import projekt.substratum.reborn.applied.AppliedSnapshot
import projekt.substratum.reborn.applied.AppliedSource
import projekt.substratum.reborn.applied.QsOption
import projekt.substratum.reborn.components.ActionTile
import projekt.substratum.reborn.components.ChipState
import projekt.substratum.reborn.components.ConfirmDialog
import projekt.substratum.reborn.components.Entrance
import projekt.substratum.reborn.components.FixBanner
import projekt.substratum.reborn.components.HeroShape
import projekt.substratum.reborn.components.MorphingShape
import projekt.substratum.reborn.components.SectionCard
import projekt.substratum.reborn.components.SectionHeader
import projekt.substratum.reborn.components.ShapedIcon
import projekt.substratum.reborn.components.StatusChip
import projekt.substratum.reborn.components.rememberResumeCount
import projekt.substratum.reborn.components.rememberSnackbar
import projekt.substratum.reborn.components.design.ActionIcon
import projekt.substratum.reborn.components.design.BusyLabel
import projekt.substratum.reborn.components.design.HeroCard
import projekt.substratum.reborn.components.design.MenuAction
import projekt.substratum.reborn.components.design.MenuGroup
import projekt.substratum.reborn.components.design.RebornFlexibleScaffold
import projekt.substratum.reborn.components.design.RebornOverflow
import projekt.substratum.reborn.components.design.RebornPullToRefresh
import projekt.substratum.reborn.components.design.heroNumber
import projekt.substratum.reborn.components.design.overline
import projekt.substratum.reborn.components.design.rememberRefreshableRoot
import projekt.substratum.reborn.components.rememberRoot
import projekt.substratum.reborn.showcase.ShowcaseCarousel
import projekt.substratum.reborn.theme.LocalRebornColors
import java.util.Locale

private enum class Confirm { Pause, Resume, SoftReboot, RestartSystemUi }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    onOpenSetup: () -> Unit,
    onOpenAmoled: () -> Unit,
    onOpenApplied: () -> Unit,
    onOpenAbout: () -> Unit,
    /** Nothing covers Home (About, a sheet): its showcase may page on its own */
    active: Boolean = true,
) {
    val context = LocalContext.current
    val snackbar = rememberSnackbar()
    val scope = rememberCoroutineScope()
    val resume = rememberResumeCount()
    var refresh by remember { mutableIntStateOf(0) }
    // Pull to refresh reads the status (and, through [refresh], what is applied) again
    val home = rememberRefreshableRoot(resume, refresh, initial = RebornCache.status) {
        RebornStatus.read(context).also { RebornCache.status = it }
    }
    val status = home.value
    // The same read as the Applied tab: one source of truth for what is applied
    val appliedVersion by AppliedRepository.version.collectAsStateWithLifecycle()
    val applied by rememberRoot(resume, refresh, appliedVersion, initial = RebornCache.applied) {
        AppliedRepository.read(context).also { RebornCache.applied = it }
    }
    val model = remember(status) { status?.let { describe(context, it) } }
    var busy by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }

    // The entrance plays once; coming back to the tab shows Home at once
    var entered by rememberSaveable { mutableStateOf(false) }
    val animate = remember { !entered }
    LaunchedEffect(Unit) { entered = true }

    /** Runs a device action once at a time; [block] returns the message to show. */
    fun act(key: String, needsRoot: Boolean = true, recheckAfter: Long = 6000L,
            block: suspend () -> String?) {
        if (busy != null) return
        if (needsRoot && model?.rootGranted != true) {
            snackbar(context.getString(R.string.reborn_needs_root))
            return
        }
        scope.launch {
            busy = key
            val message = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e("RebornHome", "$key failed", t)
                context.getString(R.string.reborn_failed)
            } finally {
                busy = null
            }
            message?.let(snackbar)
            refresh++
            if (recheckAfter > 0) {
                // System UI reports the module a few seconds after it starts again
                delay(recheckAfter)
                refresh++
            }
        }
    }

    fun fix(fix: Fix) {
        when (fix) {
            Fix.RootManager -> scope.launch {
                if (!RebornActions.openRootManager(context)) {
                    snackbar(context.getString(R.string.reborn_no_root_manager))
                }
            }
            Fix.Lsposed -> scope.launch {
                if (!RebornActions.openLsposed(context)) {
                    snackbar(context.getString(R.string.reborn_no_lsposed))
                }
            }
            Fix.Engine -> act("engine", needsRoot = false, recheckAfter = 0L) {
                RebornActions.useLsposedEngine()
                context.getString(R.string.reborn_engine_switched)
            }
            Fix.RestartSystemUi -> act("restart") {
                RebornActions.restartSystemUi()
                context.getString(R.string.reborn_restarting_systemui)
            }
            Fix.ResetGuard -> act("guard") {
                RebornActions.resetSystemUiGuard()
                context.getString(R.string.reborn_guard_reset)
            }
            Fix.ResumeHooks -> confirm = Confirm.Resume
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    RebornFlexibleScaffold(
        title = stringResource(R.string.reborn_home_title),
        subtitle = stringResource(R.string.reborn_home_subtitle),
        scrollBehavior = scrollBehavior,
        connectScroll = false,
        actions = {
            ActionIcon(R.drawable.ic_reborn_setup, stringResource(R.string.reborn_setup_action), onOpenSetup)
            ActionIcon(
                R.drawable.ic_reborn_settings,
                stringResource(R.string.reborn_settings_title),
                { RebornActions.openSettings(context) },
            )
            RebornOverflow {
                listOf(
                    MenuGroup(listOf(MenuAction(stringResource(R.string.reborn_about_menu), R.drawable.ic_reborn_info, onClick = onOpenAbout))),
                    MenuGroup(
                        listOf(
                            MenuAction(
                                stringResource(R.string.reborn_classic),
                                R.drawable.ic_reborn_open,
                                supporting = stringResource(R.string.reborn_classic_supporting),
                                onClick = { RebornActions.openClassic(context) },
                            ),
                        ),
                    ),
                )
            }
        },
    ) { padding ->
        RebornPullToRefresh(
            refreshing = home.refreshing,
            onRefresh = {
                home.refresh()
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
                item(key = "hero") {
                    Entrance(0, animate) { StatusHero(model, status?.readAt ?: 0L) }
                }
                items(model?.issues.orEmpty(), key = { "issue_" + it.key }) { issue ->
                    FixBanner(
                        title = issue.title,
                        text = issue.text,
                        fixLabel = stringResource(R.string.reborn_fix),
                        onFix = { fix(issue.fix) },
                        warning = issue.warning,
                        busy = busy != null,
                        modifier = Modifier.animateItem(),
                    )
                }
                model?.hooksUpdate?.let { update ->
                    item(key = "hooks_update") {
                        HooksUpdateCard(
                            update = update,
                            busy = busy != null,
                            onRestart = { confirm = Confirm.RestartSystemUi },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                item(key = "showcase") {
                    Entrance(1, animate) {
                        ShowcaseCarousel(theme = applied?.amoled?.theme, onOpenStudio = onOpenAmoled, active = active)
                    }
                }
                item(key = "applied_header") {
                    Entrance(2, animate) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionHeader(stringResource(R.string.reborn_applied_now), Modifier.weight(1f))
                            TextButton(onClick = onOpenApplied, modifier = Modifier.padding(top = 10.dp)) {
                                Text(stringResource(R.string.reborn_see_all))
                                Spacer(Modifier.width(4.dp))
                                Icon(painterResource(R.drawable.ic_reborn_chevron), contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                item(key = "amoled") {
                    Entrance(3, animate) { AmoledCard(applied?.amoled, onOpenAmoled) }
                }
                item(key = "applied_tiles") {
                    Entrance(4, animate) { AppliedTiles(applied, onOpenApplied) }
                }
                item(key = "actions_header") {
                    Entrance(5, animate) { SectionHeader(stringResource(R.string.reborn_quick_actions)) }
                }
                item(key = "actions") {
                    Entrance(6, animate) {
                        QuickActions(
                            paused = model?.hooksPaused == true,
                            busy = busy,
                            onRestart = {
                                act("restart") {
                                    RebornActions.restartSystemUi()
                                    context.getString(R.string.reborn_restarting_systemui)
                                }
                            },
                            onPause = { confirm = if (model?.hooksPaused == true) Confirm.Resume else Confirm.Pause },
                            onSoftReboot = { confirm = Confirm.SoftReboot },
                            onSetup = onOpenSetup,
                            onUndoAll = {
                                val snapshot = applied
                                if (snapshot == null) snackbar(context.getString(R.string.reborn_reading))
                                else AppliedRepository.askUndo(snapshot.undoPlan())
                            },
                        )
                    }
                }
            }
        }
    }

    when (confirm) {
        Confirm.Pause -> ConfirmDialog(
            title = stringResource(R.string.reborn_pause_title),
            text = stringResource(R.string.reborn_pause_text),
            confirmLabel = stringResource(R.string.reborn_pause),
            icon = R.drawable.ic_reborn_pause,
            onDismiss = { confirm = null },
            onConfirm = {
                act("pause") {
                    context.getString(if (RebornActions.setHooksPaused(true)) R.string.reborn_paused_done
                    else R.string.reborn_failed)
                }
            },
        )
        Confirm.Resume -> ConfirmDialog(
            title = stringResource(R.string.reborn_resume_title),
            text = stringResource(R.string.reborn_resume_text),
            confirmLabel = stringResource(R.string.reborn_resume),
            icon = R.drawable.ic_reborn_play,
            onDismiss = { confirm = null },
            onConfirm = {
                act("pause") {
                    context.getString(if (RebornActions.setHooksPaused(false)) R.string.reborn_resumed_done
                    else R.string.reborn_failed)
                }
            },
        )
        Confirm.RestartSystemUi -> ConfirmDialog(
            title = stringResource(R.string.reborn_restart_title),
            text = stringResource(R.string.reborn_restart_text),
            confirmLabel = stringResource(R.string.reborn_restart_confirm),
            icon = R.drawable.ic_reborn_restart,
            onDismiss = { confirm = null },
            onConfirm = {
                act("restart") {
                    RebornActions.restartSystemUi()
                    context.getString(R.string.reborn_restarting_systemui)
                }
            },
        )
        Confirm.SoftReboot -> ConfirmDialog(
            title = stringResource(R.string.reborn_soft_reboot_title),
            text = stringResource(R.string.reborn_soft_reboot_text),
            confirmLabel = stringResource(R.string.reborn_soft_reboot),
            icon = R.drawable.ic_reborn_power,
            destructive = true,
            onDismiss = { confirm = null },
            onConfirm = {
                act("reboot", recheckAfter = 0L) {
                    RebornActions.softReboot()
                    null
                }
            },
        )
        null -> Unit
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AmoledCard(amoled: AmoledSummary?, onClick: () -> Unit) {
    val on = amoled?.enabled == true
    val scheme = MaterialTheme.colorScheme
    SectionCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShapedIcon(
                icon = R.drawable.ic_reborn_amoled,
                container = if (on) scheme.primary else scheme.surfaceContainerHighest,
                content = if (on) scheme.onPrimary else scheme.onSurfaceVariant,
                size = 56.dp,
                shape = MaterialShapes.Clover4Leaf.toShape(),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.reborn_amoled_title),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
                Text(
                    text = when {
                        amoled == null -> stringResource(R.string.reborn_reading)
                        // On, but no apply ever finished recording its time (interrupted)
                        on && amoled.appliedAt <= 0L -> stringResource(
                            R.string.reborn_amoled_summary_no_time, amoled.themed, amoled.total,
                        )
                        on -> stringResource(
                            R.string.reborn_amoled_summary, amoled.themed, amoled.total,
                            relativeTime(amoled.appliedAt),
                        )
                        else -> stringResource(R.string.reborn_amoled_off_summary)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(if (on) R.string.reborn_on else R.string.reborn_off),
                style = MaterialTheme.typography.headlineMediumEmphasized,
                color = if (on) scheme.primary else scheme.onSurfaceVariant,
            )
        }
        val interrupted = amoled != null && on && amoled.appliedAt <= 0L
        if (amoled != null && on && (amoled.survivedReboot || amoled.restartNeeded > 0 || interrupted)) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (interrupted) {
                    StatusChip(stringResource(R.string.reborn_amoled_interrupted), ChipState.Warn)
                }
                if (amoled.survivedReboot) {
                    StatusChip(stringResource(R.string.reborn_survived_reboot), ChipState.OK)
                }
                if (amoled.restartNeeded > 0) {
                    StatusChip(stringResource(R.string.reborn_restart_needed, amoled.restartNeeded), ChipState.Warn)
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
private fun HooksUpdateCard(update: HooksUpdate, busy: Boolean, onRestart: () -> Unit, modifier: Modifier = Modifier) {
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scheme.onSecondaryContainer,
                        contentColor = scheme.secondaryContainer,
                        // Busy keeps the colours: the in-button indicator says it is working
                        disabledContainerColor = scheme.onSecondaryContainer,
                        disabledContentColor = scheme.secondaryContainer,
                    ),
                ) {
                    BusyLabel(busy) {
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

@Composable
private fun relativeTime(time: Long): String =
    if (time <= 0L) stringResource(R.string.reborn_never)
    else DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS).toString()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppliedTiles(applied: AppliedSnapshot?, onOpenApplied: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionCard(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            onClick = onOpenApplied,
            contentPadding = PaddingValues(18.dp),
        ) {
            ShapedIcon(
                icon = R.drawable.ic_reborn_applied,
                container = scheme.tertiaryContainer,
                content = scheme.onTertiaryContainer,
                size = 44.dp,
                shape = MaterialShapes.Cookie6Sided.toShape(),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = applied?.count(AppliedSource.THEME_OVERLAY, true)?.toString() ?: "–",
                style = MaterialTheme.typography.heroNumber,
            )
            Text(
                text = stringResource(R.string.reborn_overlays_on),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
        SectionCard(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            onClick = onOpenApplied,
            contentPadding = PaddingValues(18.dp),
        ) {
            ShapedIcon(
                icon = R.drawable.ic_reborn_tune,
                container = scheme.secondaryContainer,
                content = scheme.onSecondaryContainer,
                size = 44.dp,
                shape = MaterialShapes.Pentagon.toShape(),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.reborn_qs_tweaks),
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
            Spacer(Modifier.height(10.dp))
            if (applied == null) {
                Text(stringResource(R.string.reborn_reading), style = MaterialTheme.typography.bodyMedium)
            } else {
                // The options as Applied lists them: shade and tracks act only while AMOLED is on
                val shade = applied.qs(QsOption.SHADE)
                val tracks = applied.qs(QsOption.TRACK)
                val rows = applied.qs(QsOption.ROWS)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip(
                        stringResource(R.string.reborn_qs_shade),
                        if (shade?.enabled == true) ChipState.OK else ChipState.Off,
                    )
                    StatusChip(
                        stringResource(R.string.reborn_qs_tracks),
                        if (tracks?.enabled == true) ChipState.OK else ChipState.Off,
                    )
                    StatusChip(
                        if (rows?.enabled == true) stringResource(R.string.reborn_qs_rows,
                            String.format(Locale.US, "%.2f", rows.value?.toFloatOrNull() ?: 0f))
                        else stringResource(R.string.reborn_qs_rows_stock),
                        if (rows?.enabled == true) ChipState.OK else ChipState.Off,
                    )
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
