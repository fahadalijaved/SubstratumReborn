/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import projekt.substratum.R
import projekt.substratum.reborn.components.design.BusyLabel
import projekt.substratum.reborn.components.design.RebornConfirmDialog
import projekt.substratum.reborn.theme.LocalRebornColors

/** 28 dp, the M3E extraLarge step. */
val CardShape = ShapeDefaults.ExtraLarge

/**
 * The Reborn card: 28 dp corners on a container that reads on true black (with a hairline edge
 * in dark mode).
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    container: Color = LocalRebornColors.current.card,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalRebornColors.current
    val border = if (colors.dark) BorderStroke(1.dp, colors.cardBorder) else null
    val inner: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .animateContentSize()
                .padding(contentPadding),
            content = content,
        )
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = CardShape,
            color = container,
            border = border,
            content = inner,
        )
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = CardShape,
            color = container,
            border = border,
            content = inner,
        )
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLargeEmphasized,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .padding(start = 8.dp, top = 12.dp, bottom = 2.dp)
            .semantics { heading() },
    )
}

/** An icon on an expressive shape (a cookie, a clover...). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShapedIcon(
    @DrawableRes icon: Int,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    shape: Shape = MaterialShapes.Cookie9Sided.toShape(),
    busy: Boolean = false,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            LoadingIndicator(modifier = Modifier.size(size * 0.8f), color = content)
        } else {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}

/** A quick action: an icon on an expressive shape, a title and a line of detail. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ActionTile(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    shape: Shape = MaterialShapes.Cookie4Sided.toShape(),
    busy: Boolean = false,
    enabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    SectionCard(
        modifier = modifier,
        onClick = if (enabled && !busy) {
            {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            }
        } else null,
        contentPadding = PaddingValues(18.dp),
    ) {
        ShapedIcon(icon, container, content, shape = shape, busy = busy, size = 52.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMediumEmphasized,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A banner for something that needs fixing, with its Fix button. */
@Composable
fun FixBanner(
    title: String,
    text: String,
    fixLabel: String,
    onFix: () -> Unit,
    modifier: Modifier = Modifier,
    warning: Boolean = false,
    busy: Boolean = false,
) {
    val colors = LocalRebornColors.current
    val status = if (warning) colors.warn else colors.error
    val container = if (colors.dark) status.container else
        if (warning) status.container else MaterialTheme.colorScheme.errorContainer
    val onContainer = if (colors.dark) status.onContainer else
        if (warning) status.onContainer else MaterialTheme.colorScheme.onErrorContainer
    val haptics = LocalHapticFeedback.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = container,
        border = if (colors.dark) BorderStroke(1.dp, status.accent.copy(alpha = 0.35f)) else null,
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, end = 14.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(if (warning) R.drawable.ic_reborn_warning else R.drawable.ic_reborn_error),
                contentDescription = stringResource(if (warning) R.string.reborn_state_warn else R.string.reborn_state_error),
                tint = status.accent,
                modifier = Modifier.size(24.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleSmallEmphasized, color = onContainer)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = onContainer.copy(alpha = 0.86f))
            }
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onFix()
                },
                enabled = !busy,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = status.accent,
                    contentColor = status.onAccent,
                    // Busy keeps the accent: the in-button indicator says it is working
                    disabledContainerColor = status.accent,
                    disabledContentColor = status.onAccent,
                ),
                contentPadding = PaddingValues(horizontal = 18.dp),
            ) {
                BusyLabel(busy) { Text(fixLabel, maxLines = 1) }
            }
        }
    }
}

/**
 * A confirmation for actions that restart something. [destructive] (soft reboot, remove, undo
 * everything) gives the confirm button the error colours.
 */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    @DrawableRes icon: Int? = null,
    destructive: Boolean = false,
    /** Shown under [text]: what the action covers, for instance */
    body: (@Composable ColumnScope.() -> Unit)? = null,
) {
    RebornConfirmDialog(
        title = title,
        text = text,
        confirmLabel = confirmLabel,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        icon = icon,
        destructive = destructive,
        body = body,
    )
}
