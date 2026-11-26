/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.components.design

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CheckableDropdownMenuItem
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorPosition
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import projekt.substratum.R
import projekt.substratum.reborn.theme.LocalRebornColors

/** One row of a [RebornMenu]. */
sealed interface MenuEntry {
    val label: String
}

/** A plain action; closes the menu, then runs. [danger] = error colours (put it in its own last group). */
class MenuAction(
    override val label: String,
    @DrawableRes val icon: Int? = null,
    val supporting: String? = null,
    val trailing: String? = null,
    val enabled: Boolean = true,
    val danger: Boolean = false,
    val onClick: () -> Unit,
) : MenuEntry

/** An on/off option (replaces a Switch in trailingIcon); the menu stays open. */
class MenuToggle(
    override val label: String,
    val checked: Boolean,
    @DrawableRes val icon: Int? = null,
    val supporting: String? = null,
    val enabled: Boolean = true,
    val onCheckedChange: (Boolean) -> Unit,
) : MenuEntry

/** One of several exclusive choices (sort order, variant); closes the menu. */
class MenuChoice(
    override val label: String,
    val selected: Boolean,
    val supporting: String? = null,
    val enabled: Boolean = true,
    val onSelect: () -> Unit,
) : MenuEntry

/** A row whose state is still being read: disabled, with a small loading indicator. */
class MenuBusy(
    override val label: String,
    @DrawableRes val icon: Int? = null,
    val supporting: String? = null,
) : MenuEntry

/** A rounded group; groups are separated by MenuDefaults.GroupSpacing. [vibrant] = tertiary container, only for the menu's main action. */
class MenuGroup(
    val entries: List<MenuEntry>,
    val label: String? = null,
    val vibrant: Boolean = false,
)

/**
 * The M3 Expressive menu: DropdownMenuPopup + DropdownMenuGroup, shaped items (12 dp outer,
 * 4 dp inner corners), selected items morph to 12 dp, checkable/selectable rows, a vibrant group.
 * Replaces the classic DropdownMenu (one flat 4 dp sheet, the One UI look).
 *
 * [groups] is built inside the popup, so anything it reads (produceState and the like) runs only
 * while the menu is open. On true black the groups get a lifted container and a hairline edge,
 * since the default surfaceContainerLow is #0A0A0A and shadows do not show on black.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RebornMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    anchor: MenuAnchorPosition = MenuAnchorPosition.Below,
    groups: @Composable () -> List<MenuGroup>,
) {
    DropdownMenuPopup(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier,
        popupPositionProvider = MenuDefaults.rememberDropdownMenuPopupPositionProvider(anchor),
    ) {
        val haptics = LocalHapticFeedback.current
        val dark = LocalRebornColors.current.dark
        val scheme = MaterialTheme.colorScheme
        val all = groups().filter { it.entries.isNotEmpty() }
        all.forEachIndexed { g, group ->
            if (g > 0) Spacer(Modifier.height(MenuDefaults.GroupSpacing))
            DropdownMenuGroup(
                shapes = MenuDefaults.groupShape(g, all.size),
                containerColor = when {
                    group.vibrant -> MenuDefaults.groupVibrantContainerColor
                    dark -> scheme.surfaceContainerHigh
                    else -> MenuDefaults.groupStandardContainerColor
                },
                border = if (dark && !group.vibrant) BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.5f)) else null,
            ) {
                group.label?.let { MenuDefaults.DropdownMenuGroupLabel { Text(it) } }
                val n = group.entries.size
                group.entries.forEachIndexed { i, entry ->
                    val shapes = MenuDefaults.itemShape(i, n)
                    when (entry) {
                        is MenuAction -> DropdownMenuItem(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                onDismiss()
                                entry.onClick()
                            },
                            text = { Text(entry.label) },
                            shape = shapes.shape,
                            leadingIcon = entry.icon?.let { icon -> { MenuIcon(icon) } },
                            trailingContent = entry.trailing?.let { t ->
                                { MenuDefaults.DropdownMenuItemTrailingLabel { Text(t) } }
                            },
                            supportingText = entry.supporting?.let { s -> { Text(s) } },
                            enabled = entry.enabled,
                            colors = when {
                                entry.danger -> MenuDefaults.itemColors(
                                    textColor = scheme.error,
                                    leadingIconColor = scheme.error,
                                )
                                group.vibrant -> MenuDefaults.itemVibrantColors()
                                else -> MenuDefaults.itemColors()
                            },
                        )
                        is MenuToggle -> CheckableDropdownMenuItem(
                            checked = entry.checked,
                            onCheckedChange = {
                                haptics.performHapticFeedback(
                                    if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                                )
                                entry.onCheckedChange(it)
                            },
                            text = { Text(entry.label) },
                            shapes = shapes,
                            leadingIcon = entry.icon?.let { icon -> { MenuIcon(icon) } },
                            checkedLeadingIcon = { MenuIcon(R.drawable.ic_reborn_check) },
                            supportingText = entry.supporting?.let { s -> { Text(s) } },
                            enabled = entry.enabled,
                            colors = if (group.vibrant) MenuDefaults.selectableItemVibrantColors()
                            else MenuDefaults.selectableItemColors(),
                        )
                        is MenuChoice -> SelectableDropdownMenuItem(
                            selected = entry.selected,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onDismiss()
                                entry.onSelect()
                            },
                            text = { Text(entry.label) },
                            shapes = shapes,
                            selectedLeadingIcon = { MenuIcon(R.drawable.ic_reborn_check) },
                            supportingText = entry.supporting?.let { s -> { Text(s) } },
                            enabled = entry.enabled,
                        )
                        is MenuBusy -> DropdownMenuItem(
                            onClick = {},
                            text = { Text(entry.label) },
                            shape = shapes.shape,
                            leadingIcon = entry.icon?.let { icon -> { MenuIcon(icon) } },
                            trailingContent = { LoadingIndicator(Modifier.size(24.dp)) },
                            supportingText = entry.supporting?.let { s -> { Text(s) } },
                            enabled = false,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuIcon(@DrawableRes icon: Int) {
    Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(MenuDefaults.LeadingIconSize))
}

/** The 3-dot button: a shape-morphing IconButton that opens a [RebornMenu] below it. */
@Composable
fun RebornOverflow(
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int = R.drawable.ic_reborn_more,
    contentDescription: String = stringResource(R.string.reborn_more),
    groups: @Composable () -> List<MenuGroup>,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { open = true }, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(icon), contentDescription)
        }
        RebornMenu(expanded = open, onDismiss = { open = false }, groups = groups)
    }
}
