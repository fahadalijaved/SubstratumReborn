/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.themes

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import kotlinx.coroutines.withContext
import projekt.substratum.R
import projekt.substratum.common.platform.AmoledPreview
import projekt.substratum.reborn.components.mock.MockRoles
import projekt.substratum.reborn.components.mock.mockColor
import projekt.substratum.reborn.nav.SharedKeys
import projekt.substratum.reborn.nav.themeShared
import kotlin.math.abs

/** The colours a tile's mini Quick Settings is painted in. */
@Immutable
class MiniColors(
    val page: Color,
    val tileOff: Color,
    val iconOff: Color,
    val tileOn: Color,
    val iconOn: Color,
    val track: Color,
    val fill: Color,
    val shape: Color,
)

/**
 * A tile's colours: the look's own (engine themes, and the roles a classic theme's overlays
 * change), else colours from the theme's icon ([tint]); One UI's stock ones while neither is read.
 */
internal fun miniColors(looks: ThemeLooks?, tint: ThemeTint?): MiniColors {
    val roles = looks?.shown?.roles
    val classic = looks?.drawnFromTheme == true
    fun own(role: String): Color? {
        if (roles == null) return null
        if (!classic) return Color(roles.after(role))
        // A classic theme's role counts only when its overlays change it
        val pair = roles.raw(role) ?: return null
        if (pair.size < 2 || pair[1] == pair[0]) return null
        return Color(pair[1])
    }
    val stock = MockRoles.Empty
    fun stockOf(role: String) = Color(stock.after(role))
    val light = tint?.let { Color(it.light) }
    val deep = tint?.let { Color(it.deep) }
    val useTint = light != null && deep != null && (roles == null || classic)

    val page = (own(AmoledPreview.QS_PANEL) ?: own(AmoledPreview.SETTINGS_PAGE)
        ?: if (useTint) lerp(deep!!, Color.Black, 0.55f) else stockOf(AmoledPreview.QS_PANEL))
        .compositeOver(Color.Black)
    val tileOff = (own(AmoledPreview.QS_TILE_OFF) ?: own(AmoledPreview.SETTINGS_CARD)
        ?: if (useTint) lerp(deep!!, Color.White, 0.12f) else stockOf(AmoledPreview.QS_TILE_OFF))
        .compositeOver(page)
    val tileOn = (own(AmoledPreview.QS_TILE_ON) ?: own(AmoledPreview.SETTINGS_ACCENT)
        ?: if (useTint) light!! else stockOf(AmoledPreview.QS_TILE_ON))
        .compositeOver(page)
    val iconOn = own(AmoledPreview.QS_TILE_ICON_ON)?.compositeOver(tileOn) ?: contrastOn(tileOn)
    val iconOff = own(AmoledPreview.QS_TILE_ICON_OFF)?.compositeOver(tileOff) ?: contrastOn(tileOff).copy(alpha = 0.8f)
    val track = (own(AmoledPreview.QS_TRACK) ?: tileOff).compositeOver(page)
    val fill = (own(AmoledPreview.QS_FILL) ?: tileOn).compositeOver(page)
    val shape = (own(AmoledPreview.SETTINGS_ACCENT) ?: if (useTint) light!! else tileOn)
        .compositeOver(page)
    return MiniColors(page, tileOff, iconOff, tileOn, iconOn, track, fill, shape)
}

internal fun contrastOn(color: Color): Color = if (color.luminance() > 0.45f) Color(0xFF1B1B1F) else Color(0xFFF4F1F6)

/** The identity shapes of installed themes, one per package (the built-ins have their own). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val IdentityShapes: List<RoundedPolygon> by lazy {
    listOf(
        MaterialShapes.Sunny, MaterialShapes.Clover4Leaf, MaterialShapes.Cookie6Sided, MaterialShapes.Flower,
        MaterialShapes.Arch, MaterialShapes.SoftBurst, MaterialShapes.Pentagon, MaterialShapes.Cookie12Sided,
        MaterialShapes.Clover8Leaf, MaterialShapes.Cookie7Sided,
    )
}

/**
 * A theme's identity shape, the one its card, Home's Try next and its art wear: Cookie9Sided for
 * the built-in Reborn AMOLED, else one chosen by package, so a theme always gets the same one.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun identityPolygon(theme: ThemeItem): RoundedPolygon =
    if (theme.builtIn) MaterialShapes.Cookie9Sided
    else IdentityShapes[Math.floorMod(theme.pkg.hashCode(), IdentityShapes.size)]

/** [MiniColors.shape], or (when it is the on tiles' colour) the off tiles': a second accent beside the first. */
internal fun MiniColors.secondAccent(): Color = if (shape == tileOn) tileOff else shape

/** The tile preview's corners, inside the tile's 24 dp */
val MiniPreviewShape = RoundedCornerShape(18.dp)

/** The mini Quick Settings' width (its slider and the 2 × 2 tiles) */
private val MiniQsWidth = 104.dp

/** Too close to the page to read as a tile (pure black tiles on a black page): give it a hairline. */
private fun needsLine(part: Color, page: Color): Boolean = abs(part.luminance() - page.luminance()) < 0.015f

/**
 * A theme's card preview (92 dp): a mini Quick Settings (brightness slider and four tiles, three
 * on) painted in the theme's colours, its identity shape peeking out at the bottom right in its
 * second accent, and a small "Applied" badge at the top right when it is in use (the slider ends
 * short of it). The edge is light on a dark theme and dark on a light one. [pressed] turns the
 * shape and grows it a little, on the expressive spring.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ThemeMiniPreview(
    theme: ThemeItem,
    colors: MiniColors,
    modifier: Modifier = Modifier,
    pressed: Boolean = false,
) {
    val page = mockColor(colors.page, "miniPage")
    val tileOff = mockColor(colors.tileOff, "miniOff")
    val tileOn = mockColor(colors.tileOn, "miniOn")
    val iconOff = mockColor(colors.iconOff, "miniIconOff")
    val iconOn = mockColor(colors.iconOn, "miniIconOn")
    val track = mockColor(colors.track, "miniTrack")
    val fill = mockColor(colors.fill, "miniFill")
    val shape = mockColor(colors.shape, "miniShape")
    val lightTheme = colors.page.luminance() > 0.5f
    val edge = if (lightTheme) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.07f)
    val line = if (lightTheme) Color.Black.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.12f)
    val offLine = if (needsLine(colors.tileOff, colors.page)) line else null
    val trackLine = if (needsLine(colors.track, colors.page)) line else null
    val identity = remember(theme.id) { identityPolygon(theme) }.toShape()
    val motion = MaterialTheme.motionScheme
    val turn by animateFloatAsState(if (pressed) 36f else 0f, motion.defaultSpatialSpec(), label = "miniTurn")
    val grow by animateFloatAsState(if (pressed) 1.08f else 1f, motion.defaultSpatialSpec(), label = "miniGrow")
    Box(
        modifier
            .themeShared(SharedKeys.art(theme.id))
            .fillMaxWidth()
            .height(92.dp)
            .clip(MiniPreviewShape)
            .background(page)
            .border(1.dp, edge, MiniPreviewShape),
    ) {
        // The theme's identity shape, in its second accent, peeking out of the corner
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 10.dp, y = 12.dp)
                .size(76.dp)
                .graphicsLayer {
                    rotationZ = turn
                    scaleX = grow
                    scaleY = grow
                }
                .clip(identity)
                .background(shape),
        )
        Column(
            Modifier
                .padding(start = 9.dp, top = 8.dp)
                .width(MiniQsWidth),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            // The brightness slider, ending short of the Applied badge
            Box(
                Modifier
                    .padding(end = if (theme.inUse) 24.dp else 0.dp)
                    .fillMaxWidth()
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(track)
                    .then(if (trackLine != null) Modifier.border(1.dp, trackLine, RoundedCornerShape(8.dp)) else Modifier),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(0.64f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(fill),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_reborn_brightness),
                        contentDescription = null,
                        tint = contrastOn(fill),
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(11.dp),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    MiniTile(R.drawable.ic_reborn_wifi, tileOn, iconOn, null, Modifier.weight(1f))
                    MiniTile(R.drawable.ic_reborn_bluetooth, tileOn, iconOn, null, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    MiniTile(R.drawable.ic_reborn_flashlight, tileOff, iconOff, offLine, Modifier.weight(1f))
                    MiniTile(R.drawable.ic_mock_amoled, tileOn, iconOn, null, Modifier.weight(1f))
                }
            }
        }
        if (theme.inUse) {
            AppliedBadge(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 6.dp),
            )
        }
    }
}

/** One mini Quick Settings tile: its icon and a short bar for its label; [line] a hairline edge. */
@Composable
private fun MiniTile(@DrawableRes icon: Int, container: Color, content: Color, line: Color?, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(container)
            .then(if (line != null) Modifier.border(1.dp, line, RoundedCornerShape(13.dp)) else Modifier)
            .padding(start = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Box(
            Modifier
                .width(16.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(content.copy(alpha = content.alpha * 0.45f)),
        )
    }
}

/** The small "Applied" pill at a card preview's top right, lifted by a soft shadow. */
@Composable
fun AppliedBadge(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .height(22.dp)
            .shadow(3.dp, CircleShape)
            .background(scheme.primary, CircleShape)
            .padding(start = 5.dp, end = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_reborn_check), contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(3.dp))
        Text(
            stringResource(R.string.reborn_theme_applied_badge),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = scheme.onPrimary,
            maxLines = 1,
        )
    }
}

/**
 * [theme]'s icon colours (read on the theme image loader), for a classic theme whose overlays name
 * too few colours of the preview; null for themes that do not need them.
 */
@Composable
fun rememberThemeTint(theme: ThemeItem, needed: Boolean): State<ThemeTint?> {
    val context = LocalContext.current
    return produceState(if (needed) cachedTint(theme) else null, theme.pkg, theme.updated, needed) {
        if (!needed) {
            value = null
            return@produceState
        }
        value = cachedTint(theme) ?: withContext(heroLoader) { loadTint(context, theme) }
    }
}
