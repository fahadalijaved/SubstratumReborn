/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import kotlinx.coroutines.delay

/**
 * Staggered entrance: item [index] fades and springs up a beat after the one before it. Only
 * the first time ([animate] false shows it at once, e.g. when coming back to a tab).
 */
@Composable
fun Entrance(index: Int, animate: Boolean = true, content: @Composable () -> Unit) {
    // Saved, so an item scrolled out of a list and back in does not play it again
    var played by rememberSaveable { mutableStateOf(!animate) }
    val state = remember { MutableTransitionState(played) }
    LaunchedEffect(Unit) {
        if (!state.targetState) {
            delay(40L + index * 70L)
            state.targetState = true
            played = true
        }
    }
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visibleState = state,
        enter = fadeIn(motion.defaultEffectsSpec()) +
            slideInVertically(motion.defaultSpatialSpec()) { it / 5 } +
            scaleIn(motion.defaultSpatialSpec(), initialScale = 0.94f),
    ) {
        content()
    }
}

/** The pair of shapes the status hero breathes between. */
enum class HeroShape { Calm, Alert, Alarm, Idle }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun shapesOf(shape: HeroShape): Pair<RoundedPolygon, RoundedPolygon> = when (shape) {
    HeroShape.Calm -> MaterialShapes.Cookie9Sided to MaterialShapes.Sunny
    HeroShape.Alert -> MaterialShapes.SoftBurst to MaterialShapes.Cookie6Sided
    HeroShape.Alarm -> MaterialShapes.Burst to MaterialShapes.SoftBoom
    HeroShape.Idle -> MaterialShapes.Circle to MaterialShapes.Cookie4Sided
}

/**
 * An expressive shape that slowly turns and morphs between two Material shapes, with a soft halo
 * of the same colour behind it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MorphingShape(shape: HeroShape, color: Color, modifier: Modifier = Modifier) {
    val morph = remember(shape) { shapesOf(shape).let { Morph(it.first, it.second) } }
    val tint by animateColorAsState(color, MaterialTheme.motionScheme.slowEffectsSpec(), label = "heroTint")
    val transition = rememberInfiniteTransition(label = "hero")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "morph",
    )
    val turn by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(30000, easing = LinearEasing)),
        label = "turn",
    )
    val inner = remember { Path() }
    val halo = remember { Path() }
    Canvas(modifier) {
        // The shapes are unit sized: scale them to the canvas, then turn them about its centre
        morph.toPath(1f - progress, halo)
        withTransform({
            rotate(-turn * 0.6f)
            scale(size.width, size.height, pivot = Offset.Zero)
        }) {
            drawPath(halo, tint.copy(alpha = 0.22f))
        }
        morph.toPath(progress, inner)
        withTransform({
            rotate(turn)
            scale(0.80f, 0.80f)
            scale(size.width, size.height, pivot = Offset.Zero)
        }) {
            drawPath(inner, tint)
        }
    }
}
