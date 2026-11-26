/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.applied

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import projekt.substratum.R
import projekt.substratum.common.platform.AmoledState
import projekt.substratum.reborn.RebornActions
import projekt.substratum.reborn.RebornCache
import projekt.substratum.reborn.amoled.Inspector
import projekt.substratum.reborn.amoled.StudioState
import projekt.substratum.reborn.amoled.rememberActionSnackbar
import projekt.substratum.reborn.components.AppIcon
import projekt.substratum.reborn.components.ChipState
import projekt.substratum.reborn.components.FixBanner
import projekt.substratum.reborn.components.HeroShape
import projekt.substratum.reborn.components.MorphingShape
import projekt.substratum.reborn.components.SectionCard
import projekt.substratum.reborn.components.ShapedIcon
import projekt.substratum.reborn.components.StatusChip
import projekt.substratum.reborn.components.TagChip
import projekt.substratum.reborn.components.design.HeroCard
import projekt.substratum.reborn.components.design.PageLoading
import projekt.substratum.reborn.components.design.RebornExtendedFab
import projekt.substratum.reborn.components.design.RebornFlexibleScaffold
import projekt.substratum.reborn.components.design.RebornPullToRefresh
import projekt.substratum.reborn.components.design.SegmentedGroup
import projekt.substratum.reborn.components.design.rebornSegmentColors
import projekt.substratum.reborn.components.design.rememberRefreshableRoot
import projekt.substratum.reborn.components.onIo
import projekt.substratum.reborn.components.rememberResumeCount
import projekt.substratum.reborn.home.amoledName
import projekt.substratum.reborn.nav.OnReselect
import projekt.substratum.reborn.nav.ThemeId
import projekt.substratum.reborn.theme.LocalRebornColors

/** The entries of one target app, as Applied shows them. */
@Immutable
data class AppliedGroup(val targetPkg: String, val label: String, val entries: List<AppliedEntry>)

private val entryOrder = compareBy<AppliedEntry>({ it.source.ordinal }, { it.qs?.ordinal ?: 0 }, { it.overlay ?: "" })

/**
 * Everything applied, grouped by the app it changes: compiled overlays, AMOLED and Quick
 * Settings options. Filters by source and state, search, a swipe turns an entry off (with Undo),
 * a tap on an AMOLED row opens its colours in the inspector, and Undo all takes everything back.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun AppliedScreen(
    onOpenTheme: (ThemeId) -> Unit,
    onOpenStudio: (ThemeId) -> Unit,
    onOpenThemes: () -> Unit,
    onOpenCreate: () -> Unit,
    reselect: Int = 0,
) {
    val context = LocalContext.current
    val resume = rememberResumeCount()
    val version by AppliedRepository.version.collectAsStateWithLifecycle()
    val applied = rememberRefreshableRoot(resume, version, initial = RebornCache.applied) {
        AppliedRepository.read(context).also { RebornCache.applied = it }
    }
    val snapshot = applied.value
    val restartPending by AppliedRepository.restartPending.collectAsStateWithLifecycle()
    var source by rememberSaveable { mutableStateOf<AppliedSource?>(null) }
    var state by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var inspecting by rememberSaveable { mutableStateOf<String?>(null) }
    var restarting by remember { mutableStateOf(false) }
    // Switches made here, shown at once until a read has caught up with them
    val overrides = remember { mutableStateMapOf<String, Boolean>() }
    val snackbar = rememberActionSnackbar()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val focus = LocalFocusManager.current
    val listState = rememberLazyListState()
    // Undo all is a FAB, extended while the list is at its top
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    LaunchedEffect(snapshot) {
        val byKey = snapshot?.entries?.associateBy { it.key } ?: return@LaunchedEffect
        overrides.keys.toList().forEach { k ->
            val entry = byKey[k]
            if (entry == null || entry.enabled == overrides[k]) overrides.remove(k)
        }
    }

    val data = snapshot
    val switched = overrides.toMap()
    val all = remember(data, switched) {
        data?.entries.orEmpty().map { e -> switched[e.key]?.takeIf { it != e.enabled }?.let { e.copy(enabled = it) } ?: e }
    }
    val bySource = remember(all, source) { all.filter { source == null || it.source == source } }
    val groups = remember(bySource, state, query, data) {
        val q = query.trim().lowercase()
        bySource
            .filter { state == null || it.enabled == state }
            .filter { q.isEmpty() || it.search.contains(q) || (data?.label(it.targetPkg)?.lowercase()?.contains(q) == true) }
            .groupBy { it.targetPkg }
            .map { (pkg, list) -> AppliedGroup(pkg, data?.label(pkg) ?: pkg, list.sortedWith(entryOrder)) }
            .sortedWith(compareBy({ AppliedRepository.groupOrder(it.targetPkg, it.label).first }, { it.label.lowercase() }))
    }
    // A new filter or search starts from the top (the controls stay pinned)
    LaunchedEffect(source, state, query) {
        if (listState.firstVisibleItemIndex > 1) listState.scrollToItem(0)
    }

    val undoLabel = stringResource(R.string.reborn_studio_undo)
    fun turnOn(entry: AppliedEntry) {
        overrides[entry.key] = true
        scope.launch {
            val ok = onIo { AppliedRepository.setEnabled(context, entry, true) }
            if (entry.qs == QsOption.TILE) StudioState.reloadLook()
            if (!ok) {
                overrides.remove(entry.key)
                snackbar(context.getString(R.string.reborn_failed), null, null)
            }
        }
    }
    fun turnOff(entry: AppliedEntry) {
        haptics.performHapticFeedback(HapticFeedbackType.ToggleOff)
        overrides[entry.key] = false
        scope.launch {
            val ok = onIo { AppliedRepository.setEnabled(context, entry, false) }
            if (entry.qs == QsOption.TILE) StudioState.reloadLook()
            if (!ok) {
                overrides.remove(entry.key)
                snackbar(context.getString(R.string.reborn_failed), null, null)
                return@launch
            }
            val label = data?.label(entry.targetPkg) ?: entry.targetPkg
            val message = when (entry.source) {
                AppliedSource.THEME_OVERLAY -> context.getString(R.string.reborn_applied_off_overlay, label)
                AppliedSource.AMOLED -> context.getString(R.string.reborn_studio_app_off, label)
                AppliedSource.QS -> if (entry.qs == QsOption.TILE) context.getString(R.string.reborn_applied_off_tile)
                else context.getString(R.string.reborn_applied_off_qs, entry.title)
            }
            snackbar(message, undoLabel) { turnOn(entry) }
        }
    }
    fun restartSystemUi() {
        if (restarting) return
        scope.launch {
            restarting = true
            val ok = try {
                RebornActions.restartSystemUi()
                true
            } catch (e: Exception) {
                false
            } finally {
                restarting = false
            }
            snackbar(context.getString(if (ok) R.string.reborn_restarting_systemui else R.string.reborn_failed), null, null)
        }
    }

    val background = MaterialTheme.colorScheme.background
    val subtitle = if (data != null) {
        val on = all.count { it.enabled }
        val apps = all.map { it.targetPkg }.distinct().size
        stringResource(R.string.reborn_applied_subtitle, on, all.size - on,
            pluralStringResource(R.plurals.reborn_applied_apps, apps, apps))
    } else null
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    OnReselect(reselect) {
        scrollBehavior.state.heightOffset = 0f
        listState.animateScrollToItem(0)
    }
    // A row's source chip opens where it came from: its theme's page, or the engine theme's Studio
    val engineTheme = ThemeId.ofPackage(data?.amoled?.theme)
    val engineName = amoledName(data?.amoled)
    val onSource: (AppliedEntry) -> Unit = { entry ->
        when (entry.source) {
            AppliedSource.THEME_OVERLAY -> entry.themePkg?.let { onOpenTheme(ThemeId.ofPackage(it)) }
            AppliedSource.AMOLED -> onOpenStudio(engineTheme)
            AppliedSource.QS -> onOpenStudio(engineTheme)
        }
    }
    RebornFlexibleScaffold(
        title = stringResource(R.string.reborn_tab_applied),
        subtitle = subtitle,
        scrollBehavior = scrollBehavior,
        connectScroll = false,
        floatingActionButton = {
            if (data != null && data.entries.isNotEmpty()) {
                RebornExtendedFab(
                    label = stringResource(R.string.reborn_applied_undo_all),
                    icon = R.drawable.ic_reborn_undo,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        AppliedRepository.askUndo(data.undoPlan())
                    },
                    expanded = fabExpanded,
                )
            }
        },
    ) { padding ->
        RebornPullToRefresh(
            refreshing = applied.refreshing,
            onRefresh = applied.refresh,
            modifier = Modifier.padding(top = padding.calculateTopPadding()),
            scrollConnection = scrollBehavior.nestedScrollConnection,
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    // Room for the Undo all FAB over the last group
                    bottom = padding.calculateBottomPadding() + 28.dp + 88.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (data != null && data.entries.isNotEmpty()) {
                    stickyHeader(key = "controls", contentType = "controls") {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(background)
                                .padding(bottom = 6.dp),
                        ) {
                            TextField(
                                value = query,
                                onValueChange = { query = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                placeholder = { Text(stringResource(R.string.reborn_applied_search)) },
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
                            Filters(
                                all = all,
                                bySource = bySource,
                                source = source,
                                state = state,
                                onSource = {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    source = it
                                },
                                onState = {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    state = if (state == it) null else it
                                },
                            )
                        }
                    }
                }
                if (restartPending) {
                    item(key = "restart") {
                        FixBanner(
                            title = stringResource(R.string.reborn_applied_restart_title),
                            text = stringResource(R.string.reborn_applied_restart_text),
                            fixLabel = stringResource(R.string.reborn_applied_restart),
                            onFix = ::restartSystemUi,
                            warning = true,
                            busy = restarting,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .animateItem(),
                        )
                    }
                }
                when {
                    data == null -> item(key = "loading") { PageLoading(height = 320.dp) }
                    data.entries.isEmpty() -> item(key = "empty") {
                        EmptyApplied(onOpenThemes, onOpenCreate, Modifier.padding(horizontal = 16.dp))
                    }
                    groups.isEmpty() -> item(key = "no_match") {
                        NoMatch(Modifier.padding(horizontal = 16.dp))
                    }
                    else -> {
                        item(key = "hint") {
                            Text(
                                stringResource(R.string.reborn_applied_swipe_hint),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp),
                            )
                        }
                        items(groups, key = { it.targetPkg }, contentType = { "group" }) { group ->
                            GroupCard(
                                group = group,
                                engineName = engineName,
                                onSource = onSource,
                                onTurnOff = ::turnOff,
                                onInspect = { inspecting = it.targetPkg },
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }

    inspecting?.let { pkg ->
        val entry = all.firstOrNull { it.source == AppliedSource.AMOLED && it.targetPkg == pkg }
        if (entry != null) {
            AppliedInspector(entry, data?.label(pkg) ?: pkg, onDismiss = { inspecting = null })
        } else if (data != null) {
            // The app has no AMOLED entry any more (removed meanwhile)
            LaunchedEffect(pkg) { inspecting = null }
        }
    }
}

@Composable
private fun Filters(
    all: List<AppliedEntry>,
    bySource: List<AppliedEntry>,
    source: AppliedSource?,
    state: Boolean?,
    onSource: (AppliedSource?) -> Unit,
    onState: (Boolean) -> Unit,
) {
    val sources = listOf<Pair<AppliedSource?, Int>>(
        null to R.string.reborn_applied_filter_all,
        AppliedSource.THEME_OVERLAY to R.string.reborn_applied_filter_overlays,
        AppliedSource.AMOLED to R.string.reborn_applied_filter_amoled,
        AppliedSource.QS to R.string.reborn_applied_filter_qs,
    )
    val on = bySource.count { it.enabled }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(sources, key = { it.first?.name ?: "all" }) { (s, label) ->
            val selected = source == s
            val count = if (s == null) all.size else all.count { it.source == s }
            FilterChip(
                selected = selected,
                onClick = { onSource(s) },
                label = { Text(stringResource(R.string.reborn_studio_filter_count, stringResource(label), count)) },
                leadingIcon = if (selected) {
                    { Icon(painterResource(R.drawable.ic_reborn_check), null, Modifier.size(18.dp)) }
                } else null,
            )
        }
        item(key = "divider") {
            VerticalDivider(Modifier.height(24.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
        items(listOf(true to R.string.reborn_applied_filter_on, false to R.string.reborn_applied_filter_off),
            key = { "state_" + it.first }) { (value, label) ->
            val selected = state == value
            FilterChip(
                selected = selected,
                onClick = { onState(value) },
                label = {
                    Text(stringResource(R.string.reborn_studio_filter_count, stringResource(label),
                        if (value) on else bySource.size - on))
                },
                leadingIcon = {
                    Icon(
                        painterResource(if (value) R.drawable.ic_reborn_ok else R.drawable.ic_reborn_off),
                        null,
                        Modifier.size(18.dp),
                    )
                },
            )
        }
    }
}

/** An app's group: its icon, name and counts, then its entries as one segmented list. */
@Composable
private fun GroupCard(
    group: AppliedGroup,
    engineName: String,
    onSource: (AppliedEntry) -> Unit,
    onTurnOff: (AppliedEntry) -> Unit,
    onInspect: (AppliedEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val on = group.entries.count { it.enabled }
    Column(modifier) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(group.targetPkg, size = 40.dp, shape = MaterialTheme.shapes.medium)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    group.label,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.reborn_applied_group_counts, on, group.entries.size - on),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(if (on > 0) scheme.primaryContainer else scheme.surfaceContainerHighest)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(
                    on.toString(),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = if (on > 0) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                )
            }
        }
        SegmentedGroup(group.entries.size) { i, shapes ->
            val entry = group.entries[i]
            key(entry.key) { EntryRow(entry, engineName, shapes, onSource, onTurnOff, onInspect) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryRow(
    entry: AppliedEntry,
    engineName: String,
    shapes: ListItemShapes,
    onSource: (AppliedEntry) -> Unit,
    onTurnOff: (AppliedEntry) -> Unit,
    onInspect: (AppliedEntry) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val swipe = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    val turnOff = stringResource(R.string.reborn_applied_turn_off)
    val inspectable = entry.source == AppliedSource.AMOLED
    SwipeToDismissBox(
        state = swipe,
        // The swipe background takes the segment's shape
        modifier = Modifier.clip(shapes.shape),
        backgroundContent = { SwipeBackground(swipe) },
        enableDismissFromStartToEnd = entry.enabled,
        enableDismissFromEndToStart = entry.enabled,
        gesturesEnabled = entry.enabled,
        onDismiss = {
            onTurnOff(entry)
            scope.launch { swipe.reset() }
        },
    ) {
        val rowModifier = Modifier.semantics {
            if (entry.enabled) {
                customActions = listOf(CustomAccessibilityAction(turnOff) {
                    onTurnOff(entry)
                    true
                })
            }
        }
        val supporting: @Composable () -> Unit = {
            Column {
                Text(entry.detail, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StatusChip(
                        stringResource(if (entry.enabled) R.string.reborn_applied_state_on else R.string.reborn_applied_state_off),
                        if (entry.enabled) ChipState.OK else ChipState.Off,
                    )
                    SourceChip(entry, engineName, Modifier.clip(CircleShape).clickable { onSource(entry) })
                }
            }
        }
        val headline: @Composable () -> Unit = {
            Text(entry.title, style = MaterialTheme.typography.bodyLargeEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (inspectable) {
            SegmentedListItem(
                onClick = { onInspect(entry) },
                shapes = shapes,
                colors = rebornSegmentColors(),
                modifier = rowModifier,
                supportingContent = supporting,
                trailingContent = {
                    Icon(
                        painterResource(R.drawable.ic_reborn_chevron),
                        contentDescription = stringResource(R.string.reborn_applied_inspect),
                        tint = scheme.onSurfaceVariant,
                    )
                },
                content = headline,
            )
        } else {
            SegmentedListItem(
                shapes = shapes,
                colors = rebornSegmentColors(),
                modifier = rowModifier,
                supportingContent = supporting,
                content = headline,
            )
        }
    }
}

/** Where an entry comes from: a classic theme, the engine theme (by name) or Quick Settings. */
@Composable
private fun SourceChip(entry: AppliedEntry, engineName: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    when (entry.source) {
        AppliedSource.THEME_OVERLAY -> TagChip(
            entry.themeLabel?.let { stringResource(R.string.reborn_applied_source_theme, it) }
                ?: stringResource(R.string.reborn_applied_source_overlay),
            R.drawable.ic_reborn_layers, scheme.secondaryContainer, scheme.onSecondaryContainer, modifier,
        )
        AppliedSource.AMOLED -> TagChip(
            engineName,
            R.drawable.ic_reborn_amoled, scheme.primaryContainer, scheme.onPrimaryContainer, modifier,
        )
        AppliedSource.QS -> TagChip(
            stringResource(R.string.reborn_applied_source_qs),
            R.drawable.ic_reborn_tune, scheme.tertiaryContainer, scheme.onTertiaryContainer, modifier,
        )
    }
}

@Composable
private fun RowScope.SwipeBackground(state: SwipeToDismissBoxState) {
    val status = LocalRebornColors.current.warn
    val end = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(status.container)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (end) Arrangement.End else Arrangement.Start,
    ) {
        Icon(painterResource(R.drawable.ic_reborn_off), contentDescription = null, tint = status.accent)
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.reborn_applied_turn_off),
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = status.onContainer,
        )
    }
}

/** An AMOLED row's colours in the Studio's inspector, over Applied. */
@Composable
private fun AppliedInspector(entry: AppliedEntry, label: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // The inspector's actions (keep stock, pin) run through the Studio's state
    DisposableEffect(Unit) {
        StudioState.attach(context)
        onDispose { StudioState.detach() }
    }
    val apps by StudioState.apps.collectAsStateWithLifecycle()
    val row = apps?.firstOrNull { it.pkg == entry.targetPkg } ?: StudioState.AppRow(
        pkg = entry.targetPkg,
        label = label,
        route = entry.route ?: AmoledState.Route.NONE,
        enabled = entry.enabled,
        active = entry.enabled,
        restartNeeded = entry.restartNeeded,
        colors = entry.colors,
        error = null,
        level = entry.level,
        changed = false,
    )
    Inspector(row = row, onDismiss = onDismiss)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmptyApplied(onOpenThemes: () -> Unit, onOpenCreate: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    HeroCard(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(190.dp),
            contentAlignment = Alignment.Center,
        ) {
            MorphingShape(HeroShape.Idle, scheme.tertiary, Modifier.size(172.dp))
            Icon(
                painterResource(R.drawable.ic_reborn_check),
                contentDescription = null,
                tint = scheme.onTertiary,
                modifier = Modifier.size(60.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.reborn_applied_empty), style = MaterialTheme.typography.headlineMediumEmphasized)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.reborn_applied_empty_text),
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onOpenThemes, shapes = ButtonDefaults.shapes()) {
                Icon(painterResource(R.drawable.ic_reborn_themes), null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.reborn_explore_themes))
            }
            OutlinedButton(onClick = onOpenCreate, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.reborn_create_a_theme))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NoMatch(modifier: Modifier = Modifier) {
    SectionCard(modifier = modifier) {
        ShapedIcon(
            icon = R.drawable.ic_reborn_search,
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
            size = 52.dp,
            shape = MaterialShapes.Cookie7Sided.toShape(),
        )
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.reborn_applied_no_match), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.reborn_applied_no_match_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
