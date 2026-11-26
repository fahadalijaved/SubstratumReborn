/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.themes

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import kotlinx.coroutines.withContext
import projekt.substratum.R
import projekt.substratum.Substratum
import projekt.substratum.common.platform.AmoledPresets
import projekt.substratum.reborn.amoled.StudioState
import projekt.substratum.reborn.components.AppIcon
import projekt.substratum.reborn.components.PaletteDots
import projekt.substratum.reborn.components.PaletteRolesShort
import projekt.substratum.reborn.components.PaletteStrip
import projekt.substratum.reborn.components.TagChip
import projekt.substratum.reborn.components.mock.MockRoles
import projekt.substratum.reborn.theme.LocalRebornColors
import projekt.substratum.reborn.components.design.ThemeAvatar
import projekt.substratum.reborn.components.design.pressScale
import projekt.substratum.reborn.components.design.pressShape
import projekt.substratum.reborn.nav.SharedKeys
import projekt.substratum.reborn.nav.themeShared

/** The expressive shape of a theme's kind (its avatar, and its page's hero badge). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun kindPolygon(theme: ThemeItem): RoundedPolygon = when (theme.kind) {
    ThemeKind.BUILT_IN -> BuiltInThemes.of(theme.id)?.polygon ?: MaterialShapes.Clover4Leaf
    ThemeKind.ENGINE -> MaterialShapes.Clover4Leaf
    ThemeKind.CLASSIC -> MaterialShapes.Cookie6Sided
    ThemeKind.HYBRID -> MaterialShapes.Clover8Leaf
}

/**
 * A compact theme tile for the Themes grid (two columns on a phone, about 150 dp tall): a 92 dp
 * preview (a mini Quick Settings in the theme's colours, its kind's shape and avatar, a small
 * "Applied" badge when it is in use), then the name with its palette dots and one quiet line (what
 * it is, and who made it). In use, the tile has a 2 dp primary ring. The preview, name and avatar
 * are shared with the theme's page, so a tap morphs into it. Corners spring tighter and the tile
 * scales down while pressed.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThemeTile(
    theme: ThemeItem,
    onOpen: () -> Unit,
    onLongPress: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val shape = pressShape(interaction, rest = 24.dp, pressed = 18.dp)
    val scale = pressScale(interaction, pressed = 0.96f)
    val inUse = stringResource(R.string.reborn_theme_in_use)
    val looks by rememberThemeLooks(theme)
    // A classic theme that names too few of the preview's colours: its icon's colours instead
    val tint by rememberThemeTint(theme, needed = !theme.kind.engine && looks?.previewable != true)
    val mini = remember(looks, tint) { miniColors(looks, tint) }
    val card = scheme.surfaceContainerLow
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
                indication = ripple(),
                role = Role.Button,
                onClick = onOpen,
                onLongClick = onLongPress?.let { long ->
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        long()
                    }
                },
            )
            .semantics { if (theme.inUse) stateDescription = inUse },
        shape = shape,
        color = card,
        border = if (theme.inUse) BorderStroke(2.dp, scheme.primary) else null,
    ) {
        Column(Modifier.padding(6.dp)) {
            ThemeMiniPreview(theme, mini)
            Row(
                Modifier.padding(start = 6.dp, end = 4.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    theme.name,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .themeShared(SharedKeys.name(theme.id)),
                )
                Spacer(Modifier.width(6.dp))
                // The look's palette once the theme has enough colours of its own, else the tile's
                val shown = looks?.takeIf { it.previewable }?.shown
                if (shown != null) {
                    PaletteStrip(
                        shown.roles,
                        Modifier.themeShared(SharedKeys.palette(theme.id)),
                        ring = card,
                        dot = 10.dp,
                        shown = PaletteRolesShort,
                    )
                } else {
                    PaletteDots(listOf(mini.page, mini.tileOff, mini.tileOn), ring = card, dot = 10.dp)
                }
            }
            Text(
                tileLine(theme),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 2.dp),
            )
        }
    }
}

/** A theme look's [PaletteStrip] on a pill of the card colour, so it reads over any art. */
@Composable
fun PalettePill(
    roles: MockRoles,
    modifier: Modifier = Modifier,
    ring: Color = LocalRebornColors.current.card,
) {
    Box(
        modifier
            .background(ring, CircleShape)
            .padding(horizontal = 3.dp, vertical = 2.dp),
    ) {
        PaletteStrip(roles, ring = ring)
    }
}

/**
 * A tile's quiet line: what the theme is and who made it ("Classic · Jane"); for namesakes (two
 * themes with one name), the package and version that tell them apart.
 */
@Composable
private fun tileLine(theme: ThemeItem): String {
    if (theme.builtIn) return stringResource(R.string.reborn_builtin_byline)
    if (theme.namesake) return stringResource(R.string.reborn_theme_package_version, theme.pkg, theme.version ?: "–")
    val kind = stringResource(
        when (theme.kind) {
            ThemeKind.ENGINE -> R.string.reborn_theme_chip_rules
            ThemeKind.HYBRID -> R.string.reborn_theme_kind_hybrid
            else -> R.string.reborn_themes_filter_classic
        },
    )
    return if (theme.author.isBlank()) kind else stringResource(R.string.reborn_theme_tile_line, kind, theme.author)
}

/** The theme's icon on its kind's shape: the app icon, or for a built-in its own glyph. */
@Composable
fun KindAvatar(theme: ThemeItem, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val scheme = MaterialTheme.colorScheme
    ThemeAvatar(key = theme.id.key, modifier = modifier, size = size, polygon = kindPolygon(theme)) {
        if (theme.builtIn) {
            Icon(
                painterResource(R.drawable.ic_reborn_amoled),
                contentDescription = null,
                tint = scheme.onPrimaryContainer,
                modifier = Modifier.size(size * 0.5f),
            )
        } else {
            AppIcon(theme.pkg, size = size, shape = RectangleShape)
        }
    }
}

@Composable
internal fun byline(theme: ThemeItem): String = when {
    theme.builtIn -> stringResource(R.string.reborn_builtin_byline)
    theme.version.isNullOrBlank() -> stringResource(R.string.reborn_theme_by, theme.author)
    else -> stringResource(R.string.reborn_theme_by_version, theme.author, theme.version)
}

/** What the theme is ("Built in", "Reborn engine", "Classic overlays"), then its extras. */
@Composable
internal fun ThemeChips(theme: ThemeItem) {
    val scheme = MaterialTheme.colorScheme
    if (theme.builtIn) {
        TagChip(stringResource(R.string.reborn_theme_chip_builtin), R.drawable.ic_reborn_check, scheme.tertiaryContainer, scheme.onTertiaryContainer)
    }
    if (theme.rules) {
        TagChip(stringResource(R.string.reborn_theme_chip_rules), R.drawable.ic_reborn_engine, scheme.primaryContainer, scheme.onPrimaryContainer)
    }
    if (theme.overlays) {
        TagChip(stringResource(R.string.reborn_theme_chip_overlays), R.drawable.ic_reborn_layers, scheme.secondaryContainer, scheme.onSecondaryContainer)
    }
    if (theme.wallpapers) {
        TagChip(stringResource(R.string.reborn_theme_chip_wallpapers), R.drawable.ic_reborn_wallpaper, scheme.tertiaryContainer, scheme.onTertiaryContainer)
    }
}

/**
 * A theme's hero: drawn for a built-in, else its own image or one drawn in its icon's colours.
 * [compact] (a grid tile) leaves out the built-in's large shape: the tile's avatar carries it.
 */
@Composable
fun ThemeHero(theme: ThemeItem, modifier: Modifier = Modifier, compact: Boolean = false) {
    if (theme.builtIn) BuiltInHero(theme, modifier, compact) else HeroArt(theme, modifier)
}

/** The built-in card's art: the saved accent (One UI blue when none) over black, with its shape. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BuiltInHero(theme: ThemeItem, modifier: Modifier = Modifier, compact: Boolean = false) {
    val context = LocalContext.current
    // Read again when the look changes (an accent chosen in the Studio)
    val look by StudioState.look.collectAsState()
    val light = remember(theme.updated, look) {
        runCatching {
            val accent = AmoledPresets.look(Substratum.getPreferences()).accent
            AmoledPresets.resolveAccent(accent, AmoledPresets.monetAccent(context))
        }.getOrNull() ?: BuiltInThemes.DEFAULT_LIGHT
    }
    val accent = Color(light)
    Box(modifier) {
        DrawnHero(accent, Color(BuiltInThemes.DEFAULT_DEEP))
        if (!compact) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 20.dp, end = 24.dp)
                    .size(76.dp)
                    .clip(kindPolygon(theme).toShape())
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_reborn_amoled),
                    contentDescription = null,
                    tint = if (accent.luminance() > 0.5f) Color.Black else Color.White,
                    modifier = Modifier.size(36.dp),
                )
            }
        }
    }
}

/** The theme's hero image, or a drawn one in its icon's colours, faded in once read. */
@Composable
fun HeroArt(theme: ThemeItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth.coerceIn(1, 1440)
        val hero by produceState(cachedHero(theme, width), theme.pkg, theme.updated, width) {
            // Also after an update of the theme or another width: the old hero stays meanwhile
            value = cachedHero(theme, width) ?: withContext(heroLoader) { loadHero(context, theme, width) }
        }
        Crossfade(targetState = hero, animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(), label = "hero") { h ->
            when {
                h == null -> Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
                h.image != null -> Image(
                    bitmap = h.image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> DrawnHero(Color(h.light), Color(h.deep))
            }
        }
    }
}

/** For themes without a hero image: their colours over expressive shapes. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DrawnHero(light: Color, deep: Color) {
    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Brush.linearGradient(listOf(deep, Color.Black))),
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 56.dp, y = (-48).dp)
                .size(240.dp)
                .rotate(18f)
                .clip(MaterialShapes.Cookie9Sided.toShape())
                .background(light.copy(alpha = 0.55f)),
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-36).dp, y = (-20).dp)
                .size(150.dp)
                .rotate(-12f)
                .clip(MaterialShapes.Clover4Leaf.toShape())
                .background(light.copy(alpha = 0.22f)),
        )
        Box(
            Modifier
                .align(Alignment.Center)
                .offset(x = 40.dp, y = 6.dp)
                .size(72.dp)
                .clip(MaterialShapes.Sunny.toShape())
                .background(light.copy(alpha = 0.35f)),
        )
    }
}
