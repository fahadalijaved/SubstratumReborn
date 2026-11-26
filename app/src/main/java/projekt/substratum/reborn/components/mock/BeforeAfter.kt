/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.components.mock

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import projekt.substratum.R
import kotlin.math.roundToInt

/**
 * Before and after, one over the other: [content] drawn with [MockSide.Stock] on the left of a
 * vertical divider and with [MockSide.Amoled] on its right. Drag the divider (or tap) to compare;
 * it nudges once on first show so the gesture is discoverable.
 */
@Composable
fun BeforeAfter(
    beforeLabel: String,
    afterLabel: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable (MockSide) -> Unit,
) {
    var fraction by rememberSaveable { mutableFloatStateOf(0.5f) }
    var hinted by rememberSaveable { mutableStateOf(false) }
    var width by remember { mutableIntStateOf(0) }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var hint by remember { mutableStateOf<Job?>(null) }
    val handle = 44.dp
    val handlePx = with(LocalDensity.current) { handle.toPx() }

    LaunchedEffect(Unit) {
        if (hinted) return@LaunchedEffect
        hinted = true
        hint = launch {
            delay(650)
            animate(fraction, 0.68f, animationSpec = tween(520, easing = FastOutSlowInEasing)) { v, _ -> fraction = v }
            animate(fraction, 0.5f, animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)) { v, _ ->
                fraction = v
            }
        }
    }

    Box(
        modifier
            .clipToBounds()
            .onSizeChanged { width = it.width }
            .semantics { if (description != null) contentDescription = description }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    hint?.cancel()
                    val target = (offset.x / size.width).coerceIn(0f, 1f)
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    scope.launch {
                        animate(fraction, target, animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { v, _ ->
                            fraction = v
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        hint?.cancel()
                        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                    },
                    onDragEnd = { haptics.performHapticFeedback(HapticFeedbackType.GestureEnd) },
                ) { change, dx ->
                    change.consume()
                    if (size.width > 0) fraction = (fraction + dx / size.width).coerceIn(0f, 1f)
                }
            },
    ) {
        Box { content(MockSide.Stock) }
        Box(
            Modifier
                .matchParentSize()
                .drawWithContent {
                    clipRect(left = size.width * fraction) { this@drawWithContent.drawContent() }
                },
        ) { content(MockSide.Amoled) }
        Pill(beforeLabel, Modifier.align(Alignment.TopStart).padding(10.dp))
        Pill(afterLabel, Modifier.align(Alignment.TopEnd).padding(10.dp))
        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
                    val x = size.width * fraction
                    drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
                },
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((width * fraction - handlePx / 2f).roundToInt(), 0) }
                .shadow(8.dp, CircleShape)
                .size(handle)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_reborn_drag),
                contentDescription = null,
                tint = Color(0xFF111111),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun Pill(text: String, modifier: Modifier) {
    Box(
        modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
    }
}
