/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.components.design

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import projekt.substratum.R

/**
 * The M3E switch: a check in the thumb when on. Haptics (ToggleOn/ToggleOff) are built in, so
 * remove them at the call site. [onCheckedChange] null = display only (the row is the target).
 */
@Composable
fun RebornSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange?.let { change ->
            { on: Boolean ->
                haptics.performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                change(on)
            }
        },
        modifier = modifier,
        thumbContent = if (checked) {
            {
                Icon(
                    painterResource(R.drawable.ic_reborn_check),
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            }
        } else null,
        enabled = enabled,
    )
}

/**
 * The state-based Slider (the value/onValueChange overload is deprecated in material3 1.5.0)
 * behind the old value/onValueChange API, so call sites only change the name. The state follows
 * [value] whenever the user is not dragging. Steps tick with SegmentFrequentTick.
 */
@Composable
fun RebornSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    track: (@Composable (SliderState) -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val state = rememberSliderState(value = value, steps = steps, trackRange = valueRange)
    SideEffect { if (!state.isDragging && state.value != value) state.value = value }
    val interaction = remember { MutableInteractionSource() }
    Slider(
        state = state,
        onValueChange = {
            if (steps > 0 && it != state.value) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            state.value = it // the state form needs this, or the thumb never moves
            onValueChange(it)
        },
        modifier = modifier,
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interaction,
        track = track ?: { s -> SliderDefaults.Track(sliderState = s, trackCornerSize = 8.dp, modifier = Modifier.height(16.dp)) },
    )
}

private val Rainbow = listOf(
    Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
    Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000),
)

/** A hue slider's track that IS the rainbow (replaces the separate bar above the slider). */
@Composable
fun HueTrack(state: SliderState) {
    SliderDefaults.Track(
        sliderState = state,
        trackCornerSize = 12.dp,
        drawStopIndicator = null,
        colors = SliderDefaults.colors(activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent),
        modifier = Modifier
            .height(24.dp)
            .drawBehind {
                drawRoundRect(Brush.horizontalGradient(Rainbow), cornerRadius = CornerRadius(12.dp.toPx()))
            },
    )
}
