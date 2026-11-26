/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The deck's resting angles, back to front, and how much further each fans on a press. */
private val DECK_ANGLES = floatArrayOf(66f, 44f, 22f, 0f)
private val DECK_FAN = floatArrayOf(12f, 10f, 8f, 0f)

/**
 * The Substratum swatch deck, reborn: four pill swatches fanned about a rivet, in the theme's
 * colours (front to back: primary, a step towards inversePrimary, tertiaryFixedDim and a deep
 * tertiary), so it follows the accent and dynamic colour. [fan] spreads the swatches further
 * (0 at rest, 1 fully fanned). The launcher icon is the same deck in its classic fills
 * (res/drawable/ic_reborn_brand.xml, ic_launcher_deck_*).
 */
@Composable
fun BrandDeck(modifier: Modifier = Modifier, size: Dp = 30.dp, fan: Float = 0f) {
    val scheme = MaterialTheme.colorScheme
    val fills = listOf(
        lerp(scheme.tertiary, scheme.tertiaryContainer, 0.25f),
        scheme.tertiaryFixedDim,
        lerp(scheme.primary, scheme.inversePrimary, 0.45f),
        scheme.primary,
    )
    val rivet = scheme.onPrimary
    Canvas(modifier.size(size)) {
        // The deck is drawn in a 48 by 48 box
        val unit = this.size.minDimension / 48f
        scale(unit, unit, pivot = Offset.Zero) {
            val pivot = Offset(15f, 35f)
            fills.forEachIndexed { i, fill ->
                rotate(DECK_ANGLES[i] + DECK_FAN[i] * fan, pivot) {
                    drawRoundRect(
                        color = fill,
                        topLeft = Offset(9f, 7f),
                        size = Size(12f, 34f),
                        cornerRadius = CornerRadius(6f, 6f),
                    )
                }
            }
            drawCircle(color = rivet, radius = 3.6f, center = pivot)
        }
    }
}

/**
 * Home's brand mark: the deck at 30 dp on a 40 dp Cookie12Sided plate. It fans out once when it
 * first shows, and again while pressed, on the expressive spatial spring.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BrandMark(modifier: Modifier = Modifier, plate: Color = MaterialTheme.colorScheme.surfaceContainerHigh) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fan = remember { Animatable(0f) }
    var greeted by rememberSaveable { mutableStateOf(false) }
    val spring = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val settle = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(Unit) {
        if (greeted) return@LaunchedEffect
        greeted = true
        fan.animateTo(1f, spring)
        fan.animateTo(0f, settle)
    }
    LaunchedEffect(pressed) {
        if (pressed) fan.animateTo(1f, spring) else fan.animateTo(0f, settle)
    }
    Box(
        modifier
            .size(40.dp)
            .clip(MaterialShapes.Cookie12Sided.toShape())
            .background(plate)
            .clickable(interactionSource = interaction, indication = null, role = Role.Image) {}
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        BrandDeck(size = 30.dp, fan = fan.value)
    }
}
