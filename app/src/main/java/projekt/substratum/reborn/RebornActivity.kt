/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import projekt.substratum.common.RebornInit
import projekt.substratum.reborn.amoled.AmoledStudio
import projekt.substratum.reborn.amoled.JobsFlow
import projekt.substratum.reborn.applied.AppliedRepository
import projekt.substratum.reborn.applied.AppliedScreen
import projekt.substratum.reborn.applied.UndoEverythingHost
import projekt.substratum.reborn.components.LocalSnackbar
import projekt.substratum.reborn.components.RebornSnackbarHost
import projekt.substratum.reborn.components.design.LocalNavBarFab
import projekt.substratum.reborn.components.design.LocalNavBarSpace
import projekt.substratum.reborn.components.design.NavBarFabSlot
import projekt.substratum.reborn.components.design.NavDest
import projekt.substratum.reborn.components.design.RebornNavBar
import projekt.substratum.reborn.components.design.RebornNavBarDefaults
import projekt.substratum.reborn.components.design.fadeThrough
import projekt.substratum.reborn.create.CreateScreen
import projekt.substratum.reborn.engine.EngineScreen
import projekt.substratum.reborn.create.InspectScreen
import projekt.substratum.reborn.home.AboutScreen
import projekt.substratum.reborn.home.HomeScreen
import projekt.substratum.reborn.home.WhatsNew
import projekt.substratum.reborn.home.WhatsNewSheet
import projekt.substratum.reborn.nav.LocalRouteScope
import projekt.substratum.reborn.nav.LocalSharedScope
import projekt.substratum.reborn.nav.NavMove
import projekt.substratum.reborn.nav.RebornLinks
import projekt.substratum.reborn.nav.RebornNavigator
import projekt.substratum.reborn.nav.RebornTab
import projekt.substratum.reborn.nav.Route
import projekt.substratum.reborn.nav.ThemeId
import projekt.substratum.reborn.nav.rememberRebornNavigator
import projekt.substratum.reborn.setup.SetupScreen
import projekt.substratum.reborn.theme.RebornTheme
import projekt.substratum.reborn.theme.isDarkPreferred
import projekt.substratum.reborn.themes.ThemeDetailScreen
import projekt.substratum.reborn.themes.ThemesScreen
import projekt.substratum.util.helpers.LocaleHelper

/** An intent's extras, waiting to be resolved into a tab and its routes ([RebornLinks]). */
class RebornLink(val tab: String?, val theme: String?, val open: String?)

/**
 * The Reborn shell: Home, Themes, Create and Applied, each with its own back stack of routes (a
 * theme's page, its Studio, the inspector), the setup checklist on the first launch, What's new
 * once per release, and About. Started by [projekt.substratum.LauncherActivity] (also for the
 * launcher shortcuts); the extras [EXTRA_TAB], [EXTRA_THEME] and [EXTRA_OPEN] are resolved by
 * [RebornLinks].
 */
class RebornActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TAB = "tab"
        const val EXTRA_THEME = "theme"
        /** "detail" (a theme's page) or "studio" (its Studio) */
        const val EXTRA_OPEN = "open"

        fun linkOf(intent: Intent?): RebornLink? {
            if (intent == null) return null
            val tab = intent.getStringExtra(EXTRA_TAB)
            val theme = intent.getStringExtra(EXTRA_THEME)
            val open = intent.getStringExtra(EXTRA_OPEN)
            if (tab == null && theme.isNullOrBlank() && open == null) return null
            return RebornLink(tab, theme, open)
        }
    }

    private val link = mutableStateOf<RebornLink?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val dark = isDarkPreferred(this, systemDark)
        // Bars drawn over the content, their icons matched to the theme the app uses (which can
        // differ from the system's when chosen in Settings)
        val bars = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
        else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        super.onCreate(savedInstanceState)
        RebornInit.start(this)
        JobsFlow.start(this)
        // After a process death the saved back stacks come back instead
        if (savedInstanceState == null) link.value = linkOf(intent)
        setContent {
            RebornTheme {
                RebornApp(link)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        linkOf(intent)?.let { link.value = it }
    }
}

private fun stateKey(tab: RebornTab, route: Route) = tab.name + "|" + route.key

@Composable
fun RebornApp(link: MutableState<RebornLink?>) {
    val context = LocalContext.current
    val navigator = rememberRebornNavigator()
    val holder = rememberSaveableStateHolder()
    // A route that left its stack takes its saved state (scroll, fields) with it
    navigator.onRemoved = { tab, keys -> keys.forEach { holder.removeState(tab.name + "|" + it) } }
    var showSetup by rememberSaveable { mutableStateOf(!RebornInit.isSetupDone()) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showWhatsNew by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val motion = MaterialTheme.motionScheme
    val restartPending by AppliedRepository.restartPending.collectAsState()
    val destinations = remember { RebornTab.entries.map { NavDest(it.key, it.label, it.icon, it.selectedIcon) } }
    // Each tab's root may put a FAB beside the floating bar (Themes: Add a theme)
    val fabSlots = remember { RebornTab.entries.associateWith { NavBarFabSlot() } }

    /** The Studio of the engine theme in use (Reborn AMOLED when none), over its page in Themes. */
    fun openStudioInUse() {
        scope.launch {
            val id = ThemeId.ofPackage(RebornActions.engineThemeInUse(context))
            showAbout = false
            navigator.open(RebornTab.Themes, listOf(Route.Theme(id), Route.Studio(id)))
        }
    }

    LaunchedEffect(link.value) {
        val wanted = link.value ?: return@LaunchedEffect
        // Only a Studio link without a theme needs the engine's state (a file, read on IO)
        val studio = wanted.tab.equals("amoled", true) || wanted.tab.equals("studio", true)
        val inUse = if (studio && wanted.theme.isNullOrBlank()) RebornActions.engineThemeInUse(context) else null
        val (tab, routes) = RebornLinks.resolve(wanted.tab, wanted.theme, wanted.open, inUse)
        showAbout = false
        navigator.open(tab, routes)
        link.value = null
    }

    // At a tab's root (other than Home), back goes Home; at Home's root it leaves
    BackHandler(enabled = !showSetup && navigator.tab != RebornTab.Home && !navigator.canPop) {
        navigator.select(RebornTab.Home)
    }

    // This release's highlights, once, when Home (or a tab a link asked for) is up
    LaunchedEffect(showSetup) {
        if (showSetup || !WhatsNew.isNew()) return@LaunchedEffect
        delay(700)
        WhatsNew.markSeen()
        showWhatsNew = true
    }

    AnimatedContent(
        targetState = showSetup,
        transitionSpec = {
            (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.97f)) togetherWith
                fadeOut(motion.fastEffectsSpec())
        },
        label = "setup",
    ) { setup ->
        if (setup) {
            SetupScreen(onDone = {
                RebornInit.setSetupDone()
                showSetup = false
            })
        } else {
            CompositionLocalProvider(LocalSnackbar provides snackbar) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    // The routes draw under the floating bar: a tab's root leaves room for it at
                    // the end of its list ([LocalNavBarSpace]) and may put its FAB beside it
                    AnimatedContent(
                        targetState = navigator.tab,
                        transitionSpec = { fadeThrough(motion) },
                        label = "tab",
                    ) { tab ->
                        TabStack(tab, navigator, holder, backEnabled = !showAbout) { route ->
                            val root = route == tab.root
                            CompositionLocalProvider(
                                LocalNavBarSpace provides if (root) RebornNavBarDefaults.ContentSpace else 0.dp,
                                LocalNavBarFab provides if (root) fabSlots.getValue(tab) else null,
                            ) {
                                RouteContent(
                                    tab = tab,
                                    route = route,
                                    navigator = navigator,
                                    homeActive = !showAbout && !showWhatsNew,
                                    onOpenAbout = { showAbout = true },
                                    onOpenWhatsNew = { showWhatsNew = true },
                                    onOpenStudioInUse = ::openStudioInUse,
                                )
                            }
                        }
                    }
                    // The floating bar on the tabs' roots; a pushed page (a theme, its Studio)
                    // has the whole screen and its own actions
                    AnimatedVisibility(
                        visible = !navigator.canPop,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(),
                        enter = slideInVertically(motion.defaultSpatialSpec()) { it } + fadeIn(motion.defaultEffectsSpec()),
                        exit = slideOutVertically(motion.fastSpatialSpec()) { it } + fadeOut(motion.fastEffectsSpec()),
                    ) {
                        Box(contentAlignment = Alignment.BottomCenter) {
                            // A soft fade under the bar, so a list scrolling beneath it does not
                            // fight its icons (draws only: touches go through to the list): 112 dp
                            // over a gesture bar, the page colour at the bottom, 65 % of it 38 %
                            // up, clear at the top, the whole at 90 %
                            val page = MaterialTheme.colorScheme.background
                            val inset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(inset + RebornNavBarDefaults.ContentSpace)
                                    .background(
                                        Brush.verticalGradient(
                                            0f to ComposeColor.Transparent,
                                            0.62f to page.copy(alpha = 0.65f * 0.9f),
                                            1f to page.copy(alpha = 0.9f),
                                        ),
                                    ),
                            )
                            // It ticks on every tap itself
                            RebornNavBar(
                                destinations = destinations,
                                selected = navigator.tab.key,
                                onSelect = { d -> RebornTab.of(d.key)?.let(navigator::select) },
                                onReselect = { d -> RebornTab.of(d.key)?.let(navigator::select) },
                                badges = if (restartPending) setOf(RebornTab.Applied.key) else emptySet(),
                                floatingActionButton = fabSlots.getValue(navigator.tab).content,
                            )
                        }
                    }
                    // Over the bar (and a page's floating toolbar)
                    RebornSnackbarHost(
                        snackbar,
                        Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = RebornNavBarDefaults.ContentSpace),
                    )
                    // About, over the tabs (they keep their state underneath), on the expressive springs
                    AnimatedVisibility(
                        visible = showAbout,
                        enter = slideInHorizontally(motion.defaultSpatialSpec()) { it / 4 } +
                            fadeIn(motion.defaultEffectsSpec()),
                        exit = slideOutHorizontally(motion.fastSpatialSpec()) { it / 4 } +
                            fadeOut(motion.fastEffectsSpec()),
                    ) {
                        AboutScreen(
                            onBack = { showAbout = false },
                            onWhatsNew = { showWhatsNew = true },
                        )
                    }
                    // Undo everything (Applied, Home): its question, progress and result
                    UndoEverythingHost()
                }
            }
        }
    }

    if (showWhatsNew && !showSetup) {
        WhatsNewSheet(
            onDismiss = { showWhatsNew = false },
            onExploreThemes = {
                showAbout = false
                navigator.open(RebornTab.Themes, emptyList())
            },
        )
    }
}

/**
 * One tab's back stack: only its top route is composed, inside the holder's saved state for that
 * route (lists keep their scroll), in one SharedTransitionLayout so a theme card and its page
 * morph into each other. Push slides in from the end; pop is the reverse. A pushed route pops
 * with the predictive back gesture: the page shrinks to 90 % with rounding corners as it is
 * dragged, and springs back when the gesture is cancelled.
 */
@Composable
private fun TabStack(
    tab: RebornTab,
    navigator: RebornNavigator,
    holder: SaveableStateHolder,
    backEnabled: Boolean,
    content: @Composable (Route) -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val scope = rememberCoroutineScope()
    val canPop = navigator.stack(tab).size > 1
    var backKey by remember { mutableStateOf<String?>(null) }
    val backProgress = remember { Animatable(0f) }
    // Before the routes: their own handlers (a FAB menu, the Studio's app list) come first
    PredictiveBackHandler(enabled = backEnabled && canPop && tab == navigator.tab) { events ->
        backKey = navigator.top(tab).key
        backProgress.snapTo(0f)
        try {
            events.collect { backProgress.snapTo(it.progress) }
            navigator.pop()
        } catch (e: CancellationException) {
            scope.launch { backProgress.animateTo(0f, motion.fastSpatialSpec()) }
            throw e
        }
    }
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedScope provides this) {
            AnimatedContent(
                targetState = navigator.top(tab),
                contentKey = { it.key },
                transitionSpec = {
                    if (navigator.lastMove == NavMove.Pop) {
                        (fadeIn(motion.defaultEffectsSpec()) togetherWith
                            (slideOutHorizontally(motion.fastSpatialSpec()) { it / 4 } + fadeOut(motion.fastEffectsSpec())))
                            .apply { targetContentZIndex = -1f }
                    } else {
                        (slideInHorizontally(motion.defaultSpatialSpec()) { it / 4 } + fadeIn(motion.defaultEffectsSpec())) togetherWith
                            fadeOut(motion.fastEffectsSpec())
                    }
                },
                label = "route",
            ) { route ->
                CompositionLocalProvider(LocalRouteScope provides this) {
                    DisposableEffect(route.key) {
                        onDispose {
                            // The page that was dragged back is gone: the next one starts unscaled
                            if (backKey == route.key) {
                                backKey = null
                                scope.launch { backProgress.snapTo(0f) }
                            }
                        }
                    }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val p = if (route.key == backKey) backProgress.value else 0f
                                if (p > 0f) {
                                    val scale = 1f - 0.1f * p
                                    scaleX = scale
                                    scaleY = scale
                                    shape = RoundedCornerShape(32.dp * p)
                                    clip = true
                                }
                            },
                    ) {
                        holder.SaveableStateProvider(stateKey(tab, route)) { content(route) }
                    }
                }
            }
        }
    }
}

/** What each route shows, and where its links go. */
@Composable
private fun RouteContent(
    tab: RebornTab,
    route: Route,
    navigator: RebornNavigator,
    homeActive: Boolean,
    onOpenAbout: () -> Unit,
    onOpenWhatsNew: () -> Unit,
    onOpenStudioInUse: () -> Unit,
) {
    val reselect = navigator.reselectSignal(tab)
    when (route) {
        Route.Home -> HomeScreen(
            onOpenEngine = { navigator.push(Route.Engine) },
            // Through Engine (as the setup link does), so back from the checklist lands on Engine
            onOpenSetup = { navigator.open(RebornTab.Home, listOf(Route.Engine, Route.Setup)) },
            onOpenStudio = onOpenStudioInUse,
            onOpenTheme = { navigator.push(Route.Theme(it)) },
            onOpenThemes = { navigator.select(RebornTab.Themes) },
            onOpenCreate = { navigator.select(RebornTab.Create) },
            onOpenApplied = { navigator.select(RebornTab.Applied) },
            onOpenAbout = onOpenAbout,
            onOpenWhatsNew = onOpenWhatsNew,
            active = homeActive && navigator.tab == RebornTab.Home,
            reselect = reselect,
        )
        Route.Engine -> EngineScreen(
            onBack = { navigator.pop() },
            onOpenSetup = { navigator.push(Route.Setup) },
        )
        // The checklist as a page: Done and back return to where it was opened
        Route.Setup -> SetupScreen(onDone = { navigator.pop() })
        Route.Themes -> ThemesScreen(
            onOpenTheme = { navigator.push(Route.Theme(it)) },
            onOpenCreate = { navigator.select(RebornTab.Create) },
            reselect = reselect,
        )
        Route.Create -> CreateScreen(
            onDesign = onOpenStudioInUse,
            onInspect = { navigator.push(Route.Inspect()) },
            reselect = reselect,
        )
        Route.Applied -> AppliedScreen(
            onOpenTheme = { navigator.push(Route.Theme(it)) },
            onOpenStudio = { navigator.push(Route.Studio(it)) },
            onOpenThemes = { navigator.select(RebornTab.Themes) },
            onOpenCreate = { navigator.select(RebornTab.Create) },
            reselect = reselect,
        )
        is Route.Theme -> ThemeDetailScreen(
            id = route.id,
            onBack = { navigator.pop() },
            onOpenStudio = { section -> navigator.push(Route.Studio(route.id, section)) },
        )
        is Route.Studio -> AmoledStudio(
            id = route.id,
            section = route.section,
            onBack = { navigator.pop() },
            onOpenThemes = { navigator.open(RebornTab.Themes, emptyList()) },
        )
        is Route.Inspect -> InspectScreen(pkg = route.pkg, onBack = { navigator.pop() })
    }
}
