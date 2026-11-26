/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.components.design

import android.os.SystemClock
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** A page's first load: the contained (primaryContainer) indicator, centred in a fixed slot. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PageLoading(modifier: Modifier = Modifier, height: Dp = 280.dp) {
    Box(modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
        ContainedLoadingIndicator(Modifier.size(72.dp))
    }
}

/**
 * Determinate work (apply, build): the contained indicator morphs from a circle to a burst as
 * [progress] goes 0 to 1. Replaces percent text in a hand-made cookie.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProgressShape(progress: Float, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val p by animateFloatAsState(
        progress.coerceIn(0f, 1f),
        WavyProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "progressShape",
    )
    ContainedLoadingIndicator(progress = { p }, modifier = modifier.size(size))
}

/**
 * Button content that turns into a 24 dp loading indicator while [busy], keeping the button's
 * width (the label stays laid out, invisible). Google apps do this for in-button work.
 * Use it as the whole content of a Button/FilledTonalButton/TextButton.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BusyLabel(busy: Boolean, color: Color = LocalContentColor.current, label: @Composable () -> Unit) {
    Box(contentAlignment = Alignment.Center) {
        Box(Modifier.alpha(if (busy) 0f else 1f)) { label() }
        if (busy) LoadingIndicator(Modifier.size(24.dp), color = color)
    }
}

/**
 * Pull to refresh with the M3E morphing loading indicator (the Google-app refresh). Put the
 * Scaffold's top padding on this box's [modifier] (so the indicator drops in under the top bar)
 * and keep the bottom padding in the list's contentPadding.
 *
 * [scrollConnection]: the collapsing top bar's nested scroll (with the scaffold's connectScroll
 * false). It sits between the list and the pull, so a pull at the top first expands the bar and
 * only then refreshes; on the scaffold, outside the pull, the pull would take it first.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RebornPullToRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    scrollConnection: NestedScrollConnection? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    val haptics = LocalHapticFeedback.current
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            onRefresh()
        },
        modifier = modifier,
        state = state,
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = state,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        if (scrollConnection == null) {
            content()
        } else {
            Box(Modifier.fillMaxSize().nestedScroll(scrollConnection), content = content)
        }
    }
}

/** A value read off the main thread that pull-to-refresh can read again. */
@Stable
class Refreshable<T>(value: T?, refreshing: Boolean, val refresh: () -> Unit) {
    val value: T? = value
    val refreshing: Boolean = refreshing
}

/**
 * Like components.rememberRoot (runs [block] on Dispatchers.IO, keeps [initial] until the first
 * result, keeps the old value while re-reading), plus [Refreshable.refresh] for pull-to-refresh.
 * The indicator stays at least 600 ms so its morph is seen.
 */
@Composable
fun <T> rememberRefreshableRoot(vararg keys: Any?, initial: T? = null, block: () -> T): Refreshable<T> {
    val latest by rememberUpdatedState(block)
    var tick by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    val state = produceState(initialValue = initial, *keys, tick) {
        val started = SystemClock.uptimeMillis()
        val result = withContext(Dispatchers.IO) {
            try {
                latest()
            } catch (t: Throwable) {
                Log.e("Reborn", "background read failed", t)
                null
            }
        }
        if (result != null) value = result
        if (refreshing) {
            delay((600 - (SystemClock.uptimeMillis() - started)).coerceAtLeast(0))
            refreshing = false
        }
    }
    return Refreshable(state.value, refreshing) {
        refreshing = true
        tick++
    }
}
