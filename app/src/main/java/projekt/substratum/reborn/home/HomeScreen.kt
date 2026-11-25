/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.home

import android.text.format.DateUtils
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import projekt.substratum.R
import projekt.substratum.common.platform.RebornStatus
import projekt.substratum.reborn.RebornActions
import projekt.substratum.reborn.RebornCache
import projekt.substratum.reborn.amoled.AmoledSummary
import projekt.substratum.reborn.amoled.AppliedNow
import projekt.substratum.reborn.amoled.readAppliedNow
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
import projekt.substratum.reborn.components.rememberRoot
import projekt.substratum.reborn.components.rememberSnackbar
import projekt.substratum.reborn.theme.LocalRebornColors
import java.util.Locale

private enum class Confirm { Pause, Resume, SoftReboot }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    onOpenSetup: () -> Unit,
    onOpenAmoled: () -> Unit,
) {
    val context = LocalContext.current
    val snackbar = rememberSnackbar()
    val scope = rememberCoroutineScope()
    val resume = rememberResumeCount()
    var refresh by remember { mutableIntStateOf(0) }
    val status by rememberRoot(resume, refresh, initial = RebornCache.status) {
        RebornStatus.read(context).also { RebornCache.status = it }
    }
    val applied by rememberRoot(resume, refresh, initial = RebornCache.applied) {
        readAppliedNow(context).also { RebornCache.applied = it }
    }
    val model = remember(status) { status?.let { describe(context, it) } }
    var busy by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    var menu by remember { mutableStateOf(false) }

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
    val background = MaterialTheme.colorScheme.background
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = background,
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.reborn_home_title), fontWeight = FontWeight.Bold)
                },
                subtitle = { Text(stringResource(R.string.reborn_home_subtitle)) },
                actions = {
                    IconButton(onClick = onOpenSetup) {
                        Icon(painterResource(R.drawable.ic_reborn_setup), stringResource(R.string.reborn_setup_action))
                    }
                    IconButton(onClick = { RebornActions.openSettings(context) }) {
                        Icon(painterResource(R.drawable.ic_reborn_settings), stringResource(R.string.reborn_settings_title))
                    }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(painterResource(R.drawable.ic_reborn_more), stringResource(R.string.reborn_more))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.reborn_classic)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_reborn_open), null) },
                                onClick = {
                                    menu = false
                                    RebornActions.openClassic(context)
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = background,
                    scrolledContainerColor = background,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
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
            item(key = "applied_header") {
                Entrance(1, animate) { SectionHeader(stringResource(R.string.reborn_applied_now)) }
            }
            item(key = "amoled") {
                Entrance(2, animate) { AmoledCard(applied?.amoled, onOpenAmoled) }
            }
            item(key = "applied_tiles") {
                Entrance(3, animate) { AppliedTiles(applied) }
            }
            item(key = "actions_header") {
                Entrance(4, animate) { SectionHeader(stringResource(R.string.reborn_quick_actions)) }
            }
            item(key = "actions") {
                Entrance(5, animate) {
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
                    )
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
        Confirm.SoftReboot -> ConfirmDialog(
            title = stringResource(R.string.reborn_soft_reboot_title),
            text = stringResource(R.string.reborn_soft_reboot_text),
            confirmLabel = stringResource(R.string.reborn_soft_reboot),
            icon = R.drawable.ic_reborn_power,
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
    SectionCard(container = colors.heroCard, contentPadding = PaddingValues(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Crossfade(
                targetState = model?.level,
                animationSpec = tween(450),
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
                    text = stringResource(R.string.reborn_hero_overline).uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                    letterSpacing = 1.4.sp,
                    fontWeight = FontWeight.Bold,
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
        AnimatedContent(
            targetState = model?.headline ?: R.string.reborn_checking_headline,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "headline",
        ) { headline ->
            Text(
                text = stringResource(headline),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
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
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = when {
                        amoled == null -> stringResource(R.string.reborn_reading)
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
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (on) scheme.primary else scheme.onSurfaceVariant,
            )
        }
        if (amoled != null && on && (amoled.survivedReboot || amoled.restartNeeded > 0)) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

@Composable
private fun relativeTime(time: Long): String =
    if (time <= 0L) stringResource(R.string.reborn_never)
    else DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS).toString()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppliedTiles(applied: AppliedNow?) {
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
                text = applied?.overlays?.toString() ?: "–",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.SemiBold,
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
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            val qs = applied?.qs
            val amoledOn = applied?.amoled?.enabled == true
            if (qs == null) {
                Text(stringResource(R.string.reborn_reading), style = MaterialTheme.typography.bodyMedium)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip(
                        stringResource(R.string.reborn_qs_shade),
                        if (qs.shade && amoledOn) ChipState.OK else ChipState.Off,
                    )
                    StatusChip(
                        stringResource(R.string.reborn_qs_tracks),
                        if (qs.track && amoledOn) ChipState.OK else ChipState.Off,
                    )
                    StatusChip(
                        if (qs.aspect > 0f) stringResource(R.string.reborn_qs_rows,
                            String.format(Locale.US, "%.2f", qs.aspect))
                        else stringResource(R.string.reborn_qs_rows_stock),
                        if (qs.aspect > 0f) ChipState.OK else ChipState.Off,
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
    }
}
