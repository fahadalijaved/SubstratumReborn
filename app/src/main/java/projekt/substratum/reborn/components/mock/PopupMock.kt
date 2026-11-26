/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.components.mock

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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

private val UpdateBlue = Color(0xFF3E8BFF)
private val WeatherAmber = Color(0xFFFFA42B)

/**
 * Notifications and a popup menu, drawn: two notification cards over the wallpaper, then an app
 * page with its menu open. The notification card is System UI's
 * ([AmoledPreview.NOTIF_BG]); the menu is One UI's popup with its outline
 * ([AmoledPreview.POPUP_BG], [AmoledPreview.POPUP_OUTLINE]) over a Settings-style page.
 */
@Composable
fun PopupMock(
    roles: MockRoles,
    side: MockSide,
    modifier: Modifier = Modifier,
) = SystemFont { PopupMockBody(roles, side, modifier) }

/** The mock itself, in the system font (see [SystemFont]). */
@Composable
private fun PopupMockBody(
    roles: MockRoles,
    side: MockSide,
    modifier: Modifier = Modifier,
) {
    val notification = mockColor(roles.color(AmoledPreview.NOTIF_BG, side), "notifBg")
    val popup = mockColor(roles.color(AmoledPreview.POPUP_BG, side), "popupBg")
    val outline = mockColor(roles.color(AmoledPreview.POPUP_OUTLINE, side), "popupOutline")
    val page = mockColor(roles.color(AmoledPreview.SETTINGS_PAGE, side), "popupPage")
    val card = mockColor(roles.color(AmoledPreview.SETTINGS_CARD, side), "popupCard")
    Column(modifier.clip(RoundedCornerShape(24.dp))) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Wallpaper)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Notification(
                background = notification,
                icon = R.drawable.ic_mock_refresh,
                iconColor = UpdateBlue,
                app = stringResource(R.string.reborn_mock_update_app),
                text = stringResource(R.string.reborn_mock_update_text),
            )
            Notification(
                background = notification,
                icon = R.drawable.ic_reborn_brightness,
                iconColor = WeatherAmber,
                app = stringResource(R.string.reborn_mock_weather_app),
                text = stringResource(R.string.reborn_mock_weather_text),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .background(page)
                .padding(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp)) {
                    Text(
                        stringResource(R.string.reborn_mock_notes),
                        color = MockTitle,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        painterResource(R.drawable.ic_mock_more),
                        contentDescription = null,
                        tint = MockTitle,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Note(card, stringResource(R.string.reborn_mock_note_one), stringResource(R.string.reborn_mock_note_one_text))
                Note(card, stringResource(R.string.reborn_mock_note_two), stringResource(R.string.reborn_mock_note_two_text))
            }
            // The menu the ⋮ button opened
            val menuShape = RoundedCornerShape(18.dp)
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 26.dp)
                    .width(150.dp)
                    .clip(menuShape)
                    .background(popup)
                    .border(1.dp, outline, menuShape)
                    .padding(vertical = 6.dp),
            ) {
                MenuItem(stringResource(R.string.reborn_mock_menu_select))
                MenuItem(stringResource(R.string.reborn_mock_menu_share))
                MenuItem(stringResource(R.string.reborn_mock_menu_settings))
            }
        }
    }
}

@Composable
private fun Notification(background: Color, @DrawableRes icon: Int, iconColor: Color, app: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(iconColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(app, color = MockSummary, fontSize = 11.sp, maxLines = 1)
            Text(text, color = QsText, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Note(card: Color, title: String, text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(card)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, color = MockTitle, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Text(text, color = MockSummary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun MenuItem(text: String) {
    Text(
        text,
        color = MockTitle,
        fontSize = 13.sp,
        maxLines = 1,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
