/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.themes

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import projekt.substratum.R
import projekt.substratum.reborn.RebornActions
import projekt.substratum.reborn.RebornCache
import projekt.substratum.reborn.amoled.rememberActionSnackbar
import projekt.substratum.reborn.components.Entrance
import projekt.substratum.reborn.components.SectionCard
import projekt.substratum.reborn.components.ShapedIcon
import projekt.substratum.reborn.components.design.ActionIcon
import projekt.substratum.reborn.components.design.BusyLabel
import projekt.substratum.reborn.components.design.FabMenuItem
import projekt.substratum.reborn.components.design.MenuAction
import projekt.substratum.reborn.components.design.MenuGroup
import projekt.substratum.reborn.components.design.PageLoading
import projekt.substratum.reborn.components.design.RebornFabMenu
import projekt.substratum.reborn.components.design.RebornFlexibleScaffold
import projekt.substratum.reborn.components.design.RebornOverflow
import projekt.substratum.reborn.components.design.RebornPullToRefresh
import projekt.substratum.reborn.components.design.SectionLabel
import projekt.substratum.reborn.components.design.rebornSegmentColors
import projekt.substratum.reborn.components.design.rememberRefreshableRoot
import projekt.substratum.reborn.components.design.segmentedItems
import projekt.substratum.reborn.components.rememberResumeCount
import projekt.substratum.reborn.nav.OnReselect
import projekt.substratum.reborn.nav.ThemeId

/** Which themes the catalogue shows. */
private enum class ThemeFilter(val label: Int) {
    All(R.string.reborn_themes_filter_all),
    Engine(R.string.reborn_themes_filter_engine),
    Classic(R.string.reborn_themes_filter_classic),
    InUse(R.string.reborn_themes_filter_in_use);

    fun matches(theme: ThemeItem): Boolean = when (this) {
        All -> true
        Engine -> theme.kind.engine
        Classic -> theme.kind.classic
        InUse -> theme.inUse
    }
}

/** More than this many themes: the filter chips show. */
private const val FILTERS_FROM = 3

/**
 * The Themes tab, the heart of Reborn: the built-in themes first, then every installed theme as a
 * full-width card (Reborn engine and classic), and ways to get more. A tap opens the theme's
 * page ([onOpenTheme]); a long press, an installed theme's sheet. Search opens over the list.
 *
 * @param onOpenCreate Selects the Create tab ("Build your own")
 * @param reselect The tab's reselect count: scrolls to the top and expands the bar
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
    val list = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    // The Add FAB shows while the list is at its top, like Google's apps
    val fabVisible by remember { derivedStateOf { list.firstVisibleItemIndex == 0 } }
    OnReselect(reselect) {
        scrollBehavior.state.heightOffset = 0f
        list.animateScrollToItem(0)
    }

    // The entrance plays once; coming back to the tab shows the cards at once
    var entered by rememberSaveable { mutableStateOf(false) }
    val animate = remember { !entered }
    LaunchedEffect(Unit) { entered = true }

    // Search: one state and one query for the bar in the list and the full-screen results
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

    RebornFlexibleScaffold(
        title = stringResource(R.string.reborn_tab_themes),
        subtitle = snapshot?.let { pluralStringResource(R.plurals.reborn_themes_count, it.themes.size, it.themes.size) },
        scrollBehavior = scrollBehavior,
        // The bar's nested scroll is the pull to refresh's (below), so a pull expands the bar first
        connectScroll = false,
        actions = {
            ActionIcon(R.drawable.ic_reborn_search, stringResource(R.string.reborn_themes_search), {
                scope.launch { searchState.animateToExpanded() }
            })
            RebornOverflow {
                listOf(
                    MenuGroup(
                        listOf(
                            MenuAction(
                                stringResource(R.string.reborn_install_file),
                                R.drawable.ic_reborn_file,
                                enabled = installer.installing == null,
                                onClick = installer::pickFile,
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
            }
        },
        floatingActionButton = {
            RebornFabMenu(
                items = listOf(
                    FabMenuItem(stringResource(R.string.reborn_install_file), R.drawable.ic_reborn_file, installer::pickFile),
                    FabMenuItem(stringResource(R.string.reborn_themes_build_own), R.drawable.ic_reborn_create, onOpenCreate),
                ),
                icon = R.drawable.ic_reborn_create,
                contentDescription = stringResource(R.string.reborn_add_theme),
                visible = fabVisible && installer.installing == null,
            )
        },
    ) { padding ->
        val data = snapshot
        val showFilters = (data?.themes?.size ?: 0) > FILTERS_FROM
        val shown = if (showFilters) filter else ThemeFilter.All
        val builtIns = remember(data, shown) { data?.builtIns.orEmpty().filter(shown::matches) }
        val installed = remember(data, shown) { data?.installed.orEmpty().filter(shown::matches) }
        RebornPullToRefresh(
            refreshing = themes.refreshing,
            onRefresh = themes.refresh,
            modifier = Modifier.padding(top = padding.calculateTopPadding()),
            scrollConnection = scrollBehavior.nestedScrollConnection,
        ) {
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 4.dp,
                    // Room for the Add FAB over the last card
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "search", contentType = "search") {
                    SearchBar(
                        state = searchState,
                        inputField = searchField,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    )
                }
                if (showFilters) {
                    item(key = "filters", contentType = "filters") {
                        Filters(filter, data?.themes.orEmpty()) { filter = it }
                    }
                }
                if (data == null) {
                    item(key = "loading") { PageLoading(height = 320.dp) }
                } else {
                    if (builtIns.isNotEmpty()) {
                        item(key = "builtin_header") { SectionLabel(stringResource(R.string.reborn_themes_builtin), Modifier.padding(horizontal = 16.dp)) }
                        items(builtIns, key = { it.id.key }, contentType = { "theme" }) { theme ->
                            Entrance(0, animate) {
                                ThemeItemCard(
                                    theme = theme,
                                    onOpen = { onOpenTheme(theme.id) },
                                    onLongPress = null,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                        }
                    }
                    item(key = "installed_header") { SectionLabel(stringResource(R.string.reborn_themes_installed), Modifier.padding(horizontal = 16.dp)) }
                    when {
                        data.installed.isEmpty() -> item(key = "empty") {
                            NoThemes(
                                title = stringResource(R.string.reborn_themes_empty),
                                text = stringResource(R.string.reborn_themes_empty_text),
                            )
                        }
                        installed.isEmpty() -> item(key = "no_match") {
                            NoThemes(
                                title = stringResource(R.string.reborn_themes_no_match),
                                text = stringResource(R.string.reborn_themes_filter_none, stringResource(shown.label)),
                            )
                        }
                        else -> items(installed, key = { it.id.key }, contentType = { "theme" }) { theme ->
                            val index = data.installed.indexOf(theme) + 1
                            Entrance(index.coerceAtMost(4), animate) {
                                ThemeItemCard(
                                    theme = theme,
                                    onOpen = { onOpenTheme(theme.id) },
                                    onLongPress = { sheetFor = theme.pkg },
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                        }
                    }
                    item(key = "more_header") { SectionLabel(stringResource(R.string.reborn_themes_get_more), Modifier.padding(horizontal = 16.dp)) }
                    item(key = "more_file") {
                        DiscoverCard(
                            icon = R.drawable.ic_reborn_file,
                            shape = MaterialShapes.Cookie6Sided.toShape(),
                            container = MaterialTheme.colorScheme.tertiaryContainer,
                            content = MaterialTheme.colorScheme.onTertiaryContainer,
                            title = stringResource(R.string.reborn_discover_file_title),
                            text = stringResource(R.string.reborn_discover_file_text),
                            action = stringResource(R.string.reborn_discover_file_action),
                            actionIcon = R.drawable.ic_reborn_file,
                            busy = installer.installing == Installing.File,
                            enabled = installer.installing == null,
                            onClick = installer::pickFile,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    item(key = "more_create") {
                        DiscoverCard(
                            icon = R.drawable.ic_reborn_create,
                            shape = MaterialShapes.Flower.toShape(),
                            container = MaterialTheme.colorScheme.primaryContainer,
                            content = MaterialTheme.colorScheme.onPrimaryContainer,
                            title = stringResource(R.string.reborn_themes_build_own),
                            text = stringResource(R.string.reborn_themes_build_own_text),
                            action = stringResource(R.string.reborn_themes_build_own_action),
                            actionIcon = R.drawable.ic_reborn_create,
                            busy = false,
                            enabled = true,
                            onClick = onOpenCreate,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }
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

@Composable
private fun Filters(selected: ThemeFilter, themes: List<ThemeItem>, onSelect: (ThemeFilter) -> Unit) {
    val haptics = LocalHapticFeedback.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(ThemeFilter.entries, key = { it.name }) { f ->
            val isSelected = f == selected
            FilterChip(
                selected = isSelected,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onSelect(f)
                },
                label = {
                    Text(stringResource(R.string.reborn_studio_filter_count, stringResource(f.label), themes.count(f::matches)))
                },
                leadingIcon = if (isSelected) {
                    { Icon(painterResource(R.drawable.ic_reborn_check), null, Modifier.size(18.dp)) }
                } else null,
            )
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
                gutter = false,
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DiscoverCard(
    @DrawableRes icon: Int,
    shape: Shape,
    container: Color,
    content: Color,
    title: String,
    text: String,
    action: String,
    @DrawableRes actionIcon: Int,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val installingLabel = stringResource(R.string.reborn_installing)
    SectionCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShapedIcon(icon, container, content, size = 56.dp, shape = shape, busy = busy)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
                Spacer(Modifier.height(2.dp))
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        val scheme = MaterialTheme.colorScheme
        FilledTonalButton(
            onClick = onClick,
            enabled = enabled,
            shapes = ButtonDefaults.shapes(),
            // Busy keeps the tonal colours: the in-button indicator says it is installing
            colors = if (busy) ButtonDefaults.filledTonalButtonColors(
                disabledContainerColor = scheme.secondaryContainer,
                disabledContentColor = scheme.onSecondaryContainer,
            ) else ButtonDefaults.filledTonalButtonColors(),
            modifier = Modifier
                .align(Alignment.End)
                .semantics { if (busy) stateDescription = installingLabel },
        ) {
            BusyLabel(busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(actionIcon), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(action)
                }
            }
        }
    }
}

/** No installed themes (or none matching): what to do instead. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NoThemes(title: String, text: String, gutter: Boolean = true) {
    SectionCard(modifier = if (gutter) Modifier.padding(horizontal = 16.dp) else Modifier) {
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
