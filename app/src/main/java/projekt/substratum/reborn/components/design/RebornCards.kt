/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.components.design

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import projekt.substratum.R
import projekt.substratum.reborn.theme.LocalRebornColors

/** The hero card: 32 dp (extraLargeIncreased) corners, heroCard colour, hairline on black. */
@Composable
fun HeroCard(
    modifier: Modifier = Modifier,
    container: Color = LocalRebornColors.current.heroCard,
    contentPadding: PaddingValues = PaddingValues(24.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalRebornColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLargeIncreased,
        color = container,
        border = if (colors.dark) BorderStroke(1.dp, colors.cardBorder) else null,
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/** A theme's colours as overlapping dots with a ring in the card colour. */
@Composable
fun PaletteStrip(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    dot: Dp = 16.dp,
    overlap: Dp = 4.dp,
    ring: Color = LocalRebornColors.current.card,
    description: String? = null,
) {
    Row(
        modifier.semantics { if (description != null) contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(-overlap),
    ) {
        colors.forEach { c ->
            Box(
                Modifier
                    .size(dot)
                    .border(2.dp, ring, CircleShape)
                    .padding(1.dp)
                    .clip(CircleShape)
                    .background(c),
            )
        }
    }
}

/**
 * The theme card: art (16:9) with a scrim, avatar, name and byline over it; chips and the
 * palette below. Corners spring 28 to 20 dp and the card scales to 0.97 while pressed. Long
 * press = LongPress haptic + [onLongClick] (anchor a RebornMenu to the card for it).
 * [artModifier] lets E1 attach Modifier.sharedBounds(...) for the card-to-detail transition.
 * [supporting] is a quiet line above the chips (package and version for namesakes). The chips
 * wrap onto a second line when they do not fit.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ThemeCard(
    name: String,
    byline: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    artModifier: Modifier = Modifier,
    inUse: Boolean = false,
    palette: List<Color> = emptyList(),
    supporting: String? = null,
    avatar: (@Composable () -> Unit)? = null,
    chips: @Composable RowScope.() -> Unit = {},
    art: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalRebornColors.current
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val shape = pressShape(interaction)
    val scale = pressScale(interaction)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick?.let { long ->
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        long()
                    }
                },
            ),
        shape = shape,
        color = colors.card,
        border = when {
            inUse -> BorderStroke(2.dp, scheme.primary)
            colors.dark -> BorderStroke(1.dp, colors.cardBorder)
            else -> null
        },
    ) {
        Column {
            Box(artModifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                art()
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.4f to Color.Transparent,
                                1f to scheme.scrim.copy(alpha = 0.82f),
                            ),
                        ),
                )
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 18.dp, end = 18.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (avatar != null) {
                        avatar()
                        Spacer(Modifier.width(14.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            name,
                            style = MaterialTheme.typography.headlineSmallEmphasized,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            byline,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.82f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp)) {
                if (supporting != null) {
                    Text(
                        supporting,
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.size(10.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        content = chips,
                    )
                    if (palette.isNotEmpty()) PaletteStrip(palette)
                }
            }
        }
    }
}

/**
 * A theme's icon on its expressive shape: [polygon] when the caller picks one (E1: one shape per
 * theme kind), else a shape chosen by [key] so the same package always gets the same one.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ThemeAvatar(
    key: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    polygon: RoundedPolygon? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val shapes = listOf(
        MaterialShapes.Cookie7Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Arch, MaterialShapes.Pentagon,
        MaterialShapes.Cookie6Sided, MaterialShapes.Gem, MaterialShapes.Sunny, MaterialShapes.Puffy,
    )
    val shape = polygon ?: shapes[Math.floorMod(key.hashCode(), shapes.size)]
    Box(
        modifier
            .size(size)
            .clip(shape.toShape())
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/**
 * The confirm dialog, expressive: the icon on a Cookie9Sided plate, press-morphing buttons, and
 * an error-coloured confirm for destructive actions (soft reboot, remove, undo everything).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RebornConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    @DrawableRes icon: Int? = null,
    destructive: Boolean = false,
    body: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = icon?.let { res ->
            {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(if (destructive) scheme.errorContainer else scheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(res),
                        contentDescription = null,
                        tint = if (destructive) scheme.onErrorContainer else scheme.onSecondaryContainer,
                    )
                }
            }
        },
        title = { Text(title) },
        text = {
            Column {
                Text(text)
                if (body != null) {
                    Spacer(Modifier.size(16.dp))
                    body()
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onDismiss()
                    onConfirm()
                },
                shapes = ButtonDefaults.shapes(),
                colors = if (destructive) {
                    ButtonDefaults.buttonColors(containerColor = scheme.error, contentColor = scheme.onError)
                } else ButtonDefaults.buttonColors(),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.reborn_cancel))
            }
        },
    )
}
