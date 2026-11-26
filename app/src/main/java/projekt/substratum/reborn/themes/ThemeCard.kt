/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.themes

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import kotlinx.coroutines.withContext
import projekt.substratum.R
import projekt.substratum.Substratum
import projekt.substratum.common.platform.AmoledPresets
import projekt.substratum.reborn.components.AppIcon
import projekt.substratum.reborn.components.ChipState
import projekt.substratum.reborn.components.StatusChip
import projekt.substratum.reborn.components.TagChip
import projekt.substratum.reborn.components.design.ThemeAvatar
import projekt.substratum.reborn.components.design.ThemeCard
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
 * A full-width theme card: the hero (16:9) with a scrim, its avatar, name and byline, then the
 * kind chips. The art, name and avatar are shared with the theme's page, so a tap morphs into it.
 */
@Composable
fun ThemeItemCard(
    theme: ThemeItem,
    onOpen: () -> Unit,
    onLongPress: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    ThemeCard(
        name = theme.name,
        byline = byline(theme),
        onClick = onOpen,
        onLongClick = onLongPress,
        modifier = modifier,
        artModifier = Modifier.themeShared(SharedKeys.art(theme.id)),
        nameModifier = Modifier.themeShared(SharedKeys.name(theme.id)),
        inUse = theme.inUse,
        // Same name as another installed theme: told apart by package and version
        supporting = if (theme.namesake) {
            stringResource(R.string.reborn_theme_package_version, theme.pkg, theme.version ?: "–")
        } else null,
        avatar = { KindAvatar(theme, Modifier.themeShared(SharedKeys.avatar(theme.id))) },
        chips = {
            if (theme.inUse) StatusChip(stringResource(R.string.reborn_theme_in_use), ChipState.OK)
            ThemeChips(theme)
        },
    ) {
        ThemeHero(theme, Modifier.fillMaxSize())
    }
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

/** A theme's hero: drawn for a built-in, else its own image or one drawn in its icon's colours. */
@Composable
fun ThemeHero(theme: ThemeItem, modifier: Modifier = Modifier) {
    if (theme.builtIn) BuiltInHero(theme, modifier) else HeroArt(theme, modifier)
}

/** The built-in card's art: the saved accent (One UI blue when none) over black, with its shape. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BuiltInHero(theme: ThemeItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val light = remember(theme.updated) {
        runCatching {
            val accent = AmoledPresets.look(Substratum.getPreferences()).accent
            AmoledPresets.resolveAccent(accent, AmoledPresets.monetAccent(context))
        }.getOrNull() ?: BuiltInThemes.DEFAULT_LIGHT
    }
    val accent = Color(light)
    Box(modifier) {
        DrawnHero(accent, Color(BuiltInThemes.DEFAULT_DEEP))
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
