/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.components.design

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarScrollBehavior
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import projekt.substratum.R

@Immutable
class FabMenuItem(val label: String, @DrawableRes val icon: Int, val onClick: () -> Unit)

/**
 * The M3E FAB menu (create / install): the FAB morphs into a close button and the items fan out
 * in the tertiary family (themer and "create" actions). Back closes it. Put it in the screen
 * Scaffold's floatingActionButton slot; [visible] false (scrolling down) shrinks it away.
 */
@Composable
fun RebornFabMenu(
    items: List<FabMenuItem>,
    @DrawableRes icon: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    BackHandler(expanded) { expanded = false }
    FloatingActionButtonMenu(
        expanded = expanded,
        modifier = modifier,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = {
                    haptics.performHapticFeedback(if (it) HapticFeedbackType.LongPress else HapticFeedbackType.ContextClick)
                    expanded = it
                },
                modifier = Modifier
                    .semantics {
                        traversalIndex = -1f
                        stateDescription = if (expanded) "Expanded" else "Collapsed"
                        this.contentDescription = contentDescription
                    }
                    .animateFloatingActionButton(visible = visible || expanded, alignment = Alignment.BottomEnd),
            ) {
                val res = if (checkedProgress > 0.5f) R.drawable.ic_reborn_close else icon
                Icon(painterResource(res), contentDescription = null, modifier = Modifier.animateIcon({ checkedProgress }))
            }
        },
    ) {
        items.forEach { item ->
            FloatingActionButtonMenuItem(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    expanded = false
                    item.onClick()
                },
                text = { Text(item.label) },
                icon = { Icon(painterResource(item.icon), contentDescription = null) },
                containerColor = scheme.tertiaryContainer,
            )
        }
    }
}

/** A single primary action FAB that shrinks to its icon while the list scrolls. */
@Composable
fun RebornExtendedFab(
    label: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
) {
    MediumExtendedFloatingActionButton(
        text = { Text(label) },
        icon = { Icon(painterResource(icon), contentDescription = null) },
        onClick = onClick,
        modifier = modifier,
        expanded = expanded,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
    )
}

/**
 * The vibrant floating toolbar for page actions, with the primary action as its FAB (busy: the
 * FAB shows a loading indicator). Pair it with Modifier.nestedScroll(scrollBehavior) on the
 * scrolling container's ancestor; place it in a Box, aligned BottomCenter, offset by
 * -FloatingToolbarDefaults.ScreenOffset, zIndex(1f).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RebornFloatingToolbar(
    expanded: Boolean,
    @DrawableRes primaryIcon: Int,
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    primaryBusy: Boolean = false,
    scrollBehavior: FloatingToolbarScrollBehavior? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors()
    HorizontalFloatingToolbar(
        expanded = expanded,
        floatingActionButton = {
            FloatingToolbarDefaults.VibrantFloatingActionButton(onClick = onPrimary) {
                if (primaryBusy) {
                    LoadingIndicator(Modifier.size(28.dp), color = colors.fabContentColor)
                } else {
                    Icon(painterResource(primaryIcon), contentDescription = primaryLabel)
                }
            }
        },
        modifier = modifier,
        colors = colors,
        scrollBehavior = scrollBehavior,
        content = content,
    )
}
