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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import projekt.substratum.R
import projekt.substratum.common.platform.AmoledPreview
import projekt.substratum.common.platform.QsOptions

/** A wallpaper behind the panel, so a see-through shade reads as see-through. */
internal val Wallpaper = Brush.linearGradient(
    listOf(Color(0xFF1C2B52), Color(0xFF3B2156), Color(0xFF0D3B3C)),
)
private val AlbumArt = Brush.linearGradient(listOf(Color(0xFFFF8A3D), Color(0xFFE8407A), Color(0xFF6A3DE8)))
internal val QsText = Color(0xFFFCFCFF)

/**
 * One UI 9's blacked-out quick settings keep their shapes on black with a hairline: the tile
 * container (qs_tile_layout_container_bg, #000) has a qs_tile_container_bg_border_color edge, and
 * so does every round button that is off.
 */
internal val BlackoutEdge = Color(0xFF262626)

/** A hairline [BlackoutEdge] around a black part, so it does not float. */
internal fun Modifier.blackoutEdge(on: Boolean, shape: Shape, compact: Boolean = false): Modifier =
    if (on) border(if (compact) 0.75.dp else 1.dp, BlackoutEdge, shape) else this

/** A colour that would vanish on black (near-black, or mostly see-through) */
internal fun Color.nearBlack(): Boolean = alpha < 0.35f || luminance() < 0.02f

private val TileIcons = intArrayOf(
    R.drawable.ic_reborn_wifi, R.drawable.ic_reborn_bluetooth, R.drawable.ic_reborn_volume,
    R.drawable.ic_reborn_flashlight, R.drawable.ic_reborn_amoled, R.drawable.ic_reborn_airplane,
    R.drawable.ic_reborn_rotate, R.drawable.ic_reborn_location,
)
private val TilesOn = setOf(0, 1, 4, 6)

/**
 * One UI 9's quick settings panel, drawn: the panel, a 2×4 grid of tiles (some on), the
 * brightness bar and a media card. Every colour comes from [roles]; the tile rows are as tall as
 * [aspect] (QS row height, a fraction of the tile width) makes them.
 *
 * @param opaqueShade The AMOLED panel is solid (the module's shade option), else see-through
 * @param darkTracks The AMOLED bar track takes the rules' colour, else it stays stock
 * @param compact A small version without text, for the preset carousel
 */
@Composable
fun QsMock(
    roles: MockRoles,
    side: MockSide,
    modifier: Modifier = Modifier,
    aspect: Float = QsOptions.STOCK_ASPECT,
    opaqueShade: Boolean = true,
    darkTracks: Boolean = true,
    compact: Boolean = false,
) {
    val amoled = side == MockSide.Amoled
    val panelTarget = roles.color(AmoledPreview.QS_PANEL, side).let {
        if (amoled && !opaqueShade) it.copy(alpha = minOf(it.alpha, 0.72f)) else it
    }
    val panel = mockColor(panelTarget, "qsPanel")
    val tileOff = mockColor(roles.color(AmoledPreview.QS_TILE_OFF, side), "qsTileOff")
    val tileOn = mockColor(roles.color(AmoledPreview.QS_TILE_ON, side), "qsTileOn")
    val iconOff = mockColor(roles.color(AmoledPreview.QS_TILE_ICON_OFF, side), "qsIconOff")
    val iconOn = mockColor(roles.color(AmoledPreview.QS_TILE_ICON_ON, side), "qsIconOn")
    val track = mockColor(
        roles.color(AmoledPreview.QS_TRACK, if (amoled && darkTracks) side else MockSide.Stock), "qsTrack",
    )
    val fill = mockColor(roles.color(AmoledPreview.QS_FILL, side), "qsFill")
    val media = mockColor(roles.color(AmoledPreview.QS_MEDIA, side), "qsMedia")

    val pad = if (compact) 8.dp else 14.dp
    val gap = if (compact) 4.dp else 8.dp
    val rowAspect = aspect.coerceIn(0.3f, 1.5f)
    Box(
        modifier
            .clip(RoundedCornerShape(if (compact) 14.dp else 24.dp))
            .background(Wallpaper),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(panel)
                .padding(pad),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            if (compact) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Bar(0.32f, 5.dp, QsText.copy(alpha = 0.85f))
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(7.dp).clip(CircleShape).background(tileOff).blackoutEdge(amoled, CircleShape, true))
                    Spacer(Modifier.width(3.dp))
                    Box(Modifier.size(7.dp).clip(CircleShape).background(tileOff).blackoutEdge(amoled, CircleShape, true))
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.reborn_mock_time),
                            color = QsText,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 1,
                        )
                        Text(
                            stringResource(R.string.reborn_mock_date),
                            color = QsText.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    }
                    RoundIcon(R.drawable.ic_reborn_settings, tileOff, iconOff, 28.dp, edge = amoled)
                    Spacer(Modifier.width(6.dp))
                    RoundIcon(R.drawable.ic_reborn_power, tileOff, iconOff, 28.dp, edge = amoled)
                }
            }
            // The tile container: black with its hairline in the AMOLED look, invisible in stock
            // (the same padding on both sides, so the two halves of a before/after line up)
            val containerShape = RoundedCornerShape(if (compact) 10.dp else 20.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(containerShape)
                    .background(if (amoled) Color.Black else Color.Transparent)
                    .blackoutEdge(amoled, containerShape, compact)
                    .padding(if (compact) 3.dp else 6.dp),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                for (row in 0 until 2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        for (col in 0 until 4) {
                            val index = row * 4 + col
                            val on = index in TilesOn
                            Tile(
                                aspect = rowAspect,
                                container = if (on) tileOn else tileOff,
                                content = if (on) iconOn else iconOff,
                                icon = TileIcons[index],
                                compact = compact,
                                // Off round buttons keep an outline on black
                                edge = amoled && !on,
                            )
                        }
                    }
                }
            }
            // Brightness: a thick bar, filled part white with the sun on it
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(if (compact) 9.dp else 30.dp)
                    .clip(CircleShape)
                    .background(track),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.62f)
                        .clip(CircleShape)
                        .background(fill),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (!compact) {
                        Icon(
                            painterResource(R.drawable.ic_reborn_brightness),
                            contentDescription = null,
                            tint = Color(0xFF252528),
                            modifier = Modifier
                                .padding(start = 9.dp)
                                .size(16.dp),
                        )
                    }
                }
            }
            val mediaShape = RoundedCornerShape(if (compact) 8.dp else 20.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(mediaShape)
                    .background(media)
                    .blackoutEdge(amoled && media.nearBlack(), mediaShape, compact)
                    .padding(if (compact) 5.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(if (compact) 14.dp else 40.dp)
                        .clip(RoundedCornerShape(30))
                        .background(AlbumArt),
                )
                Spacer(Modifier.width(if (compact) 5.dp else 12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 1.dp)) {
                    if (compact) {
                        Bar(0.7f, 3.dp, QsText.copy(alpha = 0.85f))
                        Bar(0.45f, 3.dp, QsText.copy(alpha = 0.5f))
                    } else {
                        Text(
                            stringResource(R.string.reborn_mock_song),
                            color = QsText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            stringResource(R.string.reborn_mock_artist),
                            color = QsText.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                    }
                }
                Box(
                    Modifier
                        .size(if (compact) 12.dp else 32.dp)
                        .clip(CircleShape)
                        .background(fill),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!compact) {
                        Icon(
                            painterResource(R.drawable.ic_reborn_play),
                            contentDescription = null,
                            tint = Color(0xFF252528),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.Tile(
    aspect: Float,
    container: Color,
    content: Color,
    @DrawableRes icon: Int,
    compact: Boolean,
    edge: Boolean,
) {
    val shape = RoundedCornerShape(percent = if (compact) 30 else 32)
    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f / aspect)
            .clip(shape)
            .background(container)
            .blackoutEdge(edge, shape, compact),
        contentAlignment = Alignment.Center,
    ) {
        if (compact) {
            Box(
                Modifier
                    .fillMaxSize(0.34f)
                    .clip(CircleShape)
                    .background(content),
            )
        } else {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = content,
                modifier = Modifier.fillMaxSize(0.4f),
            )
        }
    }
}

@Composable
private fun RoundIcon(@DrawableRes icon: Int, container: Color, content: Color, size: Dp, edge: Boolean) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(container)
            .blackoutEdge(edge, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(size * 0.55f))
    }
}

/** A line of text, as a rounded bar (the compact mocks). */
@Composable
internal fun Bar(fraction: Float, height: Dp, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth(fraction)
            .height(height)
            .clip(CircleShape)
            .background(color),
    )
}
