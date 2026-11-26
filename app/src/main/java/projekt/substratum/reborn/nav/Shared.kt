/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.nav

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect

/** The tab stack's SharedTransitionLayout, or null outside one (previews, sheets). */
val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }

/** The route's AnimatedContent scope, or null outside a tab stack. */
val LocalRouteScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Shared-element keys between a theme's card (Themes, Home) and its page. */
object SharedKeys {
    fun art(id: ThemeId) = "theme-art-" + id.key
    fun name(id: ThemeId) = "theme-name-" + id.key
    fun avatar(id: ThemeId) = "theme-avatar-" + id.key
    fun palette(id: ThemeId) = "theme-palette-" + id.key
}

/**
 * Shared bounds keyed by [key] (a [SharedKeys] key), on the expressive spatial spring: the theme
 * card and the theme page morph into each other within one tab stack. A no-op outside a
 * SharedTransitionLayout and a route's AnimatedContent.
 */
@Composable
fun Modifier.themeShared(key: String): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val route = LocalRouteScope.current ?: return this
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Rect>()
    return with(shared) {
        this@themeShared.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = route,
            boundsTransform = BoundsTransform { _, _ -> spec },
        )
    }
}
