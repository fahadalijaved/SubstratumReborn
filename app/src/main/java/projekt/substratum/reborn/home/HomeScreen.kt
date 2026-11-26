/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import projekt.substratum.R
import projekt.substratum.common.platform.RebornStatus
import projekt.substratum.reborn.RebornActions
import projekt.substratum.reborn.RebornCache
import projekt.substratum.reborn.amoled.JobsFlow
import projekt.substratum.reborn.amoled.StudioState
import projekt.substratum.reborn.applied.AppliedRepository
import projekt.substratum.reborn.components.BrandMark
import projekt.substratum.reborn.components.Entrance
import projekt.substratum.reborn.components.rememberResumeCount
import projekt.substratum.reborn.components.rememberRoot
import projekt.substratum.reborn.components.rememberSnackbar
import projekt.substratum.reborn.components.design.LocalNavBarSpace
import projekt.substratum.reborn.components.design.MenuAction
import projekt.substratum.reborn.components.design.MenuGroup
import projekt.substratum.reborn.components.design.RebornOverflow
import projekt.substratum.reborn.components.design.RebornPullToRefresh
import projekt.substratum.reborn.components.design.pressShape
import projekt.substratum.reborn.components.design.rememberRefreshableRoot
import projekt.substratum.reborn.components.design.softTitle
import projekt.substratum.reborn.nav.OnReselect
import projekt.substratum.reborn.nav.ThemeId
import projekt.substratum.reborn.nav.enginePackage
import projekt.substratum.reborn.setup.openSetupSteps
import projekt.substratum.reborn.theme.LocalRebornColors
import projekt.substratum.reborn.themes.ThemeActions
import projekt.substratum.reborn.themes.loadThemes
import projekt.substratum.reborn.themes.rememberThemeLooks

/** At most this many themes in Try next */
private const val TRY_NEXT_MAX = 8

/**
 * Home, as calm and short as the approved mockup: a 64 dp brand bar (the swatch deck, "Reborn" over
 * a quiet "Substratum", the engine pill that opens the Engine screen, the overflow), the
 * colour-blocked Now card (the theme in use), the quick actions (Re-apply, Customize, Undo), "Try
 * next" (the other themes, in their colours) and the way into Create. What the engine needs (fix
 * banners, "Update ready", pause, the checks) lives on the Engine screen; the pill turns amber or
 * red and says so in a word when something there needs a look.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    /** Nothing covers Home (About, a sheet): its ambient shape may move */
    active: Boolean = true,
    /** The tab's reselect count: scrolls to the top */
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
    val newApps by rememberRoot(resume, refresh, appliedVersion, initial = RebornCache.newApps) {
        readNewApps(context).also { RebornCache.newApps = it }
    }
    val job by JobsFlow.job.collectAsStateWithLifecycle()
    val model = remember(status) { status?.let { describe(context, it) } }
    val setupLeft = remember(status) { status?.let { openSetupSteps(context, it) } ?: 0 }

    // The theme in use, and its previews (its look and accent for the chips)
    val now = nowState(applied, themes, job)
    val nowId = nowTheme(applied)
    val nowItem = nowId?.let { themes?.find(it) }
    val nowLooks by rememberThemeLooks(nowItem)
    // Try next: the themes not in use, the built-ins first (the Themes order)
    val tryNext = remember(themes, nowId) {
        themes?.themes.orEmpty().filter { it.id != nowId && !it.inUse }.take(TRY_NEXT_MAX)
    }

    // The entrance plays once; coming back to the tab shows Home at once
    var entered by rememberSaveable { mutableStateOf(false) }
    val animate = remember { !entered }
    LaunchedEffect(Unit) { entered = true }

    fun reapply() {
        StudioState.bind(context)
        StudioState.chooseTheme(ThemeId.ofPackage(applied?.amoled?.theme).enginePackage)
        StudioState.applyAll()
    }

    fun undo() {
        val snapshot = applied
        if (snapshot == null) snackbar(context.getString(R.string.reborn_reading))
        else AppliedRepository.askUndo(snapshot.undoPlan())
    }

    val list = rememberLazyListState()
    OnReselect(reselect) { list.animateScrollToItem(0) }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + LocalNavBarSpace.current + 24.dp
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        RebornPullToRefresh(
            refreshing = home.refreshing,
            onRefresh = {
                home.refresh()
                refresh++
            },
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottom),
            ) {
                item(key = "brand") {
                    BrandBar(
                        model = model,
                        setupLeft = setupLeft,
                        onOpenEngine = onOpenEngine,
                        onOpenSetup = onOpenSetup,
                        menu = {
                            listOf(
                                MenuGroup(
                                    listOf(
                                        MenuAction(
                                            stringResource(R.string.reborn_settings_title),
                                            R.drawable.ic_reborn_settings,
                                            onClick = { RebornActions.openSettings(context) },
                                        ),
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
                        },
                    )
                }
                item(key = "now") {
                    Entrance(0, animate) {
                        NowCard(
                            now = now,
                            looks = nowLooks,
                            installedThemes = themes?.themes?.size,
                            newApps = newApps,
                            onPickTheme = onOpenThemes,
                            onOpenCreate = onOpenCreate,
                            onOpenApplied = onOpenApplied,
                            running = active,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
                        )
                    }
                }
                item(key = "actions") {
                    Entrance(1, animate) {
                        val classic = now as? Now.Classic
                        NowActions(
                            reapplyEnabled = now is Now.Engine || classic != null,
                            reapplyFilled = (now as? Now.Engine)?.needsReapply == true,
                            undoEnabled = now is Now.Engine || classic != null,
                            onReapply = {
                                if (classic != null) {
                                    // Classic overlays are built and applied again in Classic's theme screen
                                    if (!ThemeActions.chooseOverlays(context, classic.pkg)) onOpenTheme(classic.id)
                                } else reapply()
                            },
                            onCustomize = { if (classic != null) onOpenTheme(classic.id) else onOpenStudio() },
                            onUndo = ::undo,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                        )
                    }
                }
                if (tryNext.isNotEmpty()) {
                    item(key = "try_header") {
                        Entrance(2, animate) { TryNextHeader(onOpenThemes) }
                    }
                    item(key = "try") {
                        Entrance(3, animate) { TryNext(tryNext, onOpenTheme) }
                    }
                }
                item(key = "create") {
                    Entrance(4, animate) {
                        MakeYourOwn(onOpenCreate, Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp))
                    }
                }
            }
        }
    }
}

/**
 * The brand bar (64 dp): the swatch deck on its Cookie12Sided plate, "Reborn" (22 sp, ROND 60) over
 * a quiet "Substratum", the engine pill and the overflow menu.
 */
@Composable
private fun BrandBar(
    model: HomeStatus?,
    setupLeft: Int,
    onOpenEngine: () -> Unit,
    onOpenSetup: () -> Unit,
    menu: @Composable () -> List<MenuGroup>,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            BrandMark()
            Spacer(Modifier.width(12.dp))
            Column(Modifier.semantics(mergeDescendants = true) { heading() }) {
                Text(
                    stringResource(R.string.reborn_home_title),
                    style = MaterialTheme.typography.softTitle.copy(lineHeight = 26.sp, letterSpacing = (-0.2).sp),
                    maxLines = 1,
                )
                Text(
                    stringResource(R.string.reborn_home_brand_sub),
                    style = MaterialTheme.typography.labelSmall.copy(lineHeight = 14.sp, letterSpacing = 0.3.sp),
                    color = scheme.onSurfaceVariant.copy(alpha = 0.75f),
                    maxLines = 1,
                )
            }
        }
        EnginePill(model, setupLeft, onOpenEngine, onOpenSetup)
        RebornOverflow(groups = menu)
    }
}

/**
 * The engine pill (36 dp): a Sunny dot and a word on the status colours, then a chevron. "All
 * good" in green; "Finish setup" in amber while required setup steps are open (it opens the
 * checklist over Engine); the status headline in amber or red when something needs a look
 * ("Hooks paused", "Root missing"). Otherwise it opens the Engine screen.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EnginePill(
    model: HomeStatus?,
    setupLeft: Int,
    onOpenEngine: () -> Unit,
    onOpenSetup: () -> Unit,
) {
    val colors = LocalRebornColors.current
    val motion = MaterialTheme.motionScheme
    val haptics = LocalHapticFeedback.current
    val setup = model != null && setupLeft > 0 && model.level == HeroLevel.Ok
    val tone = when {
        model == null -> colors.unknown
        setup -> colors.warn
        model.level == HeroLevel.Ok -> colors.ok
        model.level == HeroLevel.Warn -> colors.warn
        else -> colors.error
    }
    val text = when {
        model == null -> stringResource(R.string.reborn_engine_pill_checking)
        setup -> stringResource(R.string.reborn_engine_pill_setup)
        model.level == HeroLevel.Ok && model.hooksUpdate != null -> stringResource(R.string.reborn_engine_pill_update)
        model.level == HeroLevel.Ok -> stringResource(R.string.reborn_engine_pill_ok)
        else -> stringResource(model.headline)
    }
    val container by animateColorAsState(tone.container, motion.defaultEffectsSpec(), label = "pillContainer")
    val content by animateColorAsState(tone.onContainer, motion.defaultEffectsSpec(), label = "pillContent")
    val dot by animateColorAsState(tone.accent, motion.defaultEffectsSpec(), label = "pillDot")
    val interaction = remember { MutableInteractionSource() }
    val shape = pressShape(interaction, rest = 18.dp, pressed = 10.dp)
    val sunny = MaterialShapes.Sunny.toShape()
    val description = stringResource(R.string.reborn_engine_pill_description, text)
    Surface(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            if (setup) onOpenSetup() else onOpenEngine()
        },
        interactionSource = interaction,
        shape = shape,
        color = container,
        contentColor = content,
    ) {
        Row(
            Modifier
                .height(36.dp)
                .padding(start = 10.dp, end = 6.dp)
                .clearAndSetSemantics { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(16.dp)
                    .clip(sunny)
                    .background(dot),
            )
            Spacer(Modifier.width(6.dp))
            AnimatedContent(
                targetState = text,
                transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
                label = "pill",
            ) { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.labelLargeEmphasized.copy(fontSize = 13.sp, lineHeight = 20.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(4.dp))
            Icon(painterResource(R.drawable.ic_reborn_chevron), contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

/** "Try next" (22 sp, ROND 60) with a "See all" text button (to Themes). */
@Composable
private fun TryNextHeader(onSeeAll: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 32.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.reborn_home_try_next),
            style = MaterialTheme.typography.softTitle,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        TextButton(onClick = onSeeAll, shapes = ButtonDefaults.shapes()) {
            Text(
                stringResource(R.string.reborn_see_all),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

/**
 * Into Create (88 dp, 28 dp corners, surfaceContainer): a 56 dp Flower plate in tertiaryContainer
 * with a filled brush, "Make your own theme" over "Palette, preview, export", and an arrow button.
 * Its corners tighten while pressed.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MakeYourOwn(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val shape = pressShape(interaction, rest = 28.dp, pressed = 20.dp)
    val flower = MaterialShapes.Flower.toShape()
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = shape,
        color = scheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(flower)
                    .background(scheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_reborn_create_filled),
                    contentDescription = null,
                    tint = scheme.tertiary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.reborn_home_create_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    stringResource(R.string.reborn_home_create_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(16.dp))
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(scheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_reborn_arrow), contentDescription = null, modifier = Modifier.size(24.dp))
            }
        }
    }
}
