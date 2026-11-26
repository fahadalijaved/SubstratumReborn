/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.amoled

import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import projekt.substratum.R
import projekt.substratum.common.platform.AmoledState
import projekt.substratum.reborn.RebornActions
import projekt.substratum.reborn.components.AppIcon
import projekt.substratum.reborn.components.SectionCard
import projekt.substratum.reborn.components.ShapedIcon
import projekt.substratum.reborn.components.design.ActionIcon
import projekt.substratum.reborn.components.design.BackIcon
import projekt.substratum.reborn.components.design.MenuAction
import projekt.substratum.reborn.components.design.MenuBusy
import projekt.substratum.reborn.components.design.MenuGroup
import projekt.substratum.reborn.components.design.PageLoading
import projekt.substratum.reborn.components.design.RebornFlexibleScaffold
import projekt.substratum.reborn.components.design.RebornMenu
import projekt.substratum.reborn.components.design.RebornSwitch
import projekt.substratum.reborn.components.design.rebornSegmentColors
import projekt.substratum.reborn.components.design.segmentedItems
import projekt.substratum.reborn.components.onIo
import projekt.substratum.reborn.theme.LocalRebornColors

/** What the app list shows. */
enum class AppFilter(@StringRes val label: Int, @DrawableRes val icon: Int) {
    All(R.string.reborn_studio_filter_all, R.drawable.ic_reborn_apps),
    Themed(R.string.reborn_studio_filter_themed, R.drawable.ic_reborn_amoled),
    Off(R.string.reborn_studio_filter_off, R.drawable.ic_reborn_off),
    Native(R.string.reborn_studio_filter_native, R.drawable.ic_reborn_ok),
    Module(R.string.reborn_studio_filter_module, R.drawable.ic_reborn_module),
    NeedsScope(R.string.reborn_studio_filter_scope, R.drawable.ic_reborn_warning),
    Unsupported(R.string.reborn_studio_filter_unsupported, R.drawable.ic_reborn_error),
    Restart(R.string.reborn_studio_filter_restart, R.drawable.ic_reborn_restart),
    Changed(R.string.reborn_studio_filter_changed, R.drawable.ic_reborn_refresh);

    fun matches(row: StudioState.AppRow, scope: Set<String>?): Boolean = when (this) {
        All -> true
        Themed -> row.themed
        Off -> !row.enabled
        Native -> row.route == AmoledState.Route.OVERLAY
        Module -> row.route == AmoledState.Route.MODULE
        NeedsScope -> needsScope(row, scope)
        Unsupported -> row.route == AmoledState.Route.UNSUPPORTED
        Restart -> row.restartNeeded
        Changed -> row.changed
    }
}

/** Themed through the LSPosed module, which is not scoped to it: its colours do not show. */
fun needsScope(row: StudioState.AppRow, scope: Set<String>?): Boolean =
    row.route == AmoledState.Route.MODULE && scope != null && row.pkg !in scope

private enum class RowAction { Inspect, Open, Reapply, Restart, Scope }

/** What a row needs to draw, so unchanged rows skip recomposition. */
@Immutable
private class RowCallbacks(
    val onToggle: (StudioState.AppRow, Boolean) -> Unit,
    val onAction: (StudioState.AppRow, RowAction) -> Unit,
)

/**
 * Every app the engine covers: search (name and package), filters, and a row per app with its
 * route, colours, restart state and switch. A tap opens the [Inspector].
 *
 * @param onPick Pick mode (Create › Inspect an app): rows have no switch or menu, and a tap hands
 *   the app to the caller, which opens its colours
 * @param title The bar's title (the Studio's "Apps" when null)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun AppList(
    initialFilter: AppFilter,
    onBack: () -> Unit,
    onPick: ((StudioState.AppRow) -> Unit)? = null,
    title: String? = null,
) {
    val context = LocalContext.current
    val apps by StudioState.apps.collectAsStateWithLifecycle()
    val scope by StudioState.lsposedScope.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(initialFilter) }
    var inspecting by rememberSaveable { mutableStateOf<String?>(null) }
    val haptics = LocalHapticFeedback.current
    val focus = LocalFocusManager.current
    val snackbar = rememberActionSnackbar()
    val coroutines = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val counts = remember(apps, scope) {
        AppFilter.entries.associateWith { f -> apps?.count { f.matches(it, scope) } ?: 0 }
    }
    val visible = remember(apps, scope, query, filter) {
        val q = query.trim().lowercase()
        apps.orEmpty().filter { filter.matches(it, scope) && (q.isEmpty() || it.search.contains(q)) }
    }
    // A new search or filter starts from the top (the controls stay pinned)
    LaunchedEffect(query, filter) {
        if (listState.firstVisibleItemIndex > 0) listState.scrollToItem(0)
    }

    val undo = stringResource(R.string.reborn_studio_undo)
    val callbacks = remember(context, snackbar, coroutines) {
        RowCallbacks(
            onToggle = { row, on ->
                StudioState.setEnabled(row, on)
                snackbar(
                    context.getString(if (on) R.string.reborn_studio_app_on else R.string.reborn_studio_app_off, row.label),
                    undo,
                    { StudioState.setEnabled(row.copy(enabled = on), !on) },
                )
            },
            onAction = { row, action ->
                when (action) {
                    RowAction.Inspect -> inspecting = row.pkg
                    RowAction.Reapply -> StudioState.reapply(row)
                    RowAction.Open -> coroutines.launch {
                        val intent = onIo {
                            runCatching { context.packageManager.getLaunchIntentForPackage(row.pkg) }.getOrNull()
                        }
                        val started = intent != null && runCatching {
                            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }.isSuccess
                        if (!started) snackbar(context.getString(R.string.reborn_studio_no_launch, row.label), null, null)
                    }
                    RowAction.Restart -> coroutines.launch {
                        val done = StudioState.restart(row)
                        snackbar(
                            context.getString(if (done) R.string.reborn_studio_restarted else R.string.reborn_studio_restart_failed, row.label),
                            null, null,
                        )
                    }
                    RowAction.Scope -> coroutines.launch {
                        snackbar(context.getString(R.string.reborn_studio_scope_hint, row.label), null, null)
                        if (!RebornActions.openLsposed(context)) {
                            snackbar(context.getString(R.string.reborn_no_lsposed), null, null)
                        }
                    }
                }
            },
        )
    }

    val background = MaterialTheme.colorScheme.background
    RebornFlexibleScaffold(
        title = title ?: stringResource(R.string.reborn_studio_apps),
        subtitle = apps?.let { list -> stringResource(R.string.reborn_studio_themed, list.count { it.themed }, list.size) },
        large = false,
        navigationIcon = { BackIcon(onBack) },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            stickyHeader(key = "controls", contentType = "controls") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(background)
                        .padding(bottom = 8.dp),
                ) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        placeholder = { Text(stringResource(R.string.reborn_studio_search)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_reborn_search), contentDescription = null) },
                        trailingIcon = if (query.isNotEmpty()) {
                            {
                                IconButton(onClick = { query = "" }) {
                                    Icon(painterResource(R.drawable.ic_reborn_close), stringResource(R.string.reborn_studio_clear_search))
                                }
                            }
                        } else null,
                        singleLine = true,
                        shape = CircleShape,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(AppFilter.entries, key = { it.name }) { f ->
                            val selected = filter == f
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    filter = f
                                },
                                label = {
                                    Text(stringResource(R.string.reborn_studio_filter_count, stringResource(f.label), counts[f] ?: 0))
                                },
                                leadingIcon = if (selected) {
                                    { Icon(painterResource(R.drawable.ic_reborn_check), null, Modifier.size(18.dp)) }
                                } else null,
                            )
                        }
                    }
                }
            }
            val list = apps
            when {
                list == null -> item(key = "loading") { PageLoading(height = 240.dp) }
                list.isEmpty() -> item(key = "empty") {
                    EmptyCard(R.string.reborn_studio_apps_empty, R.string.reborn_studio_apps_empty_text)
                }
                visible.isEmpty() -> item(key = "none") {
                    EmptyCard(R.string.reborn_studio_no_apps, R.string.reborn_studio_no_apps_text)
                }
                // One segmented list: rows 2 dp apart, the first and last with the large corners
                else -> segmentedItems(visible, key = { it.pkg }) { row, shapes, segment ->
                    if (onPick != null) {
                        PickRow(row, needsScope(row, scope), shapes, segment.padding(horizontal = 16.dp)) { onPick(row) }
                    } else {
                        AppRowItem(
                            row = row,
                            needsScope = needsScope(row, scope),
                            callbacks = callbacks,
                            shapes = shapes,
                            modifier = segment.padding(horizontal = 16.dp),
                        )
                    }
                }
            }
        }
    }

    inspecting?.let { pkg ->
        val row = apps?.firstOrNull { it.pkg == pkg }
        if (row != null) Inspector(row = row, onDismiss = { inspecting = null })
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmptyCard(@StringRes title: Int, @StringRes text: Int) {
    SectionCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        ShapedIcon(
            icon = R.drawable.ic_reborn_search,
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
            size = 52.dp,
            shape = MaterialShapes.Cookie7Sided.toShape(),
        )
        Spacer(Modifier.height(14.dp))
        Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A row of the pick mode: the app, its route and colours, and a chevron; a tap picks it. */
@Composable
private fun PickRow(
    row: StudioState.AppRow,
    needsScope: Boolean,
    shapes: ListItemShapes,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalRebornColors.current
    val scheme = MaterialTheme.colorScheme
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        colors = rebornSegmentColors(),
        modifier = modifier,
        leadingContent = { AppIcon(row.pkg, size = 40.dp, shape = MaterialTheme.shapes.medium) },
        supportingContent = {
            Column {
                Text(row.pkg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    RouteChip(row.route)
                    when {
                        needsScope -> MiniChip(stringResource(R.string.reborn_studio_needs_scope), R.drawable.ic_reborn_warning, colors.error)
                        row.colors > 0 -> Text(
                            pluralStringResource(R.plurals.reborn_studio_colours, row.colors, row.colors),
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        },
        trailingContent = { Icon(painterResource(R.drawable.ic_reborn_chevron), contentDescription = null, tint = scheme.onSurfaceVariant) },
    ) {
        Text(row.label, style = MaterialTheme.typography.bodyLargeEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AppRowItem(
    row: StudioState.AppRow,
    needsScope: Boolean,
    callbacks: RowCallbacks,
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
) {
    val colors = LocalRebornColors.current
    val scheme = MaterialTheme.colorScheme
    // The row menu: from its 3-dot button, or a long press on the row
    var menu by remember { mutableStateOf(false) }
    SegmentedListItem(
        onClick = { callbacks.onAction(row, RowAction.Inspect) },
        onLongClick = { menu = true },
        onLongClickLabel = stringResource(R.string.reborn_more),
        shapes = shapes,
        colors = rebornSegmentColors(),
        modifier = modifier,
        leadingContent = { AppIcon(row.pkg, size = 40.dp, shape = MaterialTheme.shapes.medium) },
        supportingContent = {
            Column {
                Text(row.pkg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    RouteChip(row.route)
                    when {
                        needsScope -> MiniChip(stringResource(R.string.reborn_studio_needs_scope), R.drawable.ic_reborn_warning, colors.error)
                        row.restartNeeded -> MiniChip(stringResource(R.string.reborn_studio_restart_needed), R.drawable.ic_reborn_restart, colors.warn)
                        row.colors > 0 -> Text(
                            pluralStringResource(R.plurals.reborn_studio_colours, row.colors, row.colors),
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RebornSwitch(
                    checked = row.enabled,
                    enabled = row.route != AmoledState.Route.UNSUPPORTED,
                    onCheckedChange = { callbacks.onToggle(row, it) },
                )
                Box {
                    ActionIcon(R.drawable.ic_reborn_more, stringResource(R.string.reborn_more), { menu = true })
                    RebornMenu(expanded = menu, onDismiss = { menu = false }) {
                        // Whether a force-stop is safe is read only while the menu is open
                        val safe by produceState<Boolean?>(null, row.pkg) { value = StudioState.safeToRestart(row.pkg) }
                        listOf(
                            MenuGroup(
                                listOf(
                                    MenuAction(stringResource(R.string.reborn_studio_inspect), R.drawable.ic_reborn_palette) {
                                        callbacks.onAction(row, RowAction.Inspect)
                                    },
                                    MenuAction(stringResource(R.string.reborn_studio_open_app), R.drawable.ic_reborn_open) {
                                        callbacks.onAction(row, RowAction.Open)
                                    },
                                ),
                            ),
                            MenuGroup(
                                listOf(
                                    MenuAction(
                                        stringResource(R.string.reborn_studio_reapply_app),
                                        R.drawable.ic_reborn_refresh,
                                        enabled = row.enabled && row.route != AmoledState.Route.UNSUPPORTED,
                                    ) { callbacks.onAction(row, RowAction.Reapply) },
                                    when (safe) {
                                        null -> MenuBusy(stringResource(R.string.reborn_studio_restart_checking), R.drawable.ic_reborn_restart)
                                        true -> MenuAction(stringResource(R.string.reborn_studio_restart_app), R.drawable.ic_reborn_restart) {
                                            callbacks.onAction(row, RowAction.Restart)
                                        }
                                        false -> MenuAction(
                                            stringResource(R.string.reborn_studio_restart_unsafe),
                                            R.drawable.ic_reborn_restart,
                                            enabled = false,
                                        ) {}
                                    },
                                ),
                            ),
                            MenuGroup(
                                if (row.route == AmoledState.Route.MODULE) {
                                    listOf(
                                        MenuAction(stringResource(R.string.reborn_studio_add_scope), R.drawable.ic_reborn_module) {
                                            callbacks.onAction(row, RowAction.Scope)
                                        },
                                    )
                                } else emptyList(),
                            ),
                        )
                    }
                }
            }
        },
    ) {
        Text(row.label, style = MaterialTheme.typography.bodyLargeEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
