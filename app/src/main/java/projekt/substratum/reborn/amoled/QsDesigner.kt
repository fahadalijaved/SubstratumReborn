/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.amoled

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import projekt.substratum.R
import projekt.substratum.common.platform.AmoledRules
import projekt.substratum.common.platform.QsOptions
import projekt.substratum.reborn.components.ConfirmDialog
import projekt.substratum.reborn.components.SectionCard
import projekt.substratum.reborn.components.ShapedIcon
import projekt.substratum.reborn.components.isOneUi
import projekt.substratum.reborn.components.stockLookName
import projekt.substratum.reborn.components.mock.MockRoles
import projekt.substratum.reborn.components.mock.MockSide
import projekt.substratum.reborn.components.mock.QsMock
import projekt.substratum.reborn.theme.LocalRebornColors
import kotlin.math.abs
import kotlin.math.roundToInt

/** Named row heights: the slider snaps to them and its value is always said by name or share. */
private class Stop(val aspect: Float, @StringRes val label: Int)

private val Stops = listOf(
    Stop(0.6f, R.string.reborn_studio_qs_compact),
    Stop(0.75f, R.string.reborn_studio_qs_default),
    Stop(QsOptions.STOCK_ASPECT, R.string.reborn_studio_qs_stock),
)
private const val SNAP = 0.012f

@StringRes
private fun tileLabel(tile: String): Int = when (tile) {
    AmoledRules.TILE_ACCENT -> R.string.amoled_qs_tile_accent
    AmoledRules.TILE_BLUE -> R.string.amoled_qs_tile_blue
    AmoledRules.TILE_STOCK -> R.string.amoled_qs_tile_stock
    else -> R.string.amoled_qs_tile_white
}

/**
 * The Quick Settings designer: a live panel and the options the LSPosed module carries out in
 * System UI (opaque shade, dark tracks, active tile, row height). Nothing changes on the device
 * until "Apply to Quick Settings", which restarts System UI after a confirmation. The colour
 * options act on top of AMOLED, so they wait for it; the row height applies without it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QsDesigner(
    previews: StudioState.Previews?,
    saved: StudioState.QsSaved?,
    savedTile: String,
    amoledOn: Boolean,
    modifier: Modifier = Modifier,
) {
    // The options hook One UI 9's System UI: elsewhere they would do nothing
    if (!isOneUi()) {
        QsDesignerOneUiOnly(modifier)
        return
    }
    val haptics = LocalHapticFeedback.current
    val colors = LocalRebornColors.current
    val scheme = MaterialTheme.colorScheme
    var aspect by rememberSaveable { mutableFloatStateOf(saved?.aspect ?: QsOptions.STOCK_ASPECT) }
    var shade by rememberSaveable { mutableStateOf(saved?.shade ?: true) }
    var track by rememberSaveable { mutableStateOf(saved?.track ?: true) }
    var tile by rememberSaveable { mutableStateOf(savedTile) }
    // Until the user touches a control, the designer follows what is saved
    var touched by rememberSaveable { mutableStateOf(false) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(saved, savedTile) {
        if (!touched && saved != null) {
            aspect = saved.aspect
            shade = saved.shade
            track = saved.track
            tile = savedTile
        }
    }
    val rowsChanged = saved != null && abs(aspect - saved.aspect) > 0.004f
    val colorsChanged = saved != null && (shade != saved.shade || track != saved.track || tile != savedTile)
    val canApply = (rowsChanged || colorsChanged) && (amoledOn || !colorsChanged)

    val roles = previews?.tiles?.get(tile) ?: previews?.look ?: MockRoles.Empty
    SectionCard(modifier = modifier) {
        QsDesignerHeader()
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black)
                .padding(6.dp),
        ) {
            QsMock(
                roles = roles,
                side = MockSide.Amoled,
                modifier = Modifier.fillMaxWidth(),
                aspect = aspect,
                opaqueShade = shade,
                darkTracks = track,
            )
        }
        AnimatedVisibility(visible = !amoledOn) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = colors.warn.container,
                border = if (colors.dark) BorderStroke(1.dp, colors.warn.accent.copy(alpha = 0.35f)) else null,
                modifier = Modifier.padding(top = 14.dp),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(R.drawable.ic_reborn_warning),
                        contentDescription = null,
                        tint = colors.warn.accent,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.reborn_studio_qs_off),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.warn.onContainer,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        SwitchRow(
            title = stringResource(R.string.reborn_studio_qs_shade),
            text = stringResource(R.string.reborn_studio_qs_shade_text),
            checked = shade,
            enabled = amoledOn,
            onChange = {
                haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                shade = it
                touched = true
            },
        )
        SwitchRow(
            title = stringResource(R.string.reborn_studio_qs_tracks),
            text = stringResource(R.string.reborn_studio_qs_tracks_text),
            checked = track,
            enabled = amoledOn,
            onChange = {
                haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                track = it
                touched = true
            },
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.reborn_studio_qs_tile), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            val options = StudioState.TILE_OPTIONS
            options.forEachIndexed { index, option ->
                ToggleButton(
                    checked = tile == option,
                    onCheckedChange = {
                        if (tile != option) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            tile = option
                            touched = true
                        }
                    },
                    enabled = amoledOn,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { role = Role.RadioButton },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) {
                    Text(stringResource(tileLabel(option)), maxLines = 1, softWrap = false)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.reborn_studio_qs_rows),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            val stop = Stops.firstOrNull { abs(it.aspect - aspect) < 0.002f }
            Text(
                if (stop != null) stringResource(stop.label)
                else stringResource(R.string.reborn_studio_qs_percent, (aspect / QsOptions.STOCK_ASPECT * 100).roundToInt()),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = scheme.primary,
            )
        }
        Slider(
            value = aspect,
            onValueChange = { value ->
                val snapped = Stops.firstOrNull { abs(it.aspect - value) < SNAP }?.aspect ?: value
                if (snapped != aspect && Stops.any { it.aspect == snapped }) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                }
                aspect = snapped
                touched = true
            },
            valueRange = QsOptions.MIN_ASPECT..QsOptions.STOCK_ASPECT,
        )
        // The named stops, under where they sit on the slider; a tap goes there
        Box(Modifier.fillMaxWidth()) {
            Stops.forEach { stop ->
                val fraction = (stop.aspect - QsOptions.MIN_ASPECT) / (QsOptions.STOCK_ASPECT - QsOptions.MIN_ASPECT)
                val chosen = abs(stop.aspect - aspect) < 0.002f
                Text(
                    stringResource(stop.label),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
                    color = if (chosen) scheme.primary else scheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(BiasAlignment(fraction * 2f - 1f, 0f))
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            aspect = stop.aspect
                            touched = true
                        }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = { confirm = true },
            enabled = canApply,
            modifier = Modifier
                .fillMaxWidth()
                .height(ButtonDefaults.MediumContainerHeight),
            shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
        ) {
            Icon(painterResource(R.drawable.ic_reborn_systemui), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.reborn_studio_qs_apply), style = MaterialTheme.typography.titleSmall)
        }
    }
    if (confirm) {
        ConfirmDialog(
            title = stringResource(R.string.reborn_studio_qs_confirm_title),
            text = stringResource(R.string.reborn_studio_qs_confirm_text),
            confirmLabel = stringResource(R.string.reborn_studio_qs_confirm),
            icon = R.drawable.ic_reborn_restart,
            onDismiss = { confirm = false },
            onConfirm = {
                StudioState.applyQuickSettings(aspect, shade, track, tile)
                touched = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QsDesignerHeader() {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        ShapedIcon(
            icon = R.drawable.ic_reborn_systemui,
            container = scheme.tertiaryContainer,
            content = scheme.onTertiaryContainer,
            size = 48.dp,
            shape = MaterialShapes.Cookie6Sided.toShape(),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.reborn_studio_qs_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.reborn_studio_qs_text),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

/** Not One UI: what the designer is for, and a short note instead of controls that do nothing. */
@Composable
private fun QsDesignerOneUiOnly(modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    SectionCard(modifier = modifier) {
        QsDesignerHeader()
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = scheme.surfaceContainerHighest,
            modifier = Modifier.padding(top = 14.dp),
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_reborn_info),
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.reborn_studio_qs_oneui_only, stockLookName()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, text: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) scheme.onSurface else scheme.onSurface.copy(alpha = 0.38f),
            )
            Text(text, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
