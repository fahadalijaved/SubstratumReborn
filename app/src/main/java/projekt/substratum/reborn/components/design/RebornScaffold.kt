/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.components.design

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import projekt.substratum.R
import projekt.substratum.reborn.theme.LocalRebornColors

/**
 * Flexible top bar colours: page colour expanded; collapsed, a tonal step in light mode (so the
 * collapse is felt) and black on true black (the content scrolls under a black bar, as now).
 */
@Composable
fun rebornTopBarColors(): TopAppBarColors {
    val scheme = MaterialTheme.colorScheme
    return TopAppBarDefaults.topAppBarColors(
        containerColor = scheme.background,
        scrolledContainerColor = if (LocalRebornColors.current.dark) scheme.background else scheme.surfaceContainer,
    )
}

/** A top-bar or inline icon action whose shape morphs on press (round to squircle). */
@Composable
fun ActionIcon(
    @DrawableRes icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes(), modifier = modifier, enabled = enabled) {
        Icon(painterResource(icon), contentDescription)
    }
}

@Composable
fun BackIcon(onBack: () -> Unit) {
    ActionIcon(R.drawable.ic_reborn_back, stringResource(R.string.reborn_back), onBack)
}

/**
 * A Reborn screen: Scaffold + Large (or Medium) flexible top bar that collapses on scroll. Titles
 * use the bar's own type (no Bold override; Google Sans Flex comes from the theme). Pass
 * [scrollBehavior] when the caller needs it (re-tap a tab to scroll to top and expand the bar).
 * Keep at most two ActionIcons in [actions], then a RebornOverflow. [bottomBar] and
 * [snackbarHost] are the Scaffold's own slots (Setup's Done button, a screen's own snackbars).
 * [connectScroll] false leaves [scrollBehavior]'s nested scroll to the caller: a screen with pull to
 * refresh hands it to [RebornPullToRefresh], so the collapsed bar expands before a pull refreshes.
 * The content insets include the room a tab's root leaves for the floating navigation bar
 * ([LocalNavBarSpace]), so lists scroll under it and a FAB sits above it.
 */
@Composable
fun RebornFlexibleScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    large: Boolean = true,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(),
    connectScroll: Boolean = true,
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets.add(WindowInsets(bottom = LocalNavBarSpace.current)),
    content: @Composable (PaddingValues) -> Unit,
) {
    val titleSlot: @Composable () -> Unit = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    val subtitleSlot: (@Composable () -> Unit)? = subtitle?.let { s ->
        { Text(s, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .then(if (connectScroll) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = contentWindowInsets,
        floatingActionButton = floatingActionButton,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        topBar = {
            if (large) {
                LargeFlexibleTopAppBar(
                    title = titleSlot,
                    subtitle = subtitleSlot,
                    navigationIcon = navigationIcon,
                    actions = actions,
                    colors = rebornTopBarColors(),
                    scrollBehavior = scrollBehavior,
                )
            } else {
                MediumFlexibleTopAppBar(
                    title = titleSlot,
                    subtitle = subtitleSlot,
                    navigationIcon = navigationIcon,
                    actions = actions,
                    colors = rebornTopBarColors(),
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        content = content,
    )
}
