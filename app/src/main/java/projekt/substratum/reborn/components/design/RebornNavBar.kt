/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.components.design

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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

/** The floating bar's measures, and the room a tab's root leaves for it under its content. */
object RebornNavBarDefaults {
    /** The pill's height */
    val Height = 64.dp

    /** Between the pill and the gesture area */
    val BottomMargin = 16.dp

    /** What a root's list leaves at its end (over the navigation bar inset): the pill, its margin and a gap */
    val ContentSpace = Height + BottomMargin + 8.dp

    /** A FAB beside the pill (FloatingActionButtonMenu's button) and the gap to it */
    internal val FabSize = 56.dp
    internal val FabGap = 12.dp

    /** FloatingActionButtonMenu pads its button by 16 dp at the sides and below */
    internal val FabMenuInset = 16.dp
}

/**
 * The room a tab's root leaves under its content for the floating bar (0 on pushed routes, where
 * the bar is hidden). [RebornFlexibleScaffold] adds it to its content insets, so lists scroll
 * under the bar and a screen's FAB sits above it.
 */
val LocalNavBarSpace = staticCompositionLocalOf { 0.dp }

/**
 * A tab root's FAB, shown beside the floating bar (the M3E "floating toolbar with FAB" pattern)
 * instead of over the content. The shell provides one per tab; [NavBarFab] fills it.
 */
@Stable
class NavBarFabSlot {
    internal var owner: Any? by mutableStateOf(null)
        private set
    var content: (@Composable () -> Unit)? by mutableStateOf(null)
        private set

    internal fun set(owner: Any, content: @Composable () -> Unit) {
        this.owner = owner
        this.content = content
    }

    internal fun clear(owner: Any) {
        if (this.owner !== owner) return
        this.owner = null
        content = null
    }
}

val LocalNavBarFab = staticCompositionLocalOf<NavBarFabSlot?> { null }

/**
 * Puts [content] (a FAB or RebornFabMenu) beside the floating bar while this is composed. Returns
 * false outside the shell (no slot): the caller then shows it in its own Scaffold.
 */
@Composable
fun NavBarFab(content: @Composable () -> Unit): Boolean {
    val slot = LocalNavBarFab.current ?: return false
    val latest by rememberUpdatedState(content)
    val owner = remember { Any() }
    DisposableEffect(slot, owner) {
        slot.set(owner) { latest() }
        onDispose { slot.clear(owner) }
    }
    return true
}

/**
 * The M3 Expressive floating navigation bar: a pill centred 16 dp above the gesture area, not
 * edge to edge. Unselected destinations are icons; the selected one grows into a filled pill with
 * its icon and label, on the motion scheme's springs. Tonal container with a soft shadow (a
 * hairline on true black, where a shadow does not show), SegmentTick haptic, outlined/filled icon
 * swap with a spring pop. Re-tapping the current destination calls [onReselect]. A tab's
 * [floatingActionButton] sits beside the pill, the pair centred together. Place it at the bottom
 * of a Box over the content: it pads itself by the navigation bar inset.
 */
@Composable
fun RebornNavBar(
    destinations: List<NavDest>,
    selected: String,
    onSelect: (NavDest) -> Unit,
    modifier: Modifier = Modifier,
    onReselect: (NavDest) -> Unit = {},
    badges: Set<String> = emptySet(),
    floatingActionButton: (@Composable () -> Unit)? = null,
) {
    val colors = LocalRebornColors.current
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val haptics = LocalHapticFeedback.current
    // The FAB slot animates out with its last content
    var lastFab by remember { mutableStateOf(floatingActionButton) }
    if (floatingActionButton != null) lastFab = floatingActionButton
    val hasFab = floatingActionButton != null
    val reserve by animateDpAsState(
        if (hasFab) RebornNavBarDefaults.FabSize + RebornNavBarDefaults.FabGap else 0.dp,
        motion.defaultSpatialSpec(),
        label = "navBarFab",
    )
    // The FAB menu reaches below the pill (its button is centred on it, padded 16 dp below)
    val below = RebornNavBarDefaults.FabMenuInset - (RebornNavBarDefaults.Height - RebornNavBarDefaults.FabSize) / 2
    Layout(
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = RebornNavBarDefaults.BottomMargin - below),
        content = {
            Surface(
                shape = CircleShape,
                // surfaceContainerHigh with a 1 dp light edge on true black; white in light mode
                color = if (colors.dark) scheme.surfaceContainerHigh else scheme.surfaceContainerLowest,
                shadowElevation = 6.dp,
                border = if (colors.dark) BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)) else null,
            ) {
                Row(
                    Modifier
                        .height(RebornNavBarDefaults.Height)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    destinations.forEach { dest ->
                        val isSelected = dest.key == selected
                        NavItem(dest, isSelected, badge = dest.key in badges) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            if (isSelected) onReselect(dest) else onSelect(dest)
                        }
                    }
                }
            }
            Box {
                AnimatedVisibility(
                    visible = hasFab,
                    enter = fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.6f),
                    exit = fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 0.6f),
                ) {
                    lastFab?.invoke()
                }
            }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val bar = measurables[0].measure(loose)
        val fab = measurables[1].measure(loose)
        val belowPx = below.roundToPx()
        val reservePx = reserve.roundToPx()
        val width = constraints.maxWidth
        val groupWidth = bar.width + reservePx
        val start = ((width - groupWidth) / 2).coerceAtLeast(0)
        // Tall enough for the open FAB menu: touches outside a layout's bounds do not reach it
        val height = maxOf(bar.height + belowPx, fab.height)
        val barTop = height - belowPx - bar.height
        layout(width, height) {
            bar.place(start, barTop)
            if (fab.width > 0 && fab.height > 0) {
                // The menu's button at the group's end, centred on the pill
                val right = start + groupWidth + RebornNavBarDefaults.FabMenuInset.roundToPx()
                fab.place(right - fab.width, height - fab.height)
            }
        }
    }
}

/**
 * One destination: 48 dp, an icon at rest; selected, a primary pill (16 dp before the filled icon,
 * 20 dp after the label) that springs open. Pressed, its corners tighten to 14 dp.
 */
@Composable
private fun NavItem(dest: NavDest, selected: Boolean, badge: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val container by animateColorAsState(
        if (selected) scheme.primary else Color.Transparent,
        motion.defaultEffectsSpec(),
        label = "navPill",
    )
    val content by animateColorAsState(
        if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
        motion.defaultEffectsSpec(),
        label = "navContent",
    )
    val start by animateDpAsState(if (selected) 16.dp else 12.dp, motion.fastSpatialSpec(), label = "navStart")
    val end by animateDpAsState(if (selected) 20.dp else 12.dp, motion.fastSpatialSpec(), label = "navEnd")
    val interaction = remember { MutableInteractionSource() }
    val shape = pressShape(interaction, rest = 24.dp, pressed = 14.dp)
    val label = stringResource(dest.label)
    Row(
        Modifier
            .height(48.dp)
            .clip(shape)
            .background(container)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = interaction,
                indication = ripple(),
            )
            .padding(start = start, end = end),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The label is only drawn when selected: unselected, the icon says it
        NavIcon(dest, selected, badge, tint = content, description = if (selected) null else label)
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(motion.defaultEffectsSpec()) + expandHorizontally(motion.fastSpatialSpec(), expandFrom = Alignment.Start),
            exit = fadeOut(motion.fastEffectsSpec()) + shrinkHorizontally(motion.fastSpatialSpec(), shrinkTowards = Alignment.Start),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = content,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun NavIcon(dest: NavDest, selected: Boolean, badge: Boolean, tint: Color, description: String?) {
    val motion = MaterialTheme.motionScheme
    val pop = remember { Animatable(1f) }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(selected) {
        // Pop (1 → 1.16 → 1) only when the user selects it, not on first composition. Left mid-pop
        // (another tab chosen at once), it settles back instead of staying enlarged.
        if (selected && settled) {
            pop.animateTo(1.16f, motion.fastSpatialSpec())
            pop.animateTo(1f, motion.fastSpatialSpec())
        } else if (pop.value != 1f) {
            pop.animateTo(1f, motion.fastSpatialSpec())
        }
        settled = true
    }
    BadgedBox(badge = { if (badge) Badge() }) {
        Crossfade(selected, animationSpec = motion.fastEffectsSpec(), label = "navIcon") { filled ->
            Icon(
                painterResource(if (filled) dest.selectedIcon else dest.icon),
                contentDescription = description,
                tint = tint,
                modifier = Modifier.graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                },
            )
        }
    }
}
