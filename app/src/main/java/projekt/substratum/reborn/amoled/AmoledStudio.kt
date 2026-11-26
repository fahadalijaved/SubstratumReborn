/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.amoled

import android.os.Build
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import projekt.substratum.R
import projekt.substratum.common.platform.AmoledJobs
import projekt.substratum.common.platform.AmoledLevel
import projekt.substratum.common.platform.AmoledPresets
import projekt.substratum.common.platform.AmoledState
import projekt.substratum.common.platform.QsOptions
import projekt.substratum.reborn.RebornActions
import projekt.substratum.reborn.components.ChipState
import projekt.substratum.reborn.components.ConfirmDialog
import projekt.substratum.reborn.components.Entrance
import projekt.substratum.reborn.components.HeroShape
import projekt.substratum.reborn.components.LocalSnackbar
import projekt.substratum.reborn.components.MorphingShape
import projekt.substratum.reborn.components.SectionCard
import projekt.substratum.reborn.components.SectionHeader
import projekt.substratum.reborn.components.ShapedIcon
import projekt.substratum.reborn.components.StatusChip
import projekt.substratum.reborn.components.mock.BeforeAfter
import projekt.substratum.reborn.components.mock.MockRoles
import projekt.substratum.reborn.components.mock.MockSide
import projekt.substratum.reborn.components.mock.QsMock
import projekt.substratum.reborn.components.mock.SettingsMock
import projekt.substratum.reborn.theme.LocalRebornColors
import projekt.substratum.reborn.theme.StatusColors
import java.util.Locale

/**
 * AMOLED Studio, the AMOLED tab: the state and Apply (with its progress), the look (presets,
 * surfaces, accent) over a live before/after preview, the Quick Settings designer, and the apps,
 * each with its colours in an inspector. Everything it shows lives in [StudioState].
 */
@Composable
fun AmoledStudio() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        StudioState.attach(context)
        onDispose { StudioState.detach() }
    }
    val resume = projekt.substratum.reborn.components.rememberResumeCount()
    LaunchedEffect(resume) { if (resume > 0) StudioState.refreshAll() }

    // Job results and confirmations in the shell's snackbar
    val host = LocalSnackbar.current
    LaunchedEffect(host) {
        StudioState.events.collect { event ->
            val showing = host.currentSnackbarData
            // A switch shows its own snackbar with Undo: its result would only repeat it
            if (event.kind == AmoledJobs.Kind.SET_ENABLED && showing?.visuals?.actionLabel != null) {
                return@collect
            }
            showing?.dismiss()
            launch { host.showSnackbar(event.message, duration = SnackbarDuration.Short) }
        }
    }

    var appsPage by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = appsPage != null) { appsPage = null }
    // Kept out here, so coming back from the app list finds the Studio where it was
    val mainList = rememberLazyListState()
    val mainBar = rememberTopAppBarState()
    var entered by rememberSaveable { mutableStateOf(false) }
    val animate = remember { !entered }
    LaunchedEffect(Unit) { entered = true }
    AnimatedContent(
        targetState = appsPage,
        transitionSpec = {
            if (targetState != null) {
                (slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { it / 3 } + fadeIn(tween(220))) togetherWith
                    fadeOut(tween(120))
            } else {
                fadeIn(tween(220, delayMillis = 60)) togetherWith
                    (slideOutHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { it / 3 } + fadeOut(tween(160)))
            }
        },
        contentKey = { it != null },
        label = "studioPage",
    ) { page ->
        if (page != null) {
            AppList(
                initialFilter = AppFilter.entries.firstOrNull { it.name == page } ?: AppFilter.All,
                onBack = { appsPage = null },
            )
        } else {
            StudioMain(
                listState = mainList,
                barState = mainBar,
                animate = animate,
                onOpenApps = { appsPage = it.name },
            )
        }
    }
}

private val Levels = listOf(AmoledLevel.PURE, AmoledLevel.DIM, AmoledLevel.ELEVATED, AmoledLevel.TINTED)

@StringRes
internal fun levelLabel(level: AmoledLevel): Int = when (level) {
    AmoledLevel.PURE -> R.string.amoled_level_pure
    AmoledLevel.DIM -> R.string.amoled_level_dim
    AmoledLevel.ELEVATED -> R.string.amoled_level_elevated
    AmoledLevel.TINTED -> R.string.amoled_level_tinted
}

@StringRes
private fun levelSummary(level: AmoledLevel): Int = when (level) {
    AmoledLevel.PURE -> R.string.amoled_level_pure_summary
    AmoledLevel.DIM -> R.string.amoled_level_dim_summary
    AmoledLevel.ELEVATED -> R.string.amoled_level_elevated_summary
    AmoledLevel.TINTED -> R.string.amoled_level_tinted_summary
}

@StringRes
private fun presetLabel(id: String): Int = when (id) {
    AmoledPresets.PURE_BLACK -> R.string.amoled_preset_pure
    AmoledPresets.ELEVATED_CARDS -> R.string.amoled_preset_elevated
    AmoledPresets.DIM -> R.string.amoled_preset_dim
    AmoledPresets.SPRITE -> R.string.amoled_preset_sprite
    else -> R.string.amoled_preset_custom
}

@StringRes
private fun accentLabel(accent: String?): Int = when (accent) {
    AmoledPresets.ACCENT_MONET -> R.string.amoled_accent_monet
    AmoledPresets.ACCENT_ONEUI -> R.string.amoled_accent_oneui
    AmoledPresets.ACCENT_SPRITE -> R.string.amoled_accent_sprite
    AmoledPresets.ACCENT_WHITE -> R.string.amoled_accent_white
    null -> R.string.amoled_accent_custom
    else -> if (AmoledPresets.isCustom(accent)) R.string.amoled_accent_custom else R.string.amoled_accent_none
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StudioMain(
    listState: LazyListState,
    barState: TopAppBarState,
    animate: Boolean,
    onOpenApps: (AppFilter) -> Unit,
) {
    val context = LocalContext.current
    val summary by StudioState.summary.collectAsStateWithLifecycle()
    val job by StudioState.job.collectAsStateWithLifecycle()
    val apps by StudioState.apps.collectAsStateWithLifecycle()
    val look by StudioState.look.collectAsStateWithLifecycle()
    val previews by StudioState.previews.collectAsStateWithLifecycle()
    val themes by StudioState.themes.collectAsStateWithLifecycle()
    val theme by StudioState.theme.collectAsStateWithLifecycle()
    val dirty by StudioState.lookDirty.collectAsStateWithLifecycle()
    val qs by StudioState.qs.collectAsStateWithLifecycle()
    val lsposedScope by StudioState.lsposedScope.collectAsStateWithLifecycle()
    val engineClassic by StudioState.engineClassic.collectAsStateWithLifecycle()
    val autoTheme by StudioState.autoTheme.collectAsStateWithLifecycle()
    val pending = StudioState.pending(summary, look, dirty, theme)
    val on = summary?.enabled == true

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(barState)
    val background = MaterialTheme.colorScheme.background
    val gutter = Modifier.padding(horizontal = 16.dp)
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = background,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.reborn_tab_amoled), fontWeight = FontWeight.Bold) },
                subtitle = { Text(stringResource(R.string.reborn_studio_subtitle)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = background,
                    scrolledContainerColor = background,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "hero") {
                Entrance(0, animate) {
                    HeroCard(
                        summary = summary,
                        apps = apps,
                        job = job,
                        pending = pending,
                        themes = themes,
                        theme = theme,
                        modifier = gutter,
                        onApply = StudioState::applyAll,
                        onRemove = StudioState::remove,
                        onReapplyChanged = StudioState::reapplyChanged,
                        onClassic = {
                            RebornActions.openClassicAmoled(context, theme, themes.firstOrNull { it.pkg == theme }?.label)
                        },
                        onTheme = StudioState::chooseTheme,
                        options = EngineOptions(
                            classic = engineClassic,
                            autoTheme = autoTheme,
                            onClassic = StudioState::setEngineClassic,
                            onAutoTheme = StudioState::setAutoTheme,
                        ),
                    )
                }
            }
            item(key = "look_header") {
                Entrance(1, animate) {
                    Row(gutter, verticalAlignment = Alignment.CenterVertically) {
                        SectionHeader(stringResource(R.string.reborn_studio_look), Modifier.weight(1f))
                        AnimatedVisibility(
                            visible = pending,
                            enter = fadeIn() + scaleIn(spring(dampingRatio = 0.6f)),
                            exit = fadeOut() + scaleOut(),
                        ) {
                            StatusChip(stringResource(R.string.reborn_studio_pending), ChipState.Warn, Modifier.padding(top = 12.dp))
                        }
                    }
                }
            }
            item(key = "presets") {
                Entrance(2, animate) {
                    PresetCarousel(look, previews, qs?.aspect ?: QsOptions.STOCK_ASPECT, StudioState::choosePreset)
                }
            }
            item(key = "preview") {
                Entrance(3, animate) { PreviewCard(look, previews, qs, gutter) }
            }
            item(key = "surfaces") {
                Entrance(4, animate) { SurfacesCard(look, gutter, StudioState::chooseLevel) }
            }
            item(key = "accent") {
                Entrance(5, animate) {
                    AccentCard(look, gutter, StudioState::chooseAccent, StudioState::setAccentLight)
                }
            }
            item(key = "qs_header") {
                SectionHeader(stringResource(R.string.reborn_studio_qs), gutter)
            }
            item(key = "qs") {
                QsDesigner(
                    previews = previews,
                    saved = qs,
                    savedTile = look?.tile ?: projekt.substratum.common.platform.AmoledRules.TILE_WHITE,
                    amoledOn = on,
                    modifier = gutter,
                )
            }
            item(key = "apps_header") {
                SectionHeader(stringResource(R.string.reborn_studio_apps), gutter)
            }
            item(key = "apps") {
                AppsCard(apps, lsposedScope, gutter, onOpenApps)
            }
        }
    }
}

// Hero

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun HeroCard(
    summary: StudioState.Summary?,
    apps: List<StudioState.AppRow>?,
    job: StudioState.JobUi?,
    pending: Boolean,
    themes: List<StudioState.RulesTheme>,
    theme: String?,
    modifier: Modifier,
    onApply: () -> Unit,
    onRemove: () -> Unit,
    onReapplyChanged: () -> Unit,
    onClassic: () -> Unit,
    onTheme: (String?) -> Unit,
    options: EngineOptions,
) {
    val colors = LocalRebornColors.current
    val scheme = MaterialTheme.colorScheme
    val on = summary?.enabled == true
    val running = job != null && job.kind != AmoledJobs.Kind.SET_ENABLED
    // The app list when it could be read (it needs root), else the state file
    val rows = apps?.takeIf { it.isNotEmpty() }
    val themed = rows?.count { it.themed } ?: summary?.themed ?: 0
    val total = rows?.size ?: summary?.total ?: 0
    val restartNeeded = rows?.count { it.enabled && it.restartNeeded } ?: summary?.restartNeeded ?: 0
    val lit = on || running
    SectionCard(modifier = modifier, container = colors.heroCard, contentPadding = PaddingValues(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                if (summary == null) {
                    ContainedLoadingIndicator(Modifier.size(84.dp))
                } else {
                    MorphingShape(
                        shape = when {
                            running -> HeroShape.Alert
                            on -> HeroShape.Calm
                            else -> HeroShape.Idle
                        },
                        color = if (lit) scheme.primary else scheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Icon(
                        painterResource(R.drawable.ic_reborn_amoled),
                        contentDescription = null,
                        tint = if (lit) scheme.onPrimary else scheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.reborn_studio_overline).uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.primary,
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                AnimatedContent(
                    targetState = on,
                    transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.9f)) togetherWith fadeOut() },
                    label = "heroState",
                ) { isOn ->
                    Text(
                        text = stringResource(if (isOn) R.string.reborn_on else R.string.reborn_off),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isOn) scheme.primary else scheme.onSurface,
                    )
                }
                Text(
                    text = when {
                        summary == null -> stringResource(R.string.reborn_reading)
                        on -> stringResource(R.string.reborn_studio_themed, themed, total)
                        summary.appliedAt <= 0L -> stringResource(R.string.reborn_studio_never_applied)
                        else -> stringResource(R.string.reborn_amoled_off_summary)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        if (summary != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (on) stringResource(R.string.reborn_studio_applied, relativeTime(summary.appliedAt))
                else stringResource(R.string.reborn_studio_off_text),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            val chips = on && (summary.survivedReboot || restartNeeded > 0) || pending
            if (chips) {
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (on && summary.survivedReboot) {
                        StatusChip(stringResource(R.string.reborn_survived_reboot), ChipState.OK)
                    }
                    if (on && restartNeeded > 0) {
                        StatusChip(stringResource(R.string.reborn_restart_needed, restartNeeded), ChipState.Warn)
                    }
                    if (pending) StatusChip(stringResource(R.string.reborn_studio_pending), ChipState.Warn)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        HorizontalDivider(color = if (colors.dark) colors.cardBorder else scheme.outlineVariant)
        Spacer(Modifier.height(14.dp))
        RulesPicker(themes, theme, enabled = !running, onTheme = onTheme)
        Spacer(Modifier.height(18.dp))
        // The last job stays on screen while its progress fades out
        val last = remember { arrayOfNulls<StudioState.JobUi>(1) }
        if (job != null) last[0] = job
        AnimatedContent(
            targetState = running,
            transitionSpec = {
                (fadeIn(tween(260)) + scaleIn(initialScale = 0.96f)) togetherWith fadeOut(tween(160)) using
                    SizeTransform(clip = false)
            },
            label = "heroAction",
        ) { busy ->
            val current = job ?: last[0]
            if (busy && current != null) {
                JobProgress(current)
            } else {
                ApplyButtons(
                    on = on,
                    pending = pending,
                    enabled = summary != null,
                    onApply = onApply,
                    onRemove = onRemove,
                    onReapplyChanged = onReapplyChanged,
                    onClassic = onClassic,
                    options = options,
                )
            }
        }
    }
}

/** The engine switches of the overflow menu */
@androidx.compose.runtime.Immutable
private class EngineOptions(
    val classic: Boolean,
    val autoTheme: Boolean,
    val onClassic: (Boolean) -> Unit,
    val onAutoTheme: (Boolean) -> Unit,
)

@Composable
private fun relativeTime(time: Long): String =
    if (time <= 0L) stringResource(R.string.reborn_never)
    else DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

@Composable
private fun RulesPicker(
    themes: List<StudioState.RulesTheme>,
    theme: String?,
    enabled: Boolean,
    onTheme: (String?) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Text(
        stringResource(R.string.reborn_studio_rules),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val options = listOf<Pair<String?, String>>(null to stringResource(R.string.reborn_studio_rules_builtin)) +
            themes.map { it.pkg to it.label }
        options.forEach { (pkg, label) ->
            val selected = theme == pkg
            FilterChip(
                selected = selected,
                enabled = enabled,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onTheme(pkg)
                },
                label = { Text(label, maxLines = 1) },
                leadingIcon = if (selected) {
                    { Icon(painterResource(R.drawable.ic_reborn_check), null, Modifier.size(18.dp)) }
                } else null,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ApplyButtons(
    on: Boolean,
    pending: Boolean,
    enabled: Boolean,
    onApply: () -> Unit,
    onRemove: () -> Unit,
    onReapplyChanged: () -> Unit,
    onClassic: () -> Unit,
    options: EngineOptions,
) {
    val haptics = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    val height = ButtonDefaults.MediumContainerHeight
    val more = stringResource(R.string.reborn_studio_more_actions)
    SplitButtonLayout(
        modifier = Modifier.fillMaxWidth(),
        leadingButton = {
            SplitButtonDefaults.LeadingButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onApply()
                },
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = height),
                shapes = SplitButtonDefaults.leadingButtonShapesFor(height),
                contentPadding = SplitButtonDefaults.leadingButtonContentPaddingFor(height),
            ) {
                Icon(
                    painterResource(if (on) R.drawable.ic_reborn_refresh else R.drawable.ic_reborn_play),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(
                        when {
                            !on -> R.string.reborn_studio_apply
                            pending -> R.string.reborn_studio_apply_changes
                            else -> R.string.reborn_studio_reapply
                        }
                    ),
                    style = ButtonDefaults.textStyleFor(height),
                    maxLines = 1,
                )
            }
        },
        trailingButton = {
            Box {
                SplitButtonDefaults.TrailingButton(
                    checked = menu,
                    onCheckedChange = {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        menu = it
                    },
                    modifier = Modifier
                        .heightIn(min = height)
                        .semantics { contentDescription = more },
                    shapes = SplitButtonDefaults.trailingButtonShapesFor(height),
                    contentPadding = SplitButtonDefaults.trailingButtonContentPaddingFor(height),
                ) {
                    val rotation by animateFloatAsState(if (menu) 180f else 0f, label = "menuArrow")
                    Icon(
                        painterResource(R.drawable.ic_reborn_expand),
                        contentDescription = null,
                        modifier = Modifier
                            .size(SplitButtonDefaults.TrailingIconSize)
                            .graphicsLayer { rotationZ = rotation },
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.reborn_studio_reapply_changed)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_reborn_refresh), null) },
                        enabled = on,
                        onClick = {
                            menu = false
                            onReapplyChanged()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.reborn_studio_remove)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_reborn_delete), null) },
                        enabled = on,
                        onClick = {
                            menu = false
                            confirmRemove = true
                        },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.reborn_studio_auto_theme)) },
                        trailingIcon = { Switch(checked = options.autoTheme, onCheckedChange = null) },
                        onClick = {
                            menu = false
                            options.onAutoTheme(!options.autoTheme)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.reborn_studio_engine_classic)) },
                        trailingIcon = { Switch(checked = options.classic, onCheckedChange = null) },
                        onClick = {
                            menu = false
                            options.onClassic(!options.classic)
                        },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.reborn_studio_classic)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_reborn_open), null) },
                        onClick = {
                            menu = false
                            onClassic()
                        },
                    )
                }
            }
        },
    )
    if (confirmRemove) {
        ConfirmDialog(
            title = stringResource(R.string.system_amoled_remove_title),
            text = stringResource(R.string.system_amoled_remove_message),
            confirmLabel = stringResource(R.string.system_amoled_remove),
            icon = R.drawable.ic_reborn_delete,
            onConfirm = onRemove,
            onDismiss = { confirmRemove = false },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun JobProgress(job: StudioState.JobUi) {
    val scheme = MaterialTheme.colorScheme
    val progress by animateFloatAsState(
        targetValue = job.fraction,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessVeryLow),
        label = "jobProgress",
    )
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Crossfade(job.started, label = "jobStarted") { started ->
                if (!started) {
                    ContainedLoadingIndicator(Modifier.size(56.dp))
                } else {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(MaterialShapes.Cookie9Sided.toShape())
                            .background(scheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onPrimaryContainer,
                        )
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(
                        when (job.kind) {
                            AmoledJobs.Kind.APPLY_ALL -> R.string.reborn_studio_job_apply
                            AmoledJobs.Kind.APPLY_ONE -> R.string.reborn_studio_job_apply_one
                            AmoledJobs.Kind.REMOVE -> R.string.reborn_studio_job_remove
                            AmoledJobs.Kind.RESTORE -> R.string.reborn_studio_job_restore
                            AmoledJobs.Kind.APPLY_SYSTEMUI -> R.string.reborn_studio_job_systemui
                            AmoledJobs.Kind.SET_ENABLED -> R.string.reborn_studio_job_switch
                        }
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = when {
                        !job.started -> stringResource(R.string.reborn_studio_starting)
                        job.label != null -> stringResource(R.string.reborn_studio_progress, job.done, job.total, job.label)
                        else -> stringResource(R.string.reborn_studio_progress_count, job.done, job.total)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        AnimatedVisibility(visible = job.started) {
            Column {
                Spacer(Modifier.height(16.dp))
                LinearWavyProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (job.queued > 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.reborn_studio_queued, job.queued),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

// Look

@Composable
private fun PresetCarousel(
    look: StudioState.Look?,
    previews: StudioState.Previews?,
    aspect: Float,
    onPreset: (String) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val custom = look?.preset == AmoledPresets.CUSTOM
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(AmoledPresets.PRESETS, key = { it.id }) { preset ->
            PresetCard(
                name = stringResource(presetLabel(preset.id)),
                detail = presetDetail(preset.level, preset.accent),
                roles = previews?.presets?.get(preset.id),
                aspect = aspect,
                selected = look?.preset == preset.id,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onPreset(preset.id)
                },
            )
        }
        if (custom) {
            item(key = AmoledPresets.CUSTOM) {
                PresetCard(
                    name = stringResource(R.string.amoled_preset_custom),
                    detail = presetDetail(look.level, look.accent),
                    roles = previews?.look,
                    aspect = aspect,
                    selected = true,
                    onClick = {},
                )
            }
        }
    }
}

@Composable
private fun presetDetail(level: AmoledLevel, accent: String): String {
    val levelName = stringResource(levelLabel(level))
    return if (accent == AmoledPresets.ACCENT_NONE) levelName
    else levelName + " · " + stringResource(accentLabel(accent))
}

@Composable
private fun PresetCard(
    name: String,
    detail: String,
    roles: MockRoles?,
    aspect: Float,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalRebornColors.current
    val scheme = MaterialTheme.colorScheme
    val edge by animateColorAsState(
        if (selected) scheme.primary else if (colors.dark) colors.cardBorder else scheme.outlineVariant,
        label = "presetEdge",
    )
    val scale by animateFloatAsState(
        if (selected) 1f else 0.97f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "presetScale",
    )
    val mock = roles ?: MockRoles.Empty
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(26.dp),
        color = colors.card,
        border = BorderStroke(if (selected) 2.dp else 1.dp, edge),
        modifier = Modifier
            .width(212.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .semantics { this.selected = selected },
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                QsMock(mock, MockSide.Amoled, Modifier.weight(1f), aspect = aspect, compact = true)
                SettingsMock(mock, MockSide.Amoled, Modifier.weight(1f), compact = true)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AnimatedVisibility(
                    visible = selected,
                    enter = scaleIn(spring(dampingRatio = 0.5f)) + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                ) {
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(scheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_reborn_check),
                            contentDescription = stringResource(R.string.reborn_studio_selected),
                            tint = scheme.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewCard(
    look: StudioState.Look?,
    previews: StudioState.Previews?,
    qs: StudioState.QsSaved?,
    modifier: Modifier,
) {
    val roles = previews?.look ?: MockRoles.Empty
    SectionCard(modifier = modifier, contentPadding = PaddingValues(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 12.dp),
        ) {
            Text(
                stringResource(R.string.reborn_studio_preview),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (look != null) {
                AnimatedContent(look.level, label = "previewLevel") { level ->
                    Text(
                        stringResource(levelLabel(level)),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        BeforeAfter(
            beforeLabel = stringResource(R.string.reborn_studio_stock),
            afterLabel = stringResource(R.string.reborn_studio_amoled),
            description = stringResource(R.string.reborn_studio_preview_description),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp)),
        ) { side ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                QsMock(
                    roles = roles,
                    side = side,
                    modifier = Modifier.fillMaxWidth(),
                    aspect = qs?.aspect ?: QsOptions.STOCK_ASPECT,
                    opaqueShade = qs?.shade ?: true,
                    darkTracks = qs?.track ?: true,
                )
                SettingsMock(roles, side, Modifier.fillMaxWidth())
            }
        }
        Text(
            stringResource(R.string.reborn_studio_preview_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SurfacesCard(look: StudioState.Look?, modifier: Modifier, onLevel: (AmoledLevel) -> Unit) {
    val haptics = LocalHapticFeedback.current
    SectionCard(modifier = modifier) {
        Text(
            stringResource(R.string.reborn_studio_surfaces),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            Levels.forEachIndexed { index, level ->
                ToggleButton(
                    checked = look?.level == level,
                    onCheckedChange = {
                        if (look?.level != level) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onLevel(level)
                        }
                    },
                    enabled = look != null,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { role = Role.RadioButton },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        Levels.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) {
                    Text(stringResource(levelLabel(level)), maxLines = 1, softWrap = false)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        AnimatedContent(
            targetState = look?.level ?: AmoledLevel.PURE,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
            label = "levelSummary",
        ) { level ->
            Text(
                stringResource(levelSummary(level)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private enum class SwatchKind { None, Color, Picker }

private class AccentOption(val choice: String?, val color: Int?, val kind: SwatchKind)

private val Rainbow = listOf(
    Color(0xFFFF3B30), Color(0xFFFFCC00), Color(0xFF34C759), Color(0xFF00C7BE),
    Color(0xFF007AFF), Color(0xFFAF52DE), Color(0xFFFF3B30),
)

@Composable
private fun AccentCard(
    look: StudioState.Look?,
    modifier: Modifier,
    onAccent: (String) -> Unit,
    onAccentLight: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val accent = look?.accent ?: AmoledPresets.ACCENT_NONE
    val custom = AmoledPresets.isCustom(accent)
    val monet = remember { AmoledPresets.monetAccent(context) }
    val options = remember(monet, accent) {
        buildList {
            AmoledPresets.ACCENTS.forEach { choice ->
                if (choice == AmoledPresets.ACCENT_MONET && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@forEach
                val color = AmoledPresets.resolveAccent(choice, monet)
                add(AccentOption(choice, color, if (color == null) SwatchKind.None else SwatchKind.Color))
            }
            val customColor = if (custom) AmoledPresets.resolveAccent(accent, null) else null
            add(AccentOption(null, customColor, if (customColor != null) SwatchKind.Color else SwatchKind.Picker))
        }
    }
    var picking by rememberSaveable { mutableStateOf(false) }
    SectionCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.reborn_studio_accent),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (custom) stringResource(R.string.amoled_accent_custom_value, accent.uppercase(Locale.ROOT))
                else stringResource(accentLabel(accent)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            options.forEach { option ->
                val selected = if (option.choice == null) custom else option.choice.equals(accent, ignoreCase = true)
                AccentSwatch(
                    option = option,
                    selected = selected,
                    label = stringResource(accentLabel(option.choice)),
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        if (option.choice == null) picking = true else onAccent(option.choice)
                    },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        val lightEnabled = accent != AmoledPresets.ACCENT_NONE
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .toggleable(
                    value = look?.accentLight == true,
                    enabled = lightEnabled && look != null,
                    role = Role.Switch,
                    onValueChange = {
                        haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                        onAccentLight(it)
                    },
                )
                .padding(vertical = 6.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.reborn_studio_accent_light),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (lightEnabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
                Text(
                    stringResource(R.string.amoled_accent_light_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = look?.accentLight == true, onCheckedChange = null, enabled = lightEnabled && look != null)
        }
    }
    if (picking) {
        CustomAccentDialog(
            initial = if (custom) AmoledPresets.resolveAccent(accent, null) else null,
            onDismiss = { picking = false },
            onPick = onAccent,
        )
    }
}

@Composable
private fun AccentSwatch(option: AccentOption, selected: Boolean, label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val scale by animateFloatAsState(
        if (selected) 1.08f else 1f,
        spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "swatchScale",
    )
    val ring by animateColorAsState(if (selected) scheme.primary else Color.Transparent, label = "swatchRing")
    val fill = option.color?.let { Color(it) }
    Box(
        modifier = Modifier
            .size(50.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(2.5.dp, ring, CircleShape)
            .padding(5.dp)
            .clip(CircleShape)
            .then(
                when {
                    option.kind == SwatchKind.Picker -> Modifier.background(Brush.sweepGradient(Rainbow))
                    fill != null -> Modifier.background(fill)
                    else -> Modifier.background(scheme.surfaceContainerHighest)
                }
            )
            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.6f), CircleShape)
            .clickable(role = Role.RadioButton, onClickLabel = label, onClick = onClick)
            .semantics {
                contentDescription = label
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        if (option.kind == SwatchKind.None) {
            val slash = scheme.onSurfaceVariant
            Canvas(Modifier.size(22.dp)) {
                drawLine(slash, Offset(0f, size.height), Offset(size.width, 0f), strokeWidth = 2.dp.toPx())
            }
        }
        if (selected && option.kind != SwatchKind.None) {
            val tint = if (fill != null && fill.luminance() > 0.5f) Color.Black else Color.White
            Icon(painterResource(R.drawable.ic_reborn_check), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        } else if (option.kind == SwatchKind.Picker) {
            Icon(painterResource(R.drawable.ic_reborn_palette), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

/** A colour as #RRGGBB, or null. */
private fun parseHex(text: String): Int? {
    val hex = text.trim().removePrefix("#")
    if (hex.length != 6 || hex.any { Character.digit(it, 16) < 0 }) return null
    return (hex.toLong(16) or 0xff000000L).toInt()
}

private fun hexOf(color: Int): String = String.format(Locale.ROOT, "#%06X", color and 0xffffff)

@Composable
private fun CustomAccentDialog(initial: Int?, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var text by rememberSaveable { mutableStateOf(initial?.let(::hexOf) ?: hexOf(AmoledPresets.SPRITE_TEAL)) }
    val parsed = parseHex(text)
    val hsv = remember { FloatArray(3) }
    var hue by remember {
        mutableFloatStateOf(FloatArray(3).also { android.graphics.Color.colorToHSV(parsed ?: AmoledPresets.SPRITE_TEAL, it) }[0])
    }
    val preview by animateColorAsState(parsed?.let { Color(it) } ?: scheme.surfaceContainerHighest, label = "customPreview")
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_reborn_palette), contentDescription = null) },
        title = { Text(stringResource(R.string.reborn_studio_custom_title)) },
        text = {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(preview),
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { value ->
                        val clean = value.trim().take(7).let { if (it.startsWith("#")) it else "#$it" }.take(7)
                        text = clean
                        parseHex(clean)?.let {
                            android.graphics.Color.colorToHSV(it, hsv)
                            hue = hsv[0]
                        }
                    },
                    label = { Text(stringResource(R.string.reborn_studio_custom_hex)) },
                    isError = parsed == null && text.length >= 7,
                    supportingText = if (parsed == null && text.length >= 7) {
                        { Text(stringResource(R.string.amoled_accent_custom_invalid)) }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.reborn_studio_custom_hue), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(Brush.horizontalGradient(Rainbow)),
                )
                Slider(
                    value = hue,
                    onValueChange = { value ->
                        hue = value
                        android.graphics.Color.colorToHSV(parsed ?: AmoledPresets.SPRITE_TEAL, hsv)
                        hsv[0] = value
                        hsv[1] = hsv[1].coerceAtLeast(0.55f)
                        hsv[2] = hsv[2].coerceAtLeast(0.7f)
                        text = hexOf(android.graphics.Color.HSVToColor(hsv))
                    },
                    valueRange = 0f..360f,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = parsed != null,
                onClick = {
                    parsed?.let { onPick(hexOf(it)) }
                    onDismiss()
                },
            ) { Text(stringResource(R.string.reborn_studio_use)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.reborn_cancel)) }
        },
    )
}

// Apps

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppsCard(
    apps: List<StudioState.AppRow>?,
    scope: Set<String>?,
    modifier: Modifier,
    onOpenApps: (AppFilter) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val counts = remember(apps, scope) { AppFilter.entries.associateWith { f -> apps?.count { f.matches(it, scope) } ?: 0 } }
    SectionCard(modifier = modifier, onClick = { onOpenApps(AppFilter.All) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShapedIcon(
                icon = R.drawable.ic_reborn_apps,
                container = scheme.secondaryContainer,
                content = scheme.onSecondaryContainer,
                size = 52.dp,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (apps == null) stringResource(R.string.reborn_studio_apps)
                    else stringResource(R.string.reborn_studio_manage_apps, apps.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.reborn_studio_apps_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            Icon(painterResource(R.drawable.ic_reborn_chevron), contentDescription = null, tint = scheme.onSurfaceVariant)
        }
        val shortcuts = listOf(AppFilter.Themed, AppFilter.NeedsScope, AppFilter.Restart, AppFilter.Unsupported, AppFilter.Changed)
            .filter { (counts[it] ?: 0) > 0 }
        if (shortcuts.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                shortcuts.forEach { filter ->
                    AssistChip(
                        onClick = { onOpenApps(filter) },
                        label = {
                            Text(stringResource(R.string.reborn_studio_filter_count, stringResource(filter.label), counts[filter] ?: 0))
                        },
                        leadingIcon = {
                            Icon(painterResource(filter.icon), contentDescription = null, Modifier.size(AssistChipDefaults.IconSize))
                        },
                    )
                }
            }
        }
    }
}

// Small parts shared by the Studio's screens

/** A small status chip: an icon and a short text on a quiet container (never colour alone). */
@Composable
internal fun MiniChip(text: String, @DrawableRes icon: Int, status: StatusColors, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(status.container)
            .padding(start = 6.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = status.accent, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = status.onContainer,
            maxLines = 1,
        )
    }
}

@Composable
internal fun RouteChip(route: AmoledState.Route, modifier: Modifier = Modifier) {
    val colors = LocalRebornColors.current
    val scheme = MaterialTheme.colorScheme
    when (route) {
        AmoledState.Route.OVERLAY -> MiniChip(stringResource(R.string.reborn_studio_route_native), R.drawable.ic_reborn_ok, colors.ok, modifier)
        AmoledState.Route.MODULE -> MiniChip(
            stringResource(R.string.reborn_studio_route_module), R.drawable.ic_reborn_module,
            StatusColors(scheme.primary, scheme.onPrimary, scheme.primaryContainer, scheme.onPrimaryContainer), modifier,
        )
        AmoledState.Route.UNSUPPORTED -> MiniChip(stringResource(R.string.reborn_studio_route_unsupported), R.drawable.ic_reborn_error, colors.error, modifier)
        AmoledState.Route.NONE -> MiniChip(stringResource(R.string.reborn_studio_route_none), R.drawable.ic_reborn_off, colors.unknown, modifier)
    }
}

/** Shows a message, with an action when [action] is given, replacing the one on screen. */
@Composable
internal fun rememberActionSnackbar(): (message: String, action: String?, onAction: (() -> Unit)?) -> Unit {
    val host = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    return remember(host, scope) {
        { message, action, onAction ->
            scope.launch {
                host.currentSnackbarData?.dismiss()
                val result = host.showSnackbar(
                    message = message,
                    actionLabel = action,
                    duration = if (action != null) SnackbarDuration.Long else SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) onAction?.invoke()
            }
        }
    }
}
