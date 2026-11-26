/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.create

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import projekt.substratum.R
import projekt.substratum.common.platform.AmoledPreview
import projekt.substratum.common.platform.AmoledState
import projekt.substratum.reborn.amoled.AppFilter
import projekt.substratum.reborn.amoled.AppList
import projekt.substratum.reborn.amoled.Inspector
import projekt.substratum.reborn.amoled.StudioState
import projekt.substratum.reborn.amoled.copyName
import projekt.substratum.reborn.components.Entrance
import projekt.substratum.reborn.components.HeroShape
import projekt.substratum.reborn.components.MorphingShape
import projekt.substratum.reborn.components.ShapedIcon
import projekt.substratum.reborn.components.design.HeroCard
import projekt.substratum.reborn.components.design.PageLoading
import projekt.substratum.reborn.components.design.RebornFlexibleScaffold
import projekt.substratum.reborn.components.design.SectionLabel
import projekt.substratum.reborn.components.design.SegmentedGroup
import projekt.substratum.reborn.components.design.overline
import projekt.substratum.reborn.components.design.rebornSegmentColors
import projekt.substratum.reborn.components.design.segmentedItems
import projekt.substratum.reborn.components.rememberRoot
import projekt.substratum.reborn.home.THEMERS_URL
import projekt.substratum.reborn.home.openUrl
import projekt.substratum.reborn.nav.OnReselect
import java.util.Locale

private class Tool(
    @DrawableRes val icon: Int,
    @StringRes val title: Int,
    @StringRes val text: Int,
    val shape: Shape,
    val onClick: () -> Unit,
)

/**
 * The Create tab, for themers building for the Reborn engine: a hero into the Studio, then tools
 * (inspect an app's colours, the resource names the previews read, the theming guide). Later
 * packages add starting from the current look, importing a theme file and a theme check.
 *
 * @param onDesign Opens the Studio of the engine theme in use (else Reborn AMOLED)
 * @param onInspect Opens Create › Inspect an app
 * @param reselect The tab's reselect count: scrolls to the top and expands the bar
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CreateScreen(onDesign: () -> Unit, onInspect: () -> Unit, reselect: Int = 0) {
    val context = LocalContext.current
    val list = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var roles by rememberSaveable { mutableStateOf(false) }
    OnReselect(reselect) {
        scrollBehavior.state.heightOffset = 0f
        list.animateScrollToItem(0)
    }
    var entered by rememberSaveable { mutableStateOf(false) }
    val animate = remember { !entered }
    LaunchedEffect(Unit) { entered = true }

    val scheme = MaterialTheme.colorScheme
    val tools = listOf(
        Tool(R.drawable.ic_reborn_search, R.string.reborn_create_inspect, R.string.reborn_create_inspect_text,
            MaterialShapes.Cookie7Sided.toShape(), onInspect),
        Tool(R.drawable.ic_reborn_palette, R.string.reborn_create_roles, R.string.reborn_create_roles_text,
            MaterialShapes.Clover4Leaf.toShape()) { roles = true },
        Tool(R.drawable.ic_reborn_open, R.string.reborn_create_guide, R.string.reborn_create_guide_text,
            MaterialShapes.Pentagon.toShape()) { openUrl(context, THEMERS_URL) },
    )
    RebornFlexibleScaffold(
        title = stringResource(R.string.reborn_tab_create),
        subtitle = stringResource(R.string.reborn_create_subtitle),
        scrollBehavior = scrollBehavior,
    ) { padding ->
        LazyColumn(
            state = list,
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
                Entrance(0, animate) { CreateHero(onDesign) }
            }
            item(key = "tools_header") {
                Entrance(1, animate) { SectionLabel(stringResource(R.string.reborn_create_tools)) }
            }
            item(key = "tools") {
                Entrance(2, animate) {
                    val haptics = LocalHapticFeedback.current
                    SegmentedGroup(tools.size) { i, shapes ->
                        val tool = tools[i]
                        SegmentedListItem(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                tool.onClick()
                            },
                            shapes = shapes,
                            colors = rebornSegmentColors(),
                            leadingContent = {
                                ShapedIcon(
                                    tool.icon,
                                    when (i % 3) {
                                        0 -> scheme.primaryContainer
                                        1 -> scheme.secondaryContainer
                                        else -> scheme.tertiaryContainer
                                    },
                                    when (i % 3) {
                                        0 -> scheme.onPrimaryContainer
                                        1 -> scheme.onSecondaryContainer
                                        else -> scheme.onTertiaryContainer
                                    },
                                    size = 44.dp,
                                    shape = tool.shape,
                                )
                            },
                            trailingContent = { Icon(painterResource(R.drawable.ic_reborn_chevron), contentDescription = null) },
                            supportingContent = { Text(stringResource(tool.text)) },
                        ) {
                            Text(stringResource(tool.title), style = MaterialTheme.typography.bodyLargeEmphasized)
                        }
                    }
                }
            }
        }
    }
    if (roles) PreviewRolesSheet(onDismiss = { roles = false })
}

/** A slowly turning flower, what Create is for, its three steps, and the way into the Studio. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CreateHero(onDesign: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    HeroCard {
        Box(
            Modifier
                .fillMaxWidth()
                .height(168.dp),
            contentAlignment = Alignment.Center,
        ) {
            MorphingShape(HeroShape.Bloom, scheme.tertiary, Modifier.size(160.dp))
            Icon(
                painterResource(R.drawable.ic_reborn_create_filled),
                contentDescription = null,
                tint = scheme.onTertiary,
                modifier = Modifier.size(52.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.reborn_create_overline),
            style = MaterialTheme.typography.overline,
            color = scheme.tertiary,
        )
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.reborn_create_headline), style = MaterialTheme.typography.displaySmallEmphasized)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(R.string.reborn_create_step_start, R.string.reborn_create_step_test, R.string.reborn_create_step_share)
                .forEachIndexed { i, step -> Step(i + 1, stringResource(step), Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onDesign()
            },
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.heightIn(min = ButtonDefaults.MediumContainerHeight),
            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight, true),
        ) {
            Icon(
                painterResource(R.drawable.ic_reborn_tune),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MediumContainerHeight)),
            )
            Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(ButtonDefaults.MediumContainerHeight)))
            Text(stringResource(R.string.reborn_create_design), style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
        }
    }
}

/** One numbered step: the number on a small cookie, then its words. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Step(number: Int, text: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(28.dp)
                .background(scheme.tertiaryContainer, MaterialShapes.Cookie4Sided.toShape()),
            contentAlignment = Alignment.Center,
        ) {
            Text(number.toString(), style = MaterialTheme.typography.labelLargeEmphasized, color = scheme.onTertiaryContainer)
        }
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** The preview's roles: what paints each (app and resource name) and its stock dark colour. */
private class RoleRow(val role: String, val pkg: String?, val name: String?, val stock: Int?)

/**
 * The resource names Reborn's previews read (AmoledPreview.ROLES), with the app and the stock dark
 * value from the shipped table. A tap copies the name for a theme's rules.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewRolesSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheet = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val rows by rememberRoot(Unit) {
        val stock = AmoledPreview.assetStock(context)
        AmoledPreview.ROLES.map { RoleRow(it, stock.packageOf(it), stock.nameOf(it), stock.stock(it)) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
            Text(
                stringResource(R.string.reborn_create_roles),
                style = MaterialTheme.typography.titleLargeEmphasized,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                stringResource(R.string.reborn_create_roles_sheet_text, AmoledPreview.ROLES.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 12.dp),
            )
            val list = rows
            if (list == null) {
                PageLoading(height = 240.dp)
            } else {
                LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
                    segmentedItems(list, key = { it.role }) { row, shapes, modifier ->
                        SegmentedListItem(
                            onClick = { row.name?.let { copyName(context, it) } },
                            enabled = row.name != null,
                            shapes = shapes,
                            colors = rebornSegmentColors(),
                            modifier = modifier,
                            leadingContent = { Swatch(row.stock) },
                            overlineContent = { Text(row.role) },
                            supportingContent = {
                                Text(
                                    listOfNotNull(row.pkg, row.stock?.let(::hex)).joinToString(" · "),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        ) {
                            Text(
                                row.name ?: stringResource(R.string.reborn_create_roles_code),
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = if (row.name != null) FontFamily.Monospace else null,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Swatch(color: Int?) {
    Box(
        Modifier
            .size(32.dp)
            .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .padding(1.dp)
            .background(color?.let { Color(it) } ?: Color.Transparent, RoundedCornerShape(9.dp)),
    )
}

private fun hex(color: Int): String =
    if (color ushr 24 == 0xff) String.format(Locale.ROOT, "#%06X", color and 0xffffff)
    else String.format(Locale.ROOT, "#%08X", color)

/**
 * Create › Inspect an app: the engine's app list in pick mode; a pick opens that app's colours in
 * the inspector, where a long press copies a resource name. [pkg] opens one at once.
 */
@Composable
fun InspectScreen(pkg: String?, onBack: () -> Unit) {
    val context = LocalContext.current
    // Counted with the Studio's own attaches: the list follows the engine's state
    DisposableEffect(Unit) {
        StudioState.attach(context)
        onDispose { StudioState.detach() }
    }
    var inspecting by rememberSaveable { mutableStateOf(pkg) }
    val apps by StudioState.apps.collectAsStateWithLifecycle()
    AppList(
        initialFilter = AppFilter.All,
        onBack = onBack,
        onPick = { inspecting = it.pkg },
        title = stringResource(R.string.reborn_create_inspect),
    )
    inspecting?.let { p ->
        val row = apps?.firstOrNull { it.pkg == p } ?: StudioState.AppRow(
            pkg = p,
            label = p,
            route = AmoledState.Route.NONE,
            enabled = false,
            active = false,
            restartNeeded = false,
            colors = 0,
            error = null,
            level = null,
            changed = false,
        )
        Inspector(row = row, onDismiss = { inspecting = null })
    }
}
