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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import projekt.substratum.R
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Before and after, one over the other: [content] drawn with [MockSide.Stock] on the left of a
 * vertical divider and with [MockSide.Amoled] on its right. Drag the divider (or tap) to compare;
 * it nudges once on first show so the gesture is discoverable.
 *
 * The two labels sit in a row above the preview, never over it: each one grows stronger as its
 * side takes more of the preview.
 *
 * @param frameShape The preview's corners (the labels sit outside it)
 * @param onClick Taps call it instead of moving the divider (a preview that opens something)
 * @param handleOnly Only a drag that starts near the divider moves it; any other drag is left to
 *   a parent (a pager the preview sits in)
 * @param sweep Null plays the nudge once, the first time the preview shows; otherwise the divider
 *   sweeps across and back each time it turns true (a showcase page coming into view)
 */
@Composable
fun BeforeAfter(
    beforeLabel: String,
    afterLabel: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    frameShape: Shape = RoundedCornerShape(22.dp),
    onClick: (() -> Unit)? = null,
    handleOnly: Boolean = false,
    sweep: Boolean? = null,
    content: @Composable (MockSide) -> Unit,
) {
    var fraction by rememberSaveable { mutableFloatStateOf(0.5f) }
    var hinted by rememberSaveable { mutableStateOf(false) }
    var width by remember { mutableIntStateOf(0) }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var motion by remember { mutableStateOf<Job?>(null) }
    val handle = 44.dp
    val density = LocalDensity.current
    val handlePx = with(density) { handle.toPx() }
    // A drag that starts this close to the divider takes it (in handleOnly mode)
    val reachPx = with(density) { 36.dp.toPx() }
    val click by rememberUpdatedState(onClick)

    LaunchedEffect(sweep) {
        when (sweep) {
            null -> {
                if (hinted) return@LaunchedEffect
                hinted = true
                motion = launch {
                    delay(650)
                    animate(fraction, 0.68f, animationSpec = tween(520, easing = FastOutSlowInEasing)) { v, _ -> fraction = v }
                    animate(fraction, 0.5f, animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)) { v, _ ->
                        fraction = v
                    }
                }
            }
            true -> motion = launch {
                delay(450)
                animate(fraction, 0.82f, animationSpec = tween(700, easing = FastOutSlowInEasing)) { v, _ -> fraction = v }
                animate(fraction, 0.18f, animationSpec = tween(1100, easing = FastOutSlowInEasing)) { v, _ -> fraction = v }
                animate(fraction, 0.5f, animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessVeryLow)) { v, _ ->
                    fraction = v
                }
            }
            false -> Unit
        }
    }

    val dragModifier = if (handleOnly) {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (size.width <= 0 || abs(down.position.x - size.width * fraction) > reachPx) {
                    // Not the divider: the parent (a pager) takes the drag
                    return@awaitEachGesture
                }
                val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                    change.consume()
                    fraction = (fraction + over / size.width).coerceIn(0f, 1f)
                } ?: return@awaitEachGesture
                motion?.cancel()
                haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                horizontalDrag(drag.id) { change ->
                    change.consume()
                    fraction = (fraction + change.positionChange().x / size.width).coerceIn(0f, 1f)
                }
                haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
            }
        }
    } else {
        Modifier.pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = {
                    motion?.cancel()
                    haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                },
                onDragEnd = { haptics.performHapticFeedback(HapticFeedbackType.GestureEnd) },
            ) { change, dx ->
                change.consume()
                if (size.width > 0) fraction = (fraction + dx / size.width).coerceIn(0f, 1f)
            }
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The side that shows more of the preview reads stronger
            SideLabel(beforeLabel, dot = Color(0xFF3A3A3D), emphasis = { fraction }, leading = true)
            Spacer(Modifier.weight(1f))
            SideLabel(afterLabel, dot = Color.Black, emphasis = { 1f - fraction }, leading = false)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(frameShape)
                .clipToBounds()
                .onSizeChanged { width = it.width }
                .semantics {
                    if (description != null) contentDescription = description
                    if (onClick != null) {
                        role = Role.Button
                        onClick { click?.invoke(); true }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val open = click
                        if (open != null) {
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            open()
                            return@detectTapGestures
                        }
                        motion?.cancel()
                        val target = (offset.x / size.width).coerceIn(0f, 1f)
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        motion = scope.launch {
                            animate(fraction, target, animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { v, _ ->
                                fraction = v
                            }
                        }
                    }
                }
                .then(dragModifier),
        ) {
            Box { content(MockSide.Stock) }
            Box(
                Modifier
                    .matchParentSize()
                    .drawWithContent {
                        clipRect(left = size.width * fraction) { this@drawWithContent.drawContent() }
                    },
            ) { content(MockSide.Amoled) }
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
}

/**
 * A label above one side of the preview: a dot of that side's surface colour and its name, as
 * strong as [emphasis] (0 to 1, how much of the preview the side takes; read while drawing, so a
 * drag does not recompose the preview).
 */
@Composable
private fun SideLabel(text: String, dot: Color, emphasis: () -> Float, leading: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .graphicsLayer { alpha = 0.5f + 0.5f * emphasis().coerceIn(0f, 1f) }
            .clip(CircleShape)
            .background(scheme.surfaceContainerHighest)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading) {
            Dot(dot)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurface,
            maxLines = 1,
        )
        if (!leading) {
            Spacer(Modifier.width(6.dp))
            Dot(dot)
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(
        Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(Color(0xFF8E8E93))
            .padding(1.dp)
            .clip(CircleShape)
            .background(color),
    )
}
