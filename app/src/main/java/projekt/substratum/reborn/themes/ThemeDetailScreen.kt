/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.themes

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.text.format.DateUtils
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarExitDirection
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import projekt.substratum.R
import projekt.substratum.Substratum
import projekt.substratum.common.platform.AmoledEngine
import projekt.substratum.common.platform.AmoledJobs
import projekt.substratum.common.platform.AmoledRules
import projekt.substratum.common.platform.QsOptions
import projekt.substratum.reborn.RebornActions
import projekt.substratum.reborn.RebornCache
import projekt.substratum.reborn.amoled.JobUi
import projekt.substratum.reborn.amoled.JobsFlow
import projekt.substratum.reborn.amoled.StudioState
import projekt.substratum.reborn.amoled.rememberActionSnackbar
import projekt.substratum.reborn.components.AppIcon
import projekt.substratum.reborn.components.ChipState
import projekt.substratum.reborn.components.LocalSnackbar
import projekt.substratum.reborn.components.SectionCard
import projekt.substratum.reborn.components.StatusChip
import projekt.substratum.reborn.components.mock.BeforeAfter
import projekt.substratum.reborn.components.mock.MockRoles
import projekt.substratum.reborn.components.mock.QsMock
import projekt.substratum.reborn.components.mock.SettingsMock
import projekt.substratum.reborn.components.design.BackIcon
import projekt.substratum.reborn.components.design.MenuAction
import projekt.substratum.reborn.components.design.MenuGroup
import projekt.substratum.reborn.components.design.PageLoading
import projekt.substratum.reborn.components.design.RebornConfirmDialog
import projekt.substratum.reborn.components.design.RebornMenu
import projekt.substratum.reborn.components.design.RebornSwitch
import projekt.substratum.reborn.components.design.SectionLabel
import projekt.substratum.reborn.components.design.SegmentedGroup
import projekt.substratum.reborn.components.design.rebornSegmentColors
import projekt.substratum.reborn.components.design.rebornTopBarColors
import projekt.substratum.reborn.components.rememberResumeCount
import projekt.substratum.reborn.components.rememberRoot
import projekt.substratum.reborn.nav.SharedKeys
import projekt.substratum.reborn.nav.StudioSection
import projekt.substratum.reborn.nav.ThemeId
import projekt.substratum.reborn.nav.enginePackage
import projekt.substratum.reborn.nav.themeShared
import projekt.substratum.reborn.showcase.ShowcaseMode
import projekt.substratum.reborn.showcase.ThemeShowcase
import projekt.substratum.reborn.theme.LocalRebornColors
import java.util.concurrent.ConcurrentHashMap

/** The apps a theme changes on this phone, by theme and update (counting reads every package). */
private val coverage = ConcurrentHashMap<String, Coverage>()

/** What a theme changes here: engine themes, the apps its rules cover; classic, its targets. */
private class Coverage(val installed: List<String>, val missing: List<String>)

/** Blocking (PackageManager): the engine's own selection for the theme's rules. */
private fun engineCoverage(context: Context, theme: ThemeItem): Coverage {
    val key = "engine:${theme.id.key}@${theme.updated}"
    coverage[key]?.let { return it }
    val rules = AmoledRules.effective(context, theme.id.enginePackage, Substratum.getPreferences())
    return Coverage(AmoledEngine.coveredPackages(context, rules), emptyList()).also { coverage[key] = it }
}

/**
 * Blocking (the theme's assets, PackageManager): the apps of its assets/overlays folders. A
 * sub-target (com.android.systemui.navbars and the like) counts as its app; folders that are not
 * packages (common, Resources) are skipped.
 */
private fun classicCoverage(context: Context, theme: ThemeItem): Coverage {
    val key = "classic:${theme.pkg}@${theme.updated}"
    coverage[key]?.let { return it }
    val folders = runCatching { context.createPackageContext(theme.pkg, 0).assets.list("overlays")?.toList() }
        .getOrNull().orEmpty()
    val pm = context.packageManager
    fun installed(pkg: String) = runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess
    val on = LinkedHashSet<String>()
    val off = LinkedHashSet<String>()
    for (folder in folders.sorted()) {
        if (folder != "android" && !folder.contains('.')) continue
        if (installed(folder)) {
            on += folder
            continue
        }
        // The longest installed prefix: the app a sub-target belongs to
        val parts = folder.split('.')
        val owner = (parts.size - 1 downTo 2).map { parts.take(it).joinToString(".") }.firstOrNull(::installed)
        if (owner != null) on += owner else off += folder
    }
    return Coverage(on.toList(), off.toList()).also { coverage[key] = it }
}

/**
 * A theme's page, one for every kind: the hero, a before/after preview (Reborn engine themes),
 * what it changes on this phone, the Studio's sections, and about. Apply and Customize sit in a
 * floating toolbar that hides while scrolling down. Applying while another engine theme is on
 * asks first ([ReplaceSheet]); the toolbar then follows the job.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeDetailScreen(id: ThemeId, onBack: () -> Unit, onOpenStudio: (StudioSection?) -> Unit) {
    val context = LocalContext.current
    val resume = rememberResumeCount()
    var refresh by remember { mutableIntStateOf(0) }
    val themes by rememberRoot(id, resume, refresh, initial = RebornCache.themes) { loadThemesCached(context) }
    // The engine's state, read again when a job ends (Apply, Remove), so "In use" follows it
    val idle = JobsFlow.job.collectAsStateWithLifecycle().value == null
    LaunchedEffect(idle) { StudioState.bind(context) }
    val theme = themes?.find(id)
    when {
        theme != null -> ThemePage(theme, themes, onBack, onOpenStudio, onChanged = { refresh++ })
        else -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(title = {}, navigationIcon = { BackIcon(onBack) }, colors = rebornTopBarColors())
                if (themes == null) {
                    PageLoading(height = 320.dp)
                } else {
                    // Uninstalled meanwhile, or a link to a theme that is not here
                    SectionCard(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.reborn_theme_gone), style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.reborn_theme_gone_text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ThemePage(
    theme: ThemeItem,
    themes: ThemesSnapshot?,
    onBack: () -> Unit,
    onOpenStudio: (StudioSection?) -> Unit,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = rememberActionSnackbar()
    val summary by StudioState.summary.collectAsStateWithLifecycle()
    val look by StudioState.look.collectAsStateWithLifecycle()
    val dirty by StudioState.lookDirty.collectAsStateWithLifecycle()
    val job by JobsFlow.job.collectAsStateWithLifecycle()
    val engine = theme.kind.engine
    val enginePkg = theme.id.enginePackage
    val engineOn = summary?.enabled == true
    val engineInUse = engine && engineOn && StudioState.sameTheme(summary?.rulesTheme, enginePkg)
    val inUse = engineInUse || (theme.kind.classic && theme.inUse)
    val pending = engineInUse && StudioState.pending(summary, look, dirty, enginePkg)
    val running = job?.takeIf { it.kind != AmoledJobs.Kind.SET_ENABLED }
    // Another engine theme is on: Apply replaces it (only one at a time)
    val replacing = if (engine && engineOn && !engineInUse) {
        val current = ThemeId.ofPackage(summary?.rulesTheme)
        themes?.find(current)?.name ?: summary?.rulesTheme ?: stringResource(R.string.reborn_builtin_amoled_name)
    } else null

    var replaceAsk by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    var confirmTurnOff by rememberSaveable { mutableStateOf(false) }
    var partsVersion by remember { mutableIntStateOf(0) }
    val resumes = rememberResumeCount()
    val installer = rememberThemeInstaller(onChanged = onChanged, onOpen = { })

    // Job results and confirmations in the shell's snackbar (as the Studio shows them)
    val host = LocalSnackbar.current
    LaunchedEffect(host) {
        StudioState.events.collect { event ->
            if (event.kind == AmoledJobs.Kind.SET_ENABLED) return@collect
            // One with Undo (applied with the theme's own look) stays: this one waits behind it
            val showing = host.currentSnackbarData
            if (showing?.visuals?.actionLabel == null) showing?.dismiss()
            launch { host.showSnackbar(event.message, duration = SnackbarDuration.Short) }
        }
    }

    val ownLookText = stringResource(R.string.reborn_theme_applying_own_look, theme.name)
    val undoText = stringResource(R.string.reborn_studio_undo)
    fun apply(keepLook: Boolean) {
        val snapshot = if (!keepLook) StudioState.clearLook() else null
        StudioState.chooseTheme(enginePkg)
        StudioState.applyAll()
        if (snapshot != null) snackbar(ownLookText, undoText) { StudioState.restoreLook(snapshot) }
    }
    fun askApply(keepLook: Boolean) {
        if (replacing != null) replaceAsk = keepLook else apply(keepLook)
    }

    val looks by rememberThemeLooks(theme)
    val qs by StudioState.qs.collectAsStateWithLifecycle()

    val list = rememberLazyListState()
    val toolbarScroll = FloatingToolbarDefaults.exitAlwaysScrollBehavior(FloatingToolbarExitDirection.Bottom)
    var heroHeight by remember { mutableIntStateOf(1) }
    // The name moves into the bar once the hero is 70 % scrolled away
    val heroGone by remember {
        derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > heroHeight * 0.7f }
    }
    val motion = MaterialTheme.motionScheme
    val scheme = MaterialTheme.colorScheme

    Box(
        Modifier
            .fillMaxSize()
            .background(scheme.background)
            .nestedScroll(toolbarScroll),
    ) {
        if (engine || theme.kind.classic) {
            DetailToolbar(
                theme = theme,
                engineInUse = engineInUse,
                pending = pending,
                running = running,
                onApply = { askApply(keepLook = true) },
                onApplyOwnLook = { askApply(keepLook = false) },
                onReapply = { apply(keepLook = true) },
                onRemove = { confirmRemove = true },
                onCustomize = { onOpenStudio(null) },
                onProgress = { onOpenStudio(null) },
                onChooseOverlays = {
                    if (!ThemeActions.chooseOverlays(context, theme.pkg)) {
                        snackbar(context.getString(R.string.reborn_theme_failed), null, null)
                    }
                },
                onOpenClassic = { RebornActions.openClassic(context) },
                scrollBehavior = toolbarScroll,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = -FloatingToolbarDefaults.ScreenOffset)
                    .navigationBarsPadding()
                    .zIndex(1f),
            )
        }
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            // Room for the floating toolbar over the end of the page (above the navigation bar)
            contentPadding = PaddingValues(
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 112.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "hero") {
                DetailHero(theme, inUse, looks, Modifier.onSizeChanged { heroHeight = it.height.coerceAtLeast(1) })
            }
            // Engine themes always; classic ones when their overlays name enough of the preview's colours
            if (engine || looks?.previewable == true) {
                item(key = "preview_header") { SectionLabel(stringResource(R.string.reborn_theme_preview), Modifier.padding(horizontal = 20.dp)) }
                item(key = "preview") {
                    ThemeShowcase(
                        mode = ShowcaseMode.Looks(looks, qs = if (engine) qs else null, name = theme.name),
                        modifier = Modifier.padding(horizontal = 16.dp),
                        active = replaceAsk == null && !confirmRemove,
                    )
                }
            }
            item(key = "changes_header") { SectionLabel(stringResource(R.string.reborn_theme_changes), Modifier.padding(horizontal = 20.dp)) }
            item(key = "changes") {
                if (engine) EngineChanges(theme, { onOpenStudio(StudioSection.Apps) }, Modifier.padding(horizontal = 16.dp))
                else ClassicChanges(theme, Modifier.padding(horizontal = 16.dp))
            }
            if (engine) {
                item(key = "options_header") { SectionLabel(stringResource(R.string.reborn_theme_options), Modifier.padding(horizontal = 20.dp)) }
                item(key = "options") { EngineOptions(onOpenStudio, Modifier.padding(horizontal = 16.dp)) }
            }
            if (theme.kind.classic) {
                // Classic themes: dark mode only, and the parts on this phone with their switches
                item(key = "classic_options_header") {
                    SectionLabel(stringResource(R.string.reborn_theme_classic_options), Modifier.padding(horizontal = 20.dp))
                }
                item(key = "classic_options") { ClassicOptions(theme, Modifier.padding(horizontal = 16.dp)) }
                item(key = "classic_parts") {
                    ClassicPartsCard(
                        theme = theme,
                        refresh = partsVersion * 1000 + resumes,
                        onTurnOff = { confirmTurnOff = true },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            if (theme.kind == ThemeKind.HYBRID) {
                item(key = "classic") {
                    ClassicOverlaysCard(
                        onChoose = {
                            if (!ThemeActions.chooseOverlays(context, theme.pkg)) {
                                snackbar(context.getString(R.string.reborn_theme_failed), null, null)
                            }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            item(key = "about_header") { SectionLabel(stringResource(R.string.reborn_theme_about), Modifier.padding(horizontal = 20.dp)) }
            item(key = "about") {
                AboutSection(
                    theme = theme,
                    starterInstalled = themes?.starterInstalled == true,
                    installer = installer,
                    onShortcut = {
                        scope.launch {
                            val done = ThemeActions.pinShortcut(context, theme, themes?.themes.orEmpty())
                            snackbar(
                                context.getString(if (done) R.string.reborn_theme_shortcut_done else R.string.reborn_failed),
                                null, null,
                            )
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        // Light status bar icons over the dark hero in light mode, the app's own once it is gone
        StatusIconsOverHero(over = !heroGone)
        // Over the hero: back and more on tonal plates; a plain bar with the name once scrolled
        val barColor by animateColorAsState(
            if (heroGone) scheme.background else Color.Transparent,
            motion.defaultEffectsSpec(),
            label = "detailBar",
        )
        val titleAlpha by animateFloatAsState(if (heroGone) 1f else 0f, motion.defaultEffectsSpec(), label = "detailTitle")
        TopAppBar(
            title = {
                Text(
                    theme.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alpha(titleAlpha),
                )
            },
            navigationIcon = {
                FilledTonalIconButton(onClick = onBack, shapes = IconButtonDefaults.shapes(), modifier = Modifier.padding(start = 4.dp)) {
                    Icon(painterResource(R.drawable.ic_reborn_back), stringResource(R.string.reborn_back))
                }
            },
            actions = { if (!theme.builtIn) DetailOverflow(theme, onShortcutDone = snackbar) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = barColor, scrolledContainerColor = scheme.background),
        )
    }

    replaceAsk?.let { keepByDefault ->
        ReplaceSheet(
            current = replacing ?: "",
            next = theme.name,
            keepLookDefault = keepByDefault && StudioState.lookChosen(),
            onReplace = { keep ->
                replaceAsk = null
                apply(keep)
            },
            onDismiss = { replaceAsk = null },
        )
    }
    if (confirmTurnOff) {
        TurnOffDialog(
            themeName = theme.name,
            onConfirm = {
                confirmTurnOff = false
                ClassicThemeActions.turnOffWithUndo(scope, context, theme, snackbar) {
                    partsVersion++
                    onChanged()
                }
            },
            onDismiss = { confirmTurnOff = false },
        )
    }
    if (confirmRemove) {
        RebornConfirmDialog(
            title = stringResource(R.string.system_amoled_remove_title),
            text = stringResource(R.string.reborn_theme_remove_text, theme.name),
            confirmLabel = stringResource(R.string.system_amoled_remove),
            icon = R.drawable.ic_reborn_delete,
            destructive = true,
            onConfirm = StudioState::remove,
            onDismiss = { confirmRemove = false },
        )
    }
}

/**
 * Light status bar icons while [over] the page's dark hero (in light mode the app's bars have dark
 * icons, which vanish on it); the app's own again once the hero scrolls away or the page goes.
 */
@Composable
private fun StatusIconsOverHero(over: Boolean) {
    val view = LocalView.current
    if (LocalRebornColors.current.dark || view.isInEditMode) return
    val window = remember(view) { view.context.findActivity()?.window } ?: return
    DisposableEffect(window, over) {
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !over
        onDispose { controller.isAppearanceLightStatusBars = true }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** The 4:3 hero: the theme's art under a scrim, its avatar, name, kind chips and "In use". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailHero(theme: ThemeItem, inUse: Boolean, looks: ThemeLooks?, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .themeShared(SharedKeys.art(theme.id))
            .fillMaxWidth()
            .aspectRatio(4f / 3f),
    ) {
        ThemeHero(theme, Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to scheme.scrim.copy(alpha = 0.45f),
                        0.22f to Color.Transparent,
                        0.45f to Color.Transparent,
                        1f to scheme.scrim.copy(alpha = 0.86f),
                    ),
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KindAvatar(theme, Modifier.themeShared(SharedKeys.avatar(theme.id)), size = 56.dp)
                Spacer(Modifier.weight(1f))
                // The saved look's palette (classic: as soon as the theme gives one colour of it)
                val shown = looks?.shown
                if (looks != null && shown != null && looks.coverage > 0) {
                    PalettePill(shown.roles, Modifier.themeShared(SharedKeys.palette(theme.id)))
                    if (inUse) Spacer(Modifier.width(8.dp))
                }
                if (inUse) StatusChip(stringResource(R.string.reborn_theme_in_use), ChipState.OK)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                theme.name,
                style = MaterialTheme.typography.displaySmallEmphasized,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.themeShared(SharedKeys.name(theme.id)),
            )
            Text(
                byline(theme),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.84f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) { ThemeChips(theme) }
        }
    }
}

/** The ⋮ of an installed theme, on a tonal plate: app info and shortcut, then uninstall. */
@Composable
private fun DetailOverflow(theme: ThemeItem, onShortcutDone: (String, String?, (() -> Unit)?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    Box(Modifier.padding(end = 4.dp)) {
        FilledTonalIconButton(onClick = { open = true }, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(R.drawable.ic_reborn_more), stringResource(R.string.reborn_more))
        }
        RebornMenu(expanded = open, onDismiss = { open = false }) {
            listOf(
                MenuGroup(
                    listOf(
                        MenuAction(stringResource(R.string.reborn_theme_app_info), R.drawable.ic_reborn_info) {
                            ThemeActions.appInfo(context, theme.pkg)
                        },
                        MenuAction(stringResource(R.string.reborn_theme_shortcut), R.drawable.ic_reborn_shortcut) {
                            scope.launch {
                                val done = ThemeActions.pinShortcut(context, theme, RebornCache.themes?.themes.orEmpty())
                                onShortcutDone(
                                    context.getString(if (done) R.string.reborn_theme_shortcut_done else R.string.reborn_failed),
                                    null, null,
                                )
                            }
                        },
                    ),
                ),
                MenuGroup(
                    if (theme.removable) listOf(
                        MenuAction(stringResource(R.string.reborn_theme_uninstall), R.drawable.ic_reborn_delete, danger = true) {
                            ThemeActions.uninstall(context, theme.pkg)
                        },
                    ) else emptyList(),
                ),
            )
        }
    }
}

private const val SHOWN_ICONS = 6

/** Engine themes: how many apps the rules cover here, six of their icons; a tap opens the app list. */
@Composable
private fun EngineChanges(theme: ThemeItem, onOpenApps: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val covered by rememberRoot(theme.id.key, theme.updated) { engineCoverage(context, theme) }
    val apps = covered?.installed
    SectionCard(modifier = modifier, onClick = onOpenApps) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (apps == null) stringResource(R.string.reborn_theme_counting)
                    else pluralStringResource(R.plurals.reborn_theme_changes_apps, apps.size, apps.size),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
                Text(
                    stringResource(R.string.reborn_theme_changes_engine),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(painterResource(R.drawable.ic_reborn_chevron), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!apps.isNullOrEmpty()) {
            Spacer(Modifier.height(14.dp))
            IconRow(apps)
        }
    }
}

/** Classic themes: the apps its overlays target, here and not installed. */
@Composable
private fun ClassicChanges(theme: ThemeItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val covered by rememberRoot(theme.pkg, theme.updated) { classicCoverage(context, theme) }
    val c = covered
    SectionCard(modifier = modifier) {
        if (c == null) {
            Text(stringResource(R.string.reborn_theme_counting), style = MaterialTheme.typography.titleMediumEmphasized)
            return@SectionCard
        }
        Text(
            pluralStringResource(R.plurals.reborn_theme_changes_apps, c.installed.size, c.installed.size),
            style = MaterialTheme.typography.titleMediumEmphasized,
        )
        Text(
            stringResource(R.string.reborn_theme_changes_classic),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (c.installed.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            IconRow(c.installed)
        }
        if (c.missing.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(
                pluralStringResource(R.plurals.reborn_theme_not_installed, c.missing.size, c.missing.size),
                style = MaterialTheme.typography.labelLargeEmphasized,
            )
            Text(
                c.missing.take(SHOWN_ICONS).joinToString(", ") + if (c.missing.size > SHOWN_ICONS) "…" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun IconRow(apps: List<String>) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        apps.take(SHOWN_ICONS).forEach { pkg -> AppIcon(pkg, size = 36.dp, shape = RoundedCornerShape(10.dp)) }
        if (apps.size > SHOWN_ICONS) {
            Box(
                Modifier
                    .heightIn(min = 36.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.reborn_theme_changes_more, apps.size - SHOWN_ICONS),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

private class OptionRow(@DrawableRes val icon: Int, val title: Int, val text: Int, val section: StudioSection)

private val optionRows = listOf(
    OptionRow(R.drawable.ic_reborn_palette, R.string.reborn_theme_option_look, R.string.reborn_theme_option_look_text, StudioSection.Look),
    OptionRow(R.drawable.ic_reborn_tune, R.string.reborn_theme_option_qs, R.string.reborn_theme_option_qs_text, StudioSection.QuickSettings),
    OptionRow(R.drawable.ic_reborn_apps, R.string.reborn_theme_option_apps, R.string.reborn_theme_option_apps_text, StudioSection.Apps),
)

/** Engine themes: Look, Quick Settings and Apps, each opening the Studio there. */
@Composable
private fun EngineOptions(onOpenStudio: (StudioSection?) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    SegmentedGroup(optionRows.size, modifier) { i, shapes ->
        val row = optionRows[i]
        SegmentedListItem(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onOpenStudio(row.section)
            },
            shapes = shapes,
            colors = rebornSegmentColors(),
            leadingContent = { Icon(painterResource(row.icon), contentDescription = null) },
            trailingContent = { Icon(painterResource(R.drawable.ic_reborn_chevron), contentDescription = null) },
            supportingContent = { Text(stringResource(row.text)) },
        ) {
            Text(stringResource(row.title), style = MaterialTheme.typography.bodyLargeEmphasized)
        }
    }
}

/** A hybrid theme's classic half: its overlays are chosen in Classic's theme screen. */
@Composable
private fun ClassicOverlaysCard(onChoose: () -> Unit, modifier: Modifier = Modifier) {
    SectionCard(modifier = modifier) {
        Text(stringResource(R.string.reborn_theme_chip_overlays), style = MaterialTheme.typography.titleMediumEmphasized)
        Text(
            stringResource(R.string.reborn_theme_hybrid_classic_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        FilledTonalButton(onClick = onChoose, shapes = ButtonDefaults.shapes(), modifier = Modifier.align(Alignment.End)) {
            Icon(painterResource(R.drawable.ic_reborn_layers), null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.reborn_theme_choose_overlays))
        }
    }
}

private class AboutRow(
    @DrawableRes val icon: Int,
    val title: String,
    val value: String?,
    val busy: Boolean = false,
    val onClick: (() -> Unit)? = null,
)

/** Author, version, package and update; app info, home screen shortcut; uninstall in its own group. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AboutSection(
    theme: ThemeItem,
    starterInstalled: Boolean,
    installer: ThemeInstaller,
    onShortcut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val rows = buildList {
        if (theme.builtIn) {
            add(AboutRow(R.drawable.ic_reborn_check, stringResource(R.string.reborn_theme_builtin_row), stringResource(R.string.reborn_theme_builtin_row_text)))
            if (starterInstalled) {
                add(
                    AboutRow(
                        R.drawable.ic_reborn_info,
                        stringResource(R.string.reborn_theme_starter_installed),
                        RebornActions.AMOLED_THEME,
                    ) { ThemeActions.appInfo(context, RebornActions.AMOLED_THEME) },
                )
            } else {
                add(
                    AboutRow(
                        R.drawable.ic_reborn_download,
                        stringResource(R.string.reborn_theme_starter_install),
                        stringResource(R.string.reborn_theme_starter_install_text),
                        busy = installer.installing == Installing.Starter,
                        onClick = installer::installStarter,
                    ),
                )
            }
        } else {
            if (theme.author.isNotBlank()) add(AboutRow(R.drawable.ic_reborn_info, stringResource(R.string.reborn_theme_author), theme.author))
            add(AboutRow(R.drawable.ic_reborn_layers, stringResource(R.string.reborn_theme_version), theme.version ?: "–"))
            add(AboutRow(R.drawable.ic_reborn_apps, stringResource(R.string.reborn_theme_package), theme.pkg))
            if (theme.updated > 0) {
                add(
                    AboutRow(
                        R.drawable.ic_reborn_refresh,
                        stringResource(R.string.reborn_theme_updated),
                        DateUtils.formatDateTime(context, theme.updated, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR),
                    ),
                )
            }
            add(AboutRow(R.drawable.ic_reborn_settings, stringResource(R.string.reborn_theme_app_info), stringResource(R.string.reborn_theme_app_info_text)) {
                ThemeActions.appInfo(context, theme.pkg)
            })
            add(AboutRow(R.drawable.ic_reborn_shortcut, stringResource(R.string.reborn_theme_shortcut_add), stringResource(R.string.reborn_theme_shortcut_text), onClick = onShortcut))
        }
    }
    val uninstall = when {
        theme.builtIn && starterInstalled -> RebornActions.AMOLED_THEME
        !theme.builtIn && theme.removable -> theme.pkg
        else -> null
    }
    Column(modifier) {
        SegmentedGroup(rows.size) { i, shapes ->
            val row = rows[i]
            val leading: @Composable () -> Unit = {
                if (row.busy) LoadingIndicator(Modifier.size(24.dp)) else Icon(painterResource(row.icon), contentDescription = null)
            }
            val supporting: (@Composable () -> Unit)? = row.value?.let { v -> { Text(v, maxLines = 2, overflow = TextOverflow.Ellipsis) } }
            val title: @Composable () -> Unit = { Text(row.title, style = MaterialTheme.typography.bodyLargeEmphasized) }
            val click = row.onClick
            if (click != null) {
                SegmentedListItem(
                    onClick = click,
                    shapes = shapes,
                    colors = rebornSegmentColors(),
                    enabled = !row.busy,
                    leadingContent = leading,
                    supportingContent = supporting,
                    content = title,
                )
            } else {
                SegmentedListItem(
                    shapes = shapes,
                    colors = rebornSegmentColors(),
                    leadingContent = leading,
                    supportingContent = supporting,
                    content = title,
                )
            }
        }
        if (uninstall != null) {
            val scheme = MaterialTheme.colorScheme
            Spacer(Modifier.height(12.dp))
            SegmentedGroup(1) { _, shapes ->
                SegmentedListItem(
                    onClick = { ThemeActions.uninstall(context, uninstall) },
                    shapes = shapes,
                    colors = ListItemDefaults.segmentedColors(
                        containerColor = scheme.errorContainer,
                        contentColor = scheme.onErrorContainer,
                        leadingContentColor = scheme.onErrorContainer,
                        supportingContentColor = scheme.onErrorContainer.copy(alpha = 0.8f),
                    ),
                    leadingContent = { Icon(painterResource(R.drawable.ic_reborn_delete), contentDescription = null) },
                    supportingContent = {
                        Text(
                            stringResource(
                                if (theme.builtIn) R.string.reborn_theme_starter_uninstall_text else R.string.reborn_theme_uninstall_text,
                            ),
                        )
                    },
                ) {
                    Text(stringResource(R.string.reborn_theme_uninstall), style = MaterialTheme.typography.bodyLargeEmphasized)
                }
            }
        }
    }
}

/**
 * The page's actions in the vibrant floating toolbar: engine themes [Apply ▾] (Customize), or
 * Customize when in use and nothing is pending; classic themes [Choose overlays ▾]. While an
 * engine job runs it turns into its progress, which opens the Studio.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DetailToolbar(
    theme: ThemeItem,
    engineInUse: Boolean,
    pending: Boolean,
    running: JobUi?,
    onApply: () -> Unit,
    onApplyOwnLook: () -> Unit,
    onReapply: () -> Unit,
    onRemove: () -> Unit,
    onCustomize: () -> Unit,
    onProgress: () -> Unit,
    onChooseOverlays: () -> Unit,
    onOpenClassic: () -> Unit,
    scrollBehavior: androidx.compose.material3.FloatingToolbarScrollBehavior,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val haptics = LocalHapticFeedback.current
    val engine = theme.kind.engine
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
        scrollBehavior = scrollBehavior,
    ) {
        AnimatedContent(
            targetState = engine && running != null,
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.9f)) togetherWith
                    fadeOut(motion.fastEffectsSpec()) using SizeTransform(clip = false)
            },
            label = "detailActions",
        ) { busy ->
            if (busy && running != null) {
                ProgressPill(running, onProgress)
            } else if (engine) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val mainIsCustomize = engineInUse && !pending
                    ApplySplit(
                        label = stringResource(
                            when {
                                mainIsCustomize -> R.string.reborn_theme_customize
                                engineInUse -> R.string.reborn_studio_reapply
                                else -> R.string.reborn_studio_apply
                            },
                        ),
                        icon = when {
                            mainIsCustomize -> R.drawable.ic_reborn_tune
                            engineInUse -> R.drawable.ic_reborn_refresh
                            else -> R.drawable.ic_reborn_play
                        },
                        onMain = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            when {
                                mainIsCustomize -> onCustomize()
                                engineInUse -> onReapply()
                                else -> onApply()
                            }
                        },
                        menu = {
                            listOf(
                                MenuGroup(
                                    vibrant = true,
                                    entries = listOf(
                                        if (engineInUse) {
                                            MenuAction(stringResource(R.string.reborn_studio_reapply), R.drawable.ic_reborn_refresh, onClick = onReapply)
                                        } else {
                                            MenuAction(stringResource(R.string.reborn_studio_apply), R.drawable.ic_reborn_play, onClick = onApply)
                                        },
                                    ),
                                ),
                                MenuGroup(
                                    listOf(
                                        MenuAction(
                                            stringResource(R.string.reborn_theme_apply_own_look),
                                            R.drawable.ic_reborn_palette,
                                            supporting = stringResource(R.string.reborn_theme_apply_own_look_text),
                                            onClick = onApplyOwnLook,
                                        ),
                                    ),
                                ),
                                MenuGroup(
                                    listOf(
                                        MenuAction(
                                            stringResource(R.string.reborn_studio_remove),
                                            R.drawable.ic_reborn_delete,
                                            enabled = engineInUse,
                                            danger = true,
                                            onClick = onRemove,
                                        ),
                                    ),
                                ),
                            )
                        },
                    )
                    if (!mainIsCustomize) {
                        FilledTonalButton(
                            onClick = onCustomize,
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.heightIn(min = ButtonDefaults.MediumContainerHeight),
                            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                        ) {
                            Text(stringResource(R.string.reborn_theme_customize), style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
                        }
                    }
                }
            } else {
                ApplySplit(
                    label = stringResource(R.string.reborn_theme_choose_overlays),
                    icon = R.drawable.ic_reborn_layers,
                    onMain = onChooseOverlays,
                    menu = {
                        listOf(
                            MenuGroup(
                                listOf(
                                    MenuAction(
                                        stringResource(R.string.reborn_theme_open_classic),
                                        R.drawable.ic_reborn_open,
                                        supporting = stringResource(R.string.reborn_classic_supporting),
                                        onClick = onOpenClassic,
                                    ),
                                ),
                            ),
                        )
                    },
                )
            }
        }
    }
}

/** A split button: the main action, and ▾ with the menu anchored to it. Medium (56 dp) halves. */
@Composable
private fun ApplySplit(
    label: String,
    @DrawableRes icon: Int,
    onMain: () -> Unit,
    menu: @Composable () -> List<MenuGroup>,
) {
    val haptics = LocalHapticFeedback.current
    var open by remember { mutableStateOf(false) }
    val height = ButtonDefaults.MediumContainerHeight
    val more = stringResource(R.string.reborn_theme_more_apply)
    SplitButtonLayout(
        leadingButton = {
            SplitButtonDefaults.LeadingButton(
                onClick = onMain,
                modifier = Modifier.heightIn(min = height),
                shapes = SplitButtonDefaults.leadingButtonShapesFor(height),
                contentPadding = SplitButtonDefaults.leadingButtonContentPaddingFor(height),
            ) {
                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(SplitButtonDefaults.leadingButtonIconSizeFor(height)))
                Spacer(Modifier.width(8.dp))
                Text(label, style = ButtonDefaults.textStyleFor(height), maxLines = 1)
            }
        },
        trailingButton = {
            Box {
                SplitButtonDefaults.TrailingButton(
                    checked = open,
                    onCheckedChange = {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        open = it
                    },
                    modifier = Modifier
                        .heightIn(min = height)
                        .semantics { contentDescription = more },
                    shapes = SplitButtonDefaults.trailingButtonShapesFor(height),
                    contentPadding = SplitButtonDefaults.trailingButtonContentPaddingFor(height),
                ) {
                    val rotation by animateFloatAsState(
                        if (open) 180f else 0f,
                        MaterialTheme.motionScheme.fastSpatialSpec(),
                        label = "applyArrow",
                    )
                    Icon(
                        painterResource(R.drawable.ic_reborn_expand),
                        contentDescription = null,
                        modifier = Modifier
                            .size(SplitButtonDefaults.trailingButtonIconSizeFor(height))
                            .graphicsLayer { rotationZ = rotation },
                    )
                }
                RebornMenu(expanded = open, onDismiss = { open = false }, groups = menu)
            }
        },
    )
}

/** The running job in the toolbar: the loading indicator, "Applying · 34 / 229" and a wavy bar. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProgressPill(job: JobUi, onClick: () -> Unit) {
    val progress by animateFloatAsState(job.fraction, MaterialTheme.motionScheme.slowEffectsSpec(), label = "pillProgress")
    Column(
        Modifier
            .widthIn(min = 220.dp)
            .heightIn(min = ButtonDefaults.MediumContainerHeight)
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LoadingIndicator(Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                when {
                    job.kind == AmoledJobs.Kind.REMOVE -> stringResource(R.string.reborn_theme_removing)
                    job.started -> stringResource(R.string.reborn_theme_applying_count, job.done, job.total)
                    else -> stringResource(R.string.reborn_studio_starting)
                },
                style = MaterialTheme.typography.labelLargeEmphasized.copy(fontFeatureSettings = "tnum"),
                maxLines = 1,
            )
        }
        if (job.started) {
            Spacer(Modifier.height(6.dp))
            LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * "Replace A with B?": one engine theme at a time (classic overlays stay). With "Keep my look"
 * off, the chosen level and accent are forgotten first, so B's own apply.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReplaceSheet(
    current: String,
    next: String,
    keepLookDefault: Boolean,
    onReplace: (keepLook: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val haptics = LocalHapticFeedback.current
    var keep by rememberSaveable { mutableStateOf(keepLookDefault) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
        ) {
            Text(
                stringResource(R.string.reborn_theme_replace_title, current, next),
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.reborn_theme_replace_text),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            SegmentedGroup(1) { _, shapes ->
                SegmentedListItem(
                    checked = keep,
                    onCheckedChange = {
                        haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                        keep = it
                    },
                    shapes = shapes,
                    colors = rebornSegmentColors(),
                    supportingContent = { Text(stringResource(R.string.reborn_theme_keep_look_text)) },
                    trailingContent = { RebornSwitch(checked = keep, onCheckedChange = null) },
                ) {
                    Text(stringResource(R.string.reborn_theme_keep_look), style = MaterialTheme.typography.bodyLargeEmphasized)
                }
            }
            Spacer(Modifier.height(22.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.reborn_cancel))
                }
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        onReplace(keep)
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.reborn_theme_replace))
                }
            }
        }
    }
}
