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
import androidx.compose.foundation.layout.fillMaxHeight
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

/** One UI's dark text: AMOLED never changes text */
internal val MockTitle = Color(0xFFFCFCFF)
internal val MockSummary = Color(0xFFA3A3A7)
private val FolderBlue = Color(0xFF3E8BFF)
private val FolderGreen = Color(0xFF2FB36B)
private val FolderPurple = Color(0xFF9A6BFF)
private val FolderAmber = Color(0xFFFFA42B)
private val ListDivider = Color(0x1FFFFFFF)

/**
 * An ordinary app on the framework's dark theme, drawn: its page, a storage card with a bar in
 * the accent, and a card of folders. The page and card colours are the framework's own
 * ([AmoledPreview.LIST_PAGE], [AmoledPreview.LIST_ROW]); the accent is One UI's.
 */
@Composable
fun ListMock(
    roles: MockRoles,
    side: MockSide,
    modifier: Modifier = Modifier,
) = SystemFont { ListMockBody(roles, side, modifier) }

/** The mock itself, in the system font (see [SystemFont]). */
@Composable
private fun ListMockBody(
    roles: MockRoles,
    side: MockSide,
    modifier: Modifier = Modifier,
) {
    val page = mockColor(roles.color(AmoledPreview.LIST_PAGE, side), "listPage")
    val card = mockColor(roles.color(AmoledPreview.LIST_ROW, side), "listRow")
    val accent = mockColor(roles.color(AmoledPreview.SETTINGS_SWITCH_ON, side), "listAccent")
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(page)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.reborn_mock_files),
                color = MockTitle,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painterResource(R.drawable.ic_mock_search),
                contentDescription = null,
                tint = MockSummary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(14.dp))
            Icon(
                painterResource(R.drawable.ic_mock_more),
                contentDescription = null,
                tint = MockSummary,
                modifier = Modifier.size(20.dp),
            )
        }
        ListCard(card) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.reborn_mock_storage),
                    color = MockTitle,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(Color(0x26FFFFFF)),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.32f)
                            .clip(CircleShape)
                            .background(accent),
                    )
                }
                Text(
                    stringResource(R.string.reborn_mock_storage_text),
                    color = MockSummary,
                    fontSize = 11.sp,
                )
            }
        }
        ListCard(card) {
            Folder(R.drawable.ic_mock_file, FolderBlue,
                stringResource(R.string.reborn_mock_documents), stringResource(R.string.reborn_mock_documents_text))
            FolderDivider()
            Folder(R.drawable.ic_mock_download, FolderGreen,
                stringResource(R.string.reborn_mock_downloads), stringResource(R.string.reborn_mock_downloads_text))
            FolderDivider()
            Folder(R.drawable.ic_reborn_volume, FolderPurple,
                stringResource(R.string.reborn_mock_music), stringResource(R.string.reborn_mock_music_text))
            FolderDivider()
            Folder(R.drawable.ic_mock_wallpaper, FolderAmber,
                stringResource(R.string.reborn_mock_pictures), stringResource(R.string.reborn_mock_pictures_text))
        }
    }
}

@Composable
private fun ListCard(color: Color, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(color),
        content = content,
    )
}

@Composable
private fun FolderDivider() {
    Box(
        Modifier
            .padding(start = 52.dp, end = 14.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(ListDivider),
    )
}

@Composable
private fun Folder(@DrawableRes icon: Int, color: Color, title: String, summary: String) {
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, color = MockTitle, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(summary, color = MockSummary, fontSize = 11.sp, maxLines = 1)
        }
    }
}
