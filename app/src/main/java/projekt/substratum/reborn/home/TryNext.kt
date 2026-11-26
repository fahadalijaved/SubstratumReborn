/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.CarouselItemScope
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.ripple
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import projekt.substratum.reborn.components.design.RebornRounded
import projekt.substratum.reborn.components.design.pressScale
import projekt.substratum.reborn.nav.ThemeId
import projekt.substratum.reborn.themes.ThemeItem
import projekt.substratum.reborn.themes.contrastOn
import projekt.substratum.reborn.themes.identityPolygon
import projekt.substratum.reborn.themes.miniColors
import projekt.substratum.reborn.themes.rememberThemeLooks
import projekt.substratum.reborn.themes.rememberThemeTint
import projekt.substratum.reborn.themes.secondAccent
import projekt.substratum.reborn.themes.themeByline

/**
 * Try next: the other themes as a multi-browse carousel (184 dp tall, a 220 dp lead item, 8 dp
 * apart, extraLarge mask). Each is drawn in the theme's own colours: its page, its identity shape
 * in its accent bleeding off the bottom right, a dot in its second accent, and on the open item
 * its name and "Author · Kind".
 */
@Composable
internal fun TryNext(themes: List<ThemeItem>, onOpenTheme: (ThemeId) -> Unit, modifier: Modifier = Modifier) {
    val state = rememberCarouselState { themes.size }
    HorizontalMultiBrowseCarousel(
        state = state,
        preferredItemWidth = 220.dp,
        itemSpacing = 8.dp,
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(184.dp),
    ) { i ->
        val theme = themes[i]
        TryNextItem(theme) { onOpenTheme(theme.id) }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CarouselItemScope.TryNextItem(theme: ThemeItem, onOpen: () -> Unit) {
    val looks by rememberThemeLooks(theme)
    // A classic theme that names too few of the preview's colours: its icon's colours instead
    val tint by rememberThemeTint(theme, needed = !theme.kind.engine && looks?.previewable != true)
    val colors = remember(looks, tint) { miniColors(looks, tint) }
    val onPage = contrastOn(colors.page)
    val identity = remember(theme.id) { identityPolygon(theme) }.toShape()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val motion = MaterialTheme.motionScheme
    val turn by animateFloatAsState(if (pressed) 30f else 0f, motion.defaultSpatialSpec(), label = "artTurn")
    val grow by animateFloatAsState(if (pressed) 1.05f else 1f, motion.defaultSpatialSpec(), label = "artGrow")
    val scale = pressScale(interaction, pressed = 0.97f)
    val byline = themeByline(theme, looks)
    val info = carouselItemDrawInfo
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .maskClip(MaterialTheme.shapes.extraLarge)
            .background(colors.page)
            .clickable(interactionSource = interaction, indication = ripple(), role = Role.Button, onClick = onOpen)
            .semantics { contentDescription = theme.name + ", " + byline },
    ) {
        // The identity shape keeps to the visible part's bottom end as the item opens and closes
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = info.maskRect.right - size.width },
        ) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 28.dp, y = 40.dp)
                    .size(156.dp)
                    .graphicsLayer {
                        rotationZ = turn
                        scaleX = grow
                        scaleY = grow
                    }
                    .clip(identity)
                    .background(colors.tileOn),
            )
        }
        // The dot and the caption keep to its start; the caption shows as the item opens
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = info.maskRect.left
                },
        ) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(20.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.secondAccent()),
            )
            Column(
                Modifier
                    .padding(start = 20.dp, top = 18.dp, end = 16.dp)
                    .graphicsLayer {
                        val range = info.maxSize - info.minSize
                        alpha = if (range <= 0f) 1f else ((info.size - info.minSize) / range * 2f - 1f).coerceIn(0f, 1f)
                    },
            ) {
                Text(
                    theme.name,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = RebornRounded,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 22.sp,
                        lineHeight = 28.sp,
                        letterSpacing = (-0.2).sp,
                    ),
                    color = onPage,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    byline,
                    style = MaterialTheme.typography.labelMedium,
                    color = onPage.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
