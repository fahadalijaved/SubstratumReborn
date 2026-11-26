/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.components.design

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import projekt.substratum.reborn.theme.LocalRebornColors

/** A top-level destination: outlined icon at rest, filled when selected (Material Symbols pair). */
@Immutable
data class NavDest(
    val key: String,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    @DrawableRes val selectedIcon: Int,
)

/**
 * The M3 Expressive short navigation bar: 64 dp (not the 80 dp baseline bar that reads as One UI),
 * pill indicator, outlined/filled icon swap with a spring pop, emphasized label when selected,
 * SegmentTick haptic. Light: tonal container. Dark (true black): black with a 1 dp hairline on
 * top, never a grey slab. Re-tapping the current destination calls [onReselect].
 */
@Composable
fun RebornNavBar(
    destinations: List<NavDest>,
    selected: String,
    onSelect: (NavDest) -> Unit,
    modifier: Modifier = Modifier,
    onReselect: (NavDest) -> Unit = {},
    badges: Set<String> = emptySet(),
) {
    val colors = LocalRebornColors.current
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val edge = colors.cardBorder
    ShortNavigationBar(
        modifier = modifier.drawWithContent {
            drawContent()
            if (colors.dark) drawLine(edge, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx())
        },
        containerColor = if (colors.dark) scheme.background else scheme.surfaceContainer,
    ) {
        destinations.forEach { dest ->
            val isSelected = dest.key == selected
            ShortNavigationBarItem(
                selected = isSelected,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    if (isSelected) onReselect(dest) else onSelect(dest)
                },
                icon = { NavIcon(dest, isSelected, badge = dest.key in badges) },
                label = {
                    Text(
                        stringResource(dest.label),
                        style = if (isSelected) MaterialTheme.typography.labelMediumEmphasized
                        else MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                },
            )
        }
    }
}

@Composable
private fun NavIcon(dest: NavDest, selected: Boolean, badge: Boolean) {
    val motion = MaterialTheme.motionScheme
    val pop = remember { Animatable(1f) }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(selected) {
        // Pop only when the user selects it, not on first composition
        if (selected && settled) {
            pop.snapTo(0.8f)
            pop.animateTo(1f, motion.fastSpatialSpec())
        }
        settled = true
    }
    BadgedBox(badge = { if (badge) Badge() }) {
        Crossfade(selected, animationSpec = motion.fastEffectsSpec(), label = "navIcon") { filled ->
            Icon(
                painterResource(if (filled) dest.selectedIcon else dest.icon),
                contentDescription = null,
                modifier = Modifier.graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                },
            )
        }
    }
}
