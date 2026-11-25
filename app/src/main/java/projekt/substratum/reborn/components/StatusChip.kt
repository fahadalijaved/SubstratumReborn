/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import projekt.substratum.R
import projekt.substratum.reborn.theme.LocalRebornColors
import projekt.substratum.reborn.theme.StatusColors

/** Every state has its own icon as well as its colour: never colour alone. */
enum class ChipState(@DrawableRes val icon: Int, @StringRes val label: Int) {
    OK(R.drawable.ic_reborn_ok, R.string.reborn_state_ok),
    Warn(R.drawable.ic_reborn_warning, R.string.reborn_state_warn),
    Error(R.drawable.ic_reborn_error, R.string.reborn_state_error),
    Unknown(R.drawable.ic_reborn_unknown, R.string.reborn_state_unknown),
    Off(R.drawable.ic_reborn_off, R.string.reborn_state_off),
}

@Composable
fun statusColors(state: ChipState): StatusColors {
    val colors = LocalRebornColors.current
    return when (state) {
        ChipState.OK -> colors.ok
        ChipState.Warn -> colors.warn
        ChipState.Error -> colors.error
        ChipState.Unknown, ChipState.Off -> colors.unknown
    }
}

@Composable
fun StatusChip(text: String, state: ChipState, modifier: Modifier = Modifier) {
    val colors = statusColors(state)
    val container by animateColorAsState(colors.container, label = "chip")
    val accent by animateColorAsState(colors.accent, label = "chipAccent")
    val stateLabel = stringResource(state.label)
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(container)
            .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$stateLabel: $text" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(state.icon),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = colors.onContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
