/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.components.design

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tab switch, M3 fade-through on the expressive springs. AnimatedContent's transitionSpec is not
 * composable: read `val motion = MaterialTheme.motionScheme` outside and pass it in.
 */
fun fadeThrough(motion: MotionScheme): ContentTransform =
    (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.96f)) togetherWith
        (fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 0.98f))

/** A card's corner that springs from [rest] to [pressed] while [interaction] is pressed. */
@Composable
fun pressShape(interaction: InteractionSource, rest: Dp = 28.dp, pressed: Dp = 20.dp): Shape {
    val isPressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(
        if (isPressed) pressed else rest,
        MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "pressCorner",
    )
    return RoundedCornerShape(corner)
}

/** The matching press scale (1 to 0.97), for Modifier.graphicsLayer { scaleX = s; scaleY = s }. */
@Composable
fun pressScale(interaction: InteractionSource, pressed: Float = 0.97f): Float {
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) pressed else 1f,
        MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "pressScale",
    )
    return scale
}
