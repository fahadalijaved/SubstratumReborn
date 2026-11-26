/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.components.mock

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import projekt.substratum.R
import projekt.substratum.common.platform.AmoledPreview
import projekt.substratum.reborn.components.design.SystemFont

/** One UI's settings icons keep their own colours in dark mode: they are not AMOLED's to change. */
private val IconBlue = Color(0xFF3E8BFF)
private val IconPurple = Color(0xFF9A6BFF)
private val IconGreen = Color(0xFF2FB36B)
private val IconAmber = Color(0xFFFFA42B)
private val Avatar = Brush.linearGradient(listOf(Color(0xFF7AA7FF), Color(0xFFB98AFF)))

private class SettingsColors(
    val card: Color,
    val divider: Color,
    val title: Color,
    val summary: Color,
    val switchOn: Color,
    val accent: Color,
)

/**
 * One UI's Settings, drawn: the page, a profile card and grouped cards of rows with dividers,
 * titles, summaries and a switch. Every colour that AMOLED changes comes from [roles].
 *
 * @param compact A small version with bars for text, for the preset carousel
 */
@Composable
fun SettingsMock(
    roles: MockRoles,
    side: MockSide,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) = SystemFont { SettingsMockBody(roles, side, modifier, compact) }

/** The mock itself, in the system font (see [SystemFont]). */
@Composable
private fun SettingsMockBody(
    roles: MockRoles,
    side: MockSide,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val page = mockColor(roles.color(AmoledPreview.SETTINGS_PAGE, side), "setPage")
    val colors = SettingsColors(
        card = mockColor(roles.color(AmoledPreview.SETTINGS_CARD, side), "setCard"),
        divider = mockColor(roles.color(AmoledPreview.SETTINGS_DIVIDER, side), "setDivider"),
        title = mockColor(roles.color(AmoledPreview.SETTINGS_TITLE, side), "setTitle"),
        summary = mockColor(roles.color(AmoledPreview.SETTINGS_SUMMARY, side), "setSummary"),
        switchOn = mockColor(roles.color(AmoledPreview.SETTINGS_SWITCH_ON, side), "setSwitch"),
        accent = mockColor(roles.color(AmoledPreview.SETTINGS_ACCENT, side), "setAccent"),
    )
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(if (compact) 14.dp else 24.dp))
            .background(page)
            .padding(horizontal = if (compact) 6.dp else 12.dp, vertical = if (compact) 8.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 10.dp),
    ) {
        if (compact) {
            Bar(0.45f, 6.dp, colors.title, Modifier.padding(start = 3.dp, bottom = 2.dp))
        } else {
            Text(
                stringResource(R.string.reborn_mock_settings),
                color = colors.title,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 6.dp, bottom = 2.dp),
            )
        }
        Group(colors, compact) {
            Row(
                modifier = Modifier.padding(if (compact) 6.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(if (compact) 14.dp else 36.dp)
                        .clip(CircleShape)
                        .background(Avatar),
                )
                Spacer(Modifier.width(if (compact) 5.dp else 12.dp))
                Lines(
                    colors, compact,
                    title = stringResource(R.string.reborn_mock_profile),
                    summary = stringResource(R.string.reborn_mock_account),
                )
            }
        }
        Group(colors, compact) {
            SettingRow(colors, compact, R.drawable.ic_reborn_wifi, IconBlue,
                stringResource(R.string.reborn_mock_connections), stringResource(R.string.reborn_mock_connections_text))
            Divider(colors, compact)
            SettingRow(colors, compact, R.drawable.ic_reborn_volume, IconPurple,
                stringResource(R.string.reborn_mock_sounds), stringResource(R.string.reborn_mock_sounds_text))
        }
        Group(colors, compact) {
            SettingRow(colors, compact, R.drawable.ic_mock_amoled, IconGreen,
                stringResource(R.string.reborn_mock_dark), stringResource(R.string.reborn_mock_dark_text),
                accentSummary = true, switch = true)
            if (!compact) {
                Divider(colors, compact)
                SettingRow(colors, compact, R.drawable.ic_reborn_brightness, IconAmber,
                    stringResource(R.string.reborn_mock_display), stringResource(R.string.reborn_mock_display_text))
            }
        }
    }
}

@Composable
private fun Group(colors: SettingsColors, compact: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (compact) 8.dp else 18.dp))
            .background(colors.card),
        content = content,
    )
}

@Composable
private fun Divider(colors: SettingsColors, compact: Boolean) {
    Box(
        Modifier
            .padding(start = if (compact) 25.dp else 52.dp, end = if (compact) 6.dp else 14.dp)
            .fillMaxWidth()
            .height(if (compact) 0.5.dp else 1.dp)
            .background(colors.divider),
    )
}

@Composable
private fun SettingRow(
    colors: SettingsColors,
    compact: Boolean,
    @DrawableRes icon: Int,
    iconColor: Color,
    title: String,
    summary: String,
    accentSummary: Boolean = false,
    switch: Boolean = false,
) {
    Row(
        modifier = Modifier.padding(horizontal = if (compact) 6.dp else 12.dp, vertical = if (compact) 5.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(if (compact) 13.dp else 28.dp)
                .clip(CircleShape)
                .background(iconColor),
            contentAlignment = Alignment.Center,
        ) {
            if (!compact) {
                Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.width(if (compact) 6.dp else 12.dp))
        Lines(colors, compact, title, summary, accentSummary, Modifier.weight(1f))
        if (switch) {
            Spacer(Modifier.width(if (compact) 4.dp else 10.dp))
            Box(
                Modifier
                    .size(width = if (compact) 16.dp else 36.dp, height = if (compact) 9.dp else 20.dp)
                    .clip(CircleShape)
                    .background(colors.switchOn),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    Modifier
                        .padding(if (compact) 1.5.dp else 3.dp)
                        .size(if (compact) 6.dp else 14.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                )
            }
        }
    }
}

@Composable
private fun Lines(
    colors: SettingsColors,
    compact: Boolean,
    title: String,
    summary: String,
    accentSummary: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val summaryColor = if (accentSummary) colors.accent else colors.summary
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 1.dp)) {
        if (compact) {
            Bar(0.8f, 3.dp, colors.title)
            Bar(0.55f, 3.dp, summaryColor)
        } else {
            Text(title, color = colors.title, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(summary, color = summaryColor, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
