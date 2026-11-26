/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.themes

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.rememberSearchBarState
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import projekt.substratum.R
import projekt.substratum.reborn.RebornActions
import projekt.substratum.reborn.RebornCache
import projekt.substratum.reborn.amoled.rememberActionSnackbar
import projekt.substratum.reborn.components.Entrance
import projekt.substratum.reborn.components.SectionCard
import projekt.substratum.reborn.components.ShapedIcon
import projekt.substratum.reborn.components.design.FabMenuItem
import projekt.substratum.reborn.components.design.LocalNavBarFab
import projekt.substratum.reborn.components.design.LocalNavBarSpace
import projekt.substratum.reborn.components.design.MenuAction
import projekt.substratum.reborn.components.design.MenuGroup
import projekt.substratum.reborn.components.design.NavBarFab
import projekt.substratum.reborn.components.design.PageLoading
import projekt.substratum.reborn.components.design.RebornFabMenu
import projekt.substratum.reborn.components.design.RebornMenu
import projekt.substratum.reborn.components.design.RebornPullToRefresh
import projekt.substratum.reborn.components.design.pageTitle
import projekt.substratum.reborn.components.design.rebornSegmentColors
import projekt.substratum.reborn.components.design.rememberRefreshableRoot
import projekt.substratum.reborn.components.design.segmentedItems
import projekt.substratum.reborn.components.rememberResumeCount
import projekt.substratum.reborn.nav.OnReselect
import projekt.substratum.reborn.nav.ThemeId

/** Which themes the catalogue shows. */
private enum class ThemeFilter(val label: Int) {
    All(R.string.reborn_themes_filter_all),
    Reborn(R.string.reborn_themes_filter_reborn),
    Classic(R.string.reborn_themes_filter_classic),
    BuiltIn(R.string.reborn_themes_builtin);

    fun matches(theme: ThemeItem): Boolean = when (this) {
        All -> true
        Reborn -> theme.kind == ThemeKind.ENGINE || theme.kind == ThemeKind.HYBRID
        Classic -> theme.kind.classic
        BuiltIn -> theme.builtIn
    }
}

/** The header's height in the grid (88 dp, less the grid's 8 dp gap below it) */
private val HeaderHeight = 80.dp

/**
 * The Themes tab, the heart of Reborn: one grid of theme cards (two columns on a phone, eight
 * above the floating bar on a 412 × 915 dp screen), the built-in themes first, then every
 * installed theme (Reborn engine and classic). It scrolls under a compact 88 dp header: the title
 * with "N installed · M applied", a tonal search button (the search opens over the list) and the
 * primary Add button (install from a file, build your own, Classic), then a connected filter row
 * (All, Reborn, Classic, Built in). A tap opens the theme's page ([onOpenTheme]); a long press, an
 * installed theme's sheet. "Add a theme" also sits beside the floating bar.
 *
 * @param onOpenCreate Selects the Create tab ("Build your own")
 * @param reselect The tab's reselect count: scrolls to the top
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ThemesScreen(onOpenTheme: (ThemeId) -> Unit, onOpenCreate: () -> Unit, reselect: Int = 0) {
    val context = LocalContext.current
    val resume = rememberResumeCount()
    var refresh by remember { mutableIntStateOf(0) }
    val themes = rememberRefreshableRoot(resume, refresh, initial = RebornCache.themes) {
        loadThemes(context).also { RebornCache.themes = it }
    }
    val snapshot = themes.value
    val snackbar = rememberActionSnackbar()
    val scope = rememberCoroutineScope()
    var sheetFor by rememberSaveable { mutableStateOf<String?>(null) }
    var filter by rememberSaveable { mutableStateOf(ThemeFilter.All) }
    val installer = rememberThemeInstaller(
        onChanged = { refresh++ },
        onOpen = { pkg -> onOpenTheme(ThemeId.ofPackage(pkg)) },
    )
    val list = rememberLazyGridState()
    // Beside the floating bar the Add FAB leaves only while a theme installs, so the bar centres
    // alone meanwhile; in a screen of its own it sits at the bottom end
    val fabBesideBar = LocalNavBarFab.current != null
    val addTheme: @Composable () -> Unit = {
        RebornFabMenu(
            items = listOf(
                FabMenuItem(stringResource(R.string.reborn_install_file), R.drawable.ic_reborn_file, installer::pickFile),
                FabMenuItem(stringResource(R.string.reborn_themes_build_own), R.drawable.ic_reborn_create, onOpenCreate),
            ),
            icon = R.drawable.ic_reborn_add,
            contentDescription = stringResource(R.string.reborn_add_theme),
            visible = installer.installing == null,
        )
    }
    if (fabBesideBar && installer.installing == null) NavBarFab(addTheme)
    OnReselect(reselect) { list.animateScrollToItem(0) }

    // The entrance plays once; coming back to the tab shows the cards at once
    var entered by rememberSaveable { mutableStateOf(false) }
    val animate = remember { !entered }
    LaunchedEffect(Unit) { entered = true }

    // Search: the header's button opens it over the list, results as the user types
    val searchState = rememberSearchBarState()
    val query = rememberTextFieldState()
    val searchField: @Composable () -> Unit = {
        SearchBarDefaults.InputField(
            textFieldState = query,
            searchBarState = searchState,
            onSearch = { },
            placeholder = { Text(stringResource(R.string.reborn_themes_search)) },
            leadingIcon = {
                if (searchState.currentValue == SearchBarValue.Expanded) {
                    IconButton(onClick = { scope.launch { searchState.animateToCollapsed() } }) {
                        Icon(painterResource(R.drawable.ic_reborn_back), stringResource(R.string.reborn_back))
                    }
                } else {
                    Icon(painterResource(R.drawable.ic_reborn_search), contentDescription = null)
                }
            },
            trailingIcon = if (query.text.isNotEmpty()) {
                {
                    IconButton(onClick = { query.clearText() }) {
                        Icon(painterResource(R.drawable.ic_reborn_close), stringResource(R.string.reborn_studio_clear_search))
                    }
                }
            } else null,
        )
    }

    val data = snapshot
    // One grid: the built-ins first, then the installed themes, no section headers between them
    val tiles = remember(data, filter) {
        (data?.builtIns.orEmpty() + data?.installed.orEmpty()).filter(filter::matches)
    }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + LocalNavBarSpace.current + 24.dp
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        RebornPullToRefresh(
            refreshing = themes.refreshing,
            onRefresh = themes.refresh,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 156.dp),
                state = list,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottom),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                fullLine(key = "header", contentType = "header") {
                    ThemesHeader(
                        installed = data?.themes?.size,
                        applied = data?.themes?.count { it.inUse } ?: 0,
                        installing = installer.installing != null,
                        onSearch = { scope.launch { searchState.animateToExpanded() } },
                        addMenu = {
                            listOf(
                                MenuGroup(
                                    listOf(
                                        MenuAction(
                                            stringResource(R.string.reborn_install_file),
                                            R.drawable.ic_reborn_file,
                                            enabled = installer.installing == null,
                                            onClick = installer::pickFile,
                                        ),
                                        MenuAction(
                                            stringResource(R.string.reborn_themes_build_own),
                                            R.drawable.ic_reborn_create,
                                            onClick = onOpenCreate,
                                        ),
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
                fullLine(key = "filters", contentType = "filters") {
                    Filters(filter) { filter = it }
                }
                if (data == null) {
                    fullLine(key = "loading") { PageLoading(height = 320.dp) }
                } else {
                    items(tiles, key = { it.id.key }, contentType = { "theme" }) { theme ->
                        val index = tiles.indexOf(theme)
                        Entrance(index.coerceAtMost(7), animate) {
                            ThemeTile(
                                theme = theme,
                                onOpen = { onOpenTheme(theme.id) },
                                onLongPress = if (theme.builtIn) null else ({ sheetFor = theme.pkg }),
                            )
                        }
                    }
                    when {
                        // Only the built-ins: what to do instead (under them)
                        data.installed.isEmpty() && filter == ThemeFilter.All -> fullLine(key = "empty") {
                            NoThemes(
                                title = stringResource(R.string.reborn_themes_empty),
                                text = stringResource(R.string.reborn_themes_empty_text),
                            )
                        }
                        tiles.isEmpty() -> fullLine(key = "no_match") {
                            NoThemes(
                                title = stringResource(R.string.reborn_themes_no_match),
                                text = stringResource(R.string.reborn_themes_filter_none, stringResource(filter.label)),
                            )
                        }
                    }
                }
            }
        }
        if (!fabBesideBar) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding(),
            ) { addTheme() }
        }
    }

    // Results over everything, as the user types
    ExpandedFullScreenSearchBar(state = searchState, inputField = searchField) {
        val q = query.text.toString().trim().lowercase()
        val results = remember(snapshot, q) {
            snapshot?.themes.orEmpty().filter { q.isEmpty() || it.search.contains(q) }
        }
        SearchResults(
            results = results,
            query = q,
            onOpen = { theme ->
                scope.launch { searchState.animateToCollapsed() }
                onOpenTheme(theme.id)
            },
        )
    }

    val sheetTheme = sheetFor?.let { pkg -> snapshot?.installed?.firstOrNull { it.pkg == pkg } }
    if (sheetTheme != null) {
        ThemeSheet(
            theme = sheetTheme,
            onDismiss = { sheetFor = null },
            onOpen = {
                sheetFor = null
                onOpenTheme(sheetTheme.id)
            },
            onOpenClassic = {
                sheetFor = null
                if (!ThemeActions.chooseOverlays(context, sheetTheme.pkg)) {
                    snackbar(context.getString(R.string.reborn_theme_failed), null, null)
                }
            },
            onAppInfo = {
                sheetFor = null
                ThemeActions.appInfo(context, sheetTheme.pkg)
            },
            onShortcut = {
                sheetFor = null
                scope.launch {
                    val done = ThemeActions.pinShortcut(context, sheetTheme, snapshot?.themes.orEmpty())
                    snackbar(
                        context.getString(if (done) R.string.reborn_theme_shortcut_done else R.string.reborn_failed),
                        null, null,
                    )
                }
            },
            onUninstall = {
                sheetFor = null
                ThemeActions.uninstall(context, sheetTheme.pkg)
            },
        )
    }
}

/** A grid item across the whole row (the header, the filters, an empty state). */
private fun LazyGridScope.fullLine(key: Any, contentType: Any? = null, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }, contentType = contentType) { content() }
}

/**
 * The compact header (88 dp with the grid's gap): "Themes" in the rounded display face over
 * "N installed · M applied" (the applied count in primary), then two 48 dp buttons: a tonal
 * search and the primary Add, which opens a menu (and shows a loading indicator while a theme
 * installs).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ThemesHeader(
    installed: Int?,
    applied: Int,
    installing: Boolean,
    onSearch: () -> Unit,
    addMenu: @Composable () -> List<MenuGroup>,
) {
    val scheme = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(HeaderHeight)
            .padding(start = 4.dp, top = 8.dp),
    ) {
        Column(
            Modifier
                .weight(1f)
                .padding(top = 4.dp),
        ) {
            Text(
                stringResource(R.string.reborn_tab_themes),
                style = MaterialTheme.typography.pageTitle,
                maxLines = 1,
                modifier = Modifier.semantics { heading() },
            )
            val installedText = installed?.let { stringResource(R.string.reborn_themes_installed_n, it) }
            val appliedText = stringResource(R.string.reborn_themes_applied_n, applied)
            val reading = stringResource(R.string.reborn_reading)
            Text(
                if (installedText == null) {
                    buildAnnotatedString { append(reading) }
                } else {
                    buildAnnotatedString {
                        append(installedText)
                        append(" · ")
                        withStyle(SpanStyle(color = scheme.primary, fontWeight = FontWeight.Bold)) { append(appliedText) }
                    }
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HeaderButton(
                icon = R.drawable.ic_reborn_search,
                description = stringResource(R.string.reborn_themes_search),
                container = scheme.surfaceContainerHigh,
                content = scheme.onSurface,
                onClick = onSearch,
            )
            Box {
                val adding = stringResource(R.string.reborn_installing)
                IconButton(
                    onClick = { menu = true },
                    shapes = IconButtonDefaults.shapes(),
                    colors = IconButtonDefaults.iconButtonColors(containerColor = scheme.primary, contentColor = scheme.onPrimary),
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { if (installing) stateDescription = adding },
                ) {
                    if (installing) {
                        LoadingIndicator(Modifier.size(28.dp), color = scheme.onPrimary)
                    } else {
                        Icon(painterResource(R.drawable.ic_reborn_add), stringResource(R.string.reborn_add_theme))
                    }
                }
                RebornMenu(expanded = menu, onDismiss = { menu = false }, groups = addMenu)
            }
        }
    }
}

@Composable
private fun HeaderButton(
    @DrawableRes icon: Int,
    description: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        colors = IconButtonDefaults.iconButtonColors(containerColor = container, contentColor = content),
        modifier = Modifier.size(48.dp),
    ) {
        Icon(painterResource(icon), description)
    }
}

/**
 * The connected filter row (40 dp, 2 dp apart): All, Reborn, Classic, Built in. The chosen one
 * goes full-round in secondaryContainer, widens and grows a check; the others sit on
 * surfaceContainerHigh.
 */
@Composable
private fun Filters(selected: ThemeFilter, onSelect: (ThemeFilter) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val entries = ThemeFilter.entries
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        entries.forEachIndexed { index, f ->
            val checked = f == selected
            val weight by animateFloatAsState(if (checked) 1.4f else 1f, motion.fastSpatialSpec(), label = "filterWeight")
            ToggleButton(
                checked = checked,
                onCheckedChange = {
                    if (!checked) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(f)
                    }
                },
                modifier = Modifier
                    .weight(weight)
                    .height(40.dp)
                    .semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = ToggleButtonDefaults.colors(
                    containerColor = scheme.surfaceContainerHigh,
                    contentColor = scheme.onSurface,
                    checkedContainerColor = scheme.secondaryContainer,
                    checkedContentColor = scheme.onSecondaryContainer,
                ),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                AnimatedVisibility(
                    visible = checked,
                    enter = expandHorizontally(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
                    exit = shrinkHorizontally(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
                ) {
                    Row {
                        Icon(painterResource(R.drawable.ic_reborn_check), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                }
                Text(
                    stringResource(f.label),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The full-screen search's results: one segmented row per theme. */
@Composable
private fun SearchResults(results: List<ThemeItem>, query: String, onOpen: (ThemeItem) -> Unit) {
    if (results.isEmpty()) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
            NoThemes(
                title = stringResource(R.string.reborn_themes_no_match),
                text = stringResource(R.string.reborn_themes_no_match_text, query),
            )
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        segmentedItems(results, key = { it.id.key }) { theme, shapes, modifier ->
            SegmentedListItem(
                onClick = { onOpen(theme) },
                shapes = shapes,
                colors = rebornSegmentColors(),
                modifier = modifier,
                leadingContent = { KindAvatar(theme, size = 40.dp) },
                supportingContent = { Text(byline(theme), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            ) {
                Text(theme.name, style = MaterialTheme.typography.bodyLargeEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** No installed themes (or none matching): what to do instead. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NoThemes(title: String, text: String) {
    SectionCard {
        ShapedIcon(
            icon = R.drawable.ic_reborn_themes,
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
            size = 52.dp,
            shape = MaterialShapes.Cookie7Sided.toShape(),
        )
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
