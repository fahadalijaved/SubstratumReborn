/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.home

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.withContext
import projekt.substratum.R
import projekt.substratum.common.platform.RebornStatus
import projekt.substratum.reborn.RebornActions
import projekt.substratum.reborn.RebornCache
import projekt.substratum.reborn.amoled.JobsFlow
import projekt.substratum.reborn.amoled.StudioState
import projekt.substratum.reborn.applied.AppliedRepository
import projekt.substratum.reborn.applied.AppliedSnapshot
import projekt.substratum.reborn.applied.AppliedSource
import projekt.substratum.reborn.components.BrandMark
import projekt.substratum.reborn.components.Entrance
import projekt.substratum.reborn.components.FixBanner
import projekt.substratum.reborn.components.SectionCard
import projekt.substratum.reborn.components.SectionHeader
import projekt.substratum.reborn.components.ShapedIcon
import projekt.substratum.reborn.components.rememberResumeCount
import projekt.substratum.reborn.components.rememberRoot
import projekt.substratum.reborn.components.rememberSnackbar
import projekt.substratum.reborn.components.design.ActionIcon
import projekt.substratum.reborn.components.design.MenuAction
import projekt.substratum.reborn.components.design.MenuGroup
import projekt.substratum.reborn.components.design.RebornFlexibleScaffold
import projekt.substratum.reborn.components.design.RebornOverflow
import projekt.substratum.reborn.components.design.RebornPullToRefresh
import projekt.substratum.reborn.components.design.heroNumber
import projekt.substratum.reborn.components.design.pressShape
import projekt.substratum.reborn.components.design.rememberRefreshableRoot
import projekt.substratum.reborn.engine.Confirm
import projekt.substratum.reborn.engine.EngineDialogs
import projekt.substratum.reborn.engine.HooksUpdateCard
import projekt.substratum.reborn.engine.rememberEngineActions
import projekt.substratum.reborn.nav.OnReselect
import projekt.substratum.reborn.nav.ThemeId
import projekt.substratum.reborn.nav.enginePackage
import projekt.substratum.reborn.setup.openSetupSteps
import projekt.substratum.reborn.showcase.ShowcaseMode
import projekt.substratum.reborn.showcase.ThemeShowcase
import projekt.substratum.reborn.theme.LocalRebornColors
import projekt.substratum.reborn.themes.ThemeItem
import projekt.substratum.reborn.themes.ThemeLooks
import projekt.substratum.reborn.themes.ThemePreviews
import projekt.substratum.reborn.themes.ThemeTile
import projekt.substratum.reborn.themes.ThemesSnapshot
import projekt.substratum.reborn.themes.heroLoader
import projekt.substratum.reborn.themes.loadThemes
import projekt.substratum.reborn.themes.rememberThemeLooks

/** The width of a theme card in Home's row (the Themes grid's minimum) */
private val RowTileWidth = 156.dp

/**
 * Home leads with the theme in use (the Now card), then a one-line engine strip (Engine screen),
 * your themes, the before/after showcase pointed at them, a way into Create, what is applied now
 * and the safety row (pause the module, undo everything). Fix banners and "Update ready" show above
 * the Now card only when something needs them.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    onOpenEngine: () -> Unit,
    onOpenSetup: () -> Unit,
    /** The Studio of the engine theme in use (Reborn AMOLED when none) */
    onOpenStudio: () -> Unit,
    /** A theme's page */
    onOpenTheme: (ThemeId) -> Unit,
    onOpenThemes: () -> Unit,
    onOpenCreate: () -> Unit,
    onOpenApplied: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenWhatsNew: () -> Unit,
    /** Nothing covers Home (About, a sheet): its showcase may page on its own */
    active: Boolean = true,
    /** The tab's reselect count: scrolls to the top and expands the bar */
    reselect: Int = 0,
) {
    val context = LocalContext.current
    val snackbar = rememberSnackbar()
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
    val themes by rememberRoot(resume, refresh, appliedVersion, initial = RebornCache.themes) {
        loadThemes(context).also { RebornCache.themes = it }
    }
    val job by JobsFlow.job.collectAsStateWithLifecycle()
    val model = remember(status) { status?.let { describe(context, it) } }
    val setupLeft = remember(status) { status?.let { openSetupSteps(context, it) } ?: 0 }
    val actions = rememberEngineActions(model) { refresh++ }
    val busy = actions.busy

    // The theme in use, and its previews (palette and look summary)
    val nowId = nowTheme(applied)
    val nowItem = nowId?.let { themes?.find(it) }
    val nowLooks by rememberThemeLooks(nowItem)
    val showcaseThemes by rememberShowcaseThemes(themes, nowId)

    // The entrance plays once; coming back to the tab shows Home at once
    var entered by rememberSaveable { mutableStateOf(false) }
    val animate = remember { !entered }
    LaunchedEffect(Unit) { entered = true }

    fun reapply() {
        StudioState.bind(context)
        StudioState.chooseTheme(ThemeId.ofPackage(applied?.amoled?.theme).enginePackage)
        StudioState.applyAll()
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val list = rememberLazyListState()
    OnReselect(reselect) {
        scrollBehavior.state.heightOffset = 0f
        list.animateScrollToItem(0)
    }
    RebornFlexibleScaffold(
        title = stringResource(R.string.reborn_home_title),
        subtitle = stringResource(R.string.reborn_home_subtitle),
        scrollBehavior = scrollBehavior,
        connectScroll = false,
        navigationIcon = { BrandMark(Modifier.padding(start = 8.dp, end = 4.dp)) },
        actions = {
            ActionIcon(
                R.drawable.ic_reborn_settings,
                stringResource(R.string.reborn_settings_title),
                { RebornActions.openSettings(context) },
            )
            RebornOverflow {
                listOf(
                    MenuGroup(
                        listOf(
                            MenuAction(stringResource(R.string.reborn_about_menu), R.drawable.ic_reborn_info, onClick = onOpenAbout),
                            MenuAction(stringResource(R.string.reborn_about_whats_new), R.drawable.ic_reborn_themes, onClick = onOpenWhatsNew),
                        ),
                    ),
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
                state = list,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 4.dp,
                    bottom = padding.calculateBottomPadding() + 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val gutter = Modifier.padding(horizontal = 16.dp)
                items(model?.issues.orEmpty(), key = { "issue_" + it.key }) { issue ->
                    FixBanner(
                        title = issue.title,
                        text = issue.text,
                        fixLabel = stringResource(R.string.reborn_fix),
                        onFix = { actions.fix(issue.fix) },
                        warning = issue.warning,
                        busy = busy != null,
                        // Only the banner whose fix is running shows the indicator
                        working = busy != null && busy == actions.actionKey(issue.fix),
                        modifier = Modifier
                            .animateItem()
                            .then(gutter),
                    )
                }
                model?.hooksUpdate?.let { update ->
                    item(key = "hooks_update") {
                        HooksUpdateCard(
                            update = update,
                            busy = busy != null,
                            working = busy == "restart",
                            onRestart = { actions.confirm = Confirm.RestartSystemUi },
                            modifier = Modifier
                                .animateItem()
                                .then(gutter),
                        )
                    }
                }
                item(key = "now") {
                    Entrance(0, animate) {
                        NowCard(
                            applied = applied,
                            themes = themes,
                            job = job,
                            looks = nowLooks,
                            onCustomize = onOpenStudio,
                            onChangeTheme = onOpenThemes,
                            onOpenTheme = onOpenTheme,
                            onReapply = ::reapply,
                            onOpenStudio = onOpenStudio,
                            modifier = gutter,
                        )
                    }
                }
                item(key = "engine") {
                    Entrance(1, animate) {
                        EngineStrip(
                            model = model,
                            status = status,
                            setupLeft = setupLeft,
                            onOpenEngine = onOpenEngine,
                            onOpenSetup = onOpenSetup,
                            modifier = gutter,
                        )
                    }
                }
                item(key = "themes_header") {
                    Entrance(2, animate) {
                        Header(stringResource(R.string.reborn_home_your_themes), onOpenThemes, gutter)
                    }
                }
                item(key = "themes_row") {
                    Entrance(3, animate) {
                        YourThemes(themes, nowId, onOpenTheme, onOpenThemes)
                    }
                }
                item(key = "showcase") {
                    Entrance(4, animate) {
                        ThemeShowcase(
                            mode = ShowcaseMode.Themes(
                                themes = showcaseThemes,
                                inUse = nowId,
                                onOpenTheme = onOpenTheme,
                                onExplore = onOpenThemes,
                            ),
                            modifier = gutter,
                            active = active && actions.confirm == null,
                        )
                    }
                }
                item(key = "create") {
                    Entrance(5, animate) { CreateCard(onOpenCreate, gutter) }
                }
                item(key = "applied_header") {
                    Entrance(6, animate) {
                        Header(stringResource(R.string.reborn_applied_now), onOpenApplied, gutter)
                    }
                }
                item(key = "applied_tiles") {
                    Entrance(7, animate) { AppliedTiles(applied, onOpenApplied, gutter) }
                }
                item(key = "safety") {
                    Entrance(8, animate) {
                        SafetyRow(
                            paused = model?.hooksPaused == true,
                            busy = busy == "pause",
                            onPause = actions::askPause,
                            onUndoAll = {
                                val snapshot = applied
                                if (snapshot == null) snackbar(context.getString(R.string.reborn_reading))
                                else AppliedRepository.askUndo(snapshot.undoPlan())
                            },
                            modifier = gutter,
                        )
                    }
                }
            }
        }
    }
    EngineDialogs(actions)
}

/**
 * The themes "Try a theme" offers: every theme with enough colours of its own for a before and
 * after, the one in use first, then the built-ins. Read on the theme image loader.
 */
@Composable
private fun rememberShowcaseThemes(themes: ThemesSnapshot?, inUse: ThemeId?): State<List<ThemeLooks>?> {
    val context = LocalContext.current.applicationContext
    val look by StudioState.look.collectAsStateWithLifecycle()
    val initial = themes?.themes?.sortedBy { order(it, inUse) }?.let(::cachedShowcase)
    return produceState(initialValue = initial, themes, inUse, look) {
        val all = themes?.themes ?: return@produceState
        val ordered = all.sortedBy { order(it, inUse) }
        // The one in use (or Reborn AMOLED) first, so the showcase has its subject at once
        val first = ordered.take(1)
        withContext(heroLoader) { ThemePreviews.loadAll(context, first) }.filter { it.previewable }
            .takeIf { it.isNotEmpty() && value.isNullOrEmpty() }?.let { value = it }
        value = withContext(heroLoader) { ThemePreviews.loadAll(context, ordered) }.filter { it.previewable }
    }
}

private fun cachedShowcase(themes: List<ThemeItem>): List<ThemeLooks>? =
    themes.mapNotNull(ThemePreviews::last).filter { it.previewable }.takeIf { it.isNotEmpty() }

/** In use first, then the built-ins, then the rest by name (the Themes order). */
private fun order(theme: ThemeItem, inUse: ThemeId?): Int = when {
    theme.id == (inUse ?: ThemeId.RebornAmoled) -> 0
    theme.builtIn -> 1
    else -> 2
}

/** A section header with "See all". */
@Composable
private fun Header(text: String, onSeeAll: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        SectionHeader(text, Modifier.weight(1f))
        TextButton(onClick = onSeeAll, modifier = Modifier.padding(top = 10.dp), shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.reborn_see_all))
            Spacer(Modifier.width(4.dp))
            Icon(painterResource(R.drawable.ic_reborn_chevron), contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * One line about the engine, opening the Engine screen: a status dot and "Engine ready · One UI 9 ·
 * LSPosed", or the status headline when something is wrong; "Finish setup" (opening the
 * checklist) while required setup steps are open.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EngineStrip(
    model: HomeStatus?,
    status: RebornStatus.Snapshot?,
    setupLeft: Int,
    onOpenEngine: () -> Unit,
    onOpenSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalRebornColors.current
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val setup = model != null && setupLeft > 0
    val tone = when {
        model == null -> colors.unknown
        setup && model.level == HeroLevel.Ok -> colors.warn
        model.level == HeroLevel.Ok -> colors.ok
        model.level == HeroLevel.Warn -> colors.warn
        else -> colors.error
    }
    val container by animateColorAsState(if (colors.dark) scheme.surfaceContainer else tone.container, label = "stripContainer")
    val dot by animateColorAsState(tone.accent, label = "stripDot")
    val text = when {
        model == null -> stringResource(R.string.reborn_checking)
        setup -> pluralStringResource(R.plurals.reborn_home_finish_setup, setupLeft, setupLeft)
        model.level == HeroLevel.Ok && model.issues.isEmpty() -> listOfNotNull(
            stringResource(R.string.reborn_home_engine_ready),
            status?.oneUi?.removeSuffix(".0") ?: status?.android,
            stringResource(R.string.reborn_home_engine_lsposed).takeIf { status?.engine?.xposed == true },
        ).joinToString(" · ")
        else -> stringResource(model.headline)
    }
    val interaction = remember { MutableInteractionSource() }
    val shape = pressShape(interaction, rest = 28.dp, pressed = 18.dp)
    Surface(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            if (setup) onOpenSetup() else onOpenEngine()
        },
        interactionSource = interaction,
        shape = shape,
        color = container,
        border = if (colors.dark) BorderStroke(1.dp, colors.cardBorder) else null,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(18.dp)
                    .clip(MaterialShapes.Sunny.toShape())
                    .background(dot),
            )
            Spacer(Modifier.width(12.dp))
            val motion = MaterialTheme.motionScheme
            AnimatedContent(
                targetState = text,
                transitionSpec = {
                    fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec())
                },
                label = "strip",
                modifier = Modifier.weight(1f),
            ) { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                painterResource(R.drawable.ic_reborn_chevron),
                contentDescription = stringResource(if (setup) R.string.reborn_setup_action else R.string.reborn_engine_title),
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Your themes: compact cards (the one in use first, then the built-ins), each with its palette;
 * the last one leads to Themes to install or create one.
 */
@Composable
private fun YourThemes(
    themes: ThemesSnapshot?,
    inUse: ThemeId?,
    onOpenTheme: (ThemeId) -> Unit,
    onOpenThemes: () -> Unit,
) {
    val items = remember(themes, inUse) { themes?.themes.orEmpty().sortedBy { order(it, inUse) } }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items, key = { it.id.key }) { theme ->
            ThemeTile(
                theme = theme,
                onOpen = { onOpenTheme(theme.id) },
                onLongPress = null,
                modifier = Modifier.width(RowTileWidth),
            )
        }
        item(key = "more") { MoreTile(onOpenThemes, Modifier.width(RowTileWidth)) }
    }
}

/** The row's last card: install a theme from a file, or create one (both in Themes). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MoreTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val shape = pressShape(interaction, rest = 24.dp, pressed = 18.dp)
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = shape,
        color = scheme.surfaceContainerLow,
        modifier = modifier,
    ) {
        Column(Modifier.padding(6.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(5f / 4f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(scheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                ShapedIcon(
                    icon = R.drawable.ic_reborn_create,
                    container = scheme.primaryContainer,
                    content = scheme.onPrimaryContainer,
                    size = 56.dp,
                    shape = MaterialShapes.Cookie9Sided.toShape(),
                )
            }
            Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 6.dp)) {
                Text(
                    stringResource(R.string.reborn_home_more_themes),
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.reborn_home_more_themes_text),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Into Create: start from your look, test it live, share it as a Reborn engine theme. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CreateCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    SectionCard(modifier = modifier, onClick = onClick, contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShapedIcon(
                icon = R.drawable.ic_reborn_create,
                container = scheme.tertiaryContainer,
                content = scheme.onTertiaryContainer,
                size = 56.dp,
                shape = MaterialShapes.Flower.toShape(),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.reborn_home_create_title), style = MaterialTheme.typography.titleMediumEmphasized)
                Text(
                    stringResource(R.string.reborn_home_create_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(scheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_reborn_chevron), contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Applied now: apps themed, overlays and Quick Settings tweaks, each opening Applied. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppliedTiles(applied: AppliedSnapshot?, onOpenApplied: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CountTile(
            icon = R.drawable.ic_reborn_apps,
            container = scheme.primaryContainer,
            content = scheme.onPrimaryContainer,
            count = applied?.let { if (it.amoled.enabled) it.amoled.themed else 0 },
            label = stringResource(R.string.reborn_home_tile_apps),
            onClick = onOpenApplied,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        CountTile(
            icon = R.drawable.ic_reborn_layers,
            container = scheme.tertiaryContainer,
            content = scheme.onTertiaryContainer,
            count = applied?.count(AppliedSource.THEME_OVERLAY, true),
            label = stringResource(R.string.reborn_home_tile_overlays),
            onClick = onOpenApplied,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        CountTile(
            icon = R.drawable.ic_reborn_tune,
            container = scheme.secondaryContainer,
            content = scheme.onSecondaryContainer,
            count = applied?.count(AppliedSource.QS, true),
            label = stringResource(R.string.reborn_home_tile_qs),
            onClick = onOpenApplied,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CountTile(
    @DrawableRes icon: Int,
    container: Color,
    content: Color,
    count: Int?,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(modifier = modifier, onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        ShapedIcon(icon, container, content, size = 36.dp, shape = MaterialShapes.Cookie6Sided.toShape())
        Spacer(Modifier.height(10.dp))
        Text(
            count?.toString() ?: "–",
            style = MaterialTheme.typography.heroNumber.copy(fontSize = MaterialTheme.typography.headlineLarge.fontSize),
            maxLines = 1,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The safety row: pause or resume the module (asks first) and undo everything (shows its plan first). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SafetyRow(
    paused: Boolean,
    busy: Boolean,
    onPause: () -> Unit,
    onUndoAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val colors = LocalRebornColors.current
    val height = ButtonDefaults.MediumContainerHeight
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onPause()
            },
            enabled = !busy,
            shapes = ButtonDefaults.shapesFor(height),
            contentPadding = ButtonDefaults.contentPaddingFor(height),
            colors = if (paused) ButtonDefaults.filledTonalButtonColors(
                containerColor = colors.warn.container,
                contentColor = colors.warn.onContainer,
            ) else ButtonDefaults.filledTonalButtonColors(),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = height),
        ) {
            Icon(
                painterResource(if (paused) R.drawable.ic_reborn_play else R.drawable.ic_reborn_pause),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(if (paused) R.string.reborn_home_resume_module else R.string.reborn_home_pause_module),
                style = ButtonDefaults.textStyleFor(height),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FilledTonalButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onUndoAll()
            },
            shapes = ButtonDefaults.shapesFor(height),
            contentPadding = ButtonDefaults.contentPaddingFor(height),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = height),
        ) {
            Icon(painterResource(R.drawable.ic_reborn_undo), contentDescription = null, modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.reborn_action_undo_all),
                style = ButtonDefaults.textStyleFor(height),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
