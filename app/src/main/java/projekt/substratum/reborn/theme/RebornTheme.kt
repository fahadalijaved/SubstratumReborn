/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import projekt.substratum.R
import projekt.substratum.Substratum
import projekt.substratum.common.References
import projekt.substratum.reborn.components.design.RebornTypography

/** A status role: the strong accent, what sits on it, and a quiet container for chips. */
@Immutable
data class StatusColors(
    val accent: Color,
    val onAccent: Color,
    val container: Color,
    val onContainer: Color,
)

/** Colours Material's scheme has no role for: status (green, amber, red) and card edges. */
@Immutable
data class RebornColors(
    val dark: Boolean,
    val ok: StatusColors,
    val warn: StatusColors,
    val error: StatusColors,
    val unknown: StatusColors,
    /** Cards on true black need an edge to read as cards. */
    val cardBorder: Color,
    val card: Color,
    val heroCard: Color,
)

val LocalRebornColors = staticCompositionLocalOf<RebornColors> {
    error("RebornTheme is not set")
}

/** Dark unless the user chose otherwise in Settings; the default follows the system. */
fun isDarkPreferred(context: Context, systemDark: Boolean): Boolean {
    if (context.resources.getBoolean(R.bool.forceAppDarkTheme)) return true
    return when (Substratum.getPreferences()?.getString(References.APP_THEME, References.AUTO_THEME)) {
        References.DARK_THEME -> true
        References.DEFAULT_THEME -> false
        else -> systemDark
    }
}

@Composable
fun rebornDarkTheme(): Boolean {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    return remember(systemDark) { isDarkPreferred(context, systemDark) }
}

private val Black = Color(0xFF000000)

private fun amoled(base: ColorScheme): ColorScheme = base.copy(
    background = Black,
    surface = Black,
    surfaceDim = Black,
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF222222),
)

private fun extras(dark: Boolean, scheme: ColorScheme): RebornColors = if (dark) {
    RebornColors(
        dark = true,
        ok = StatusColors(Color(0xFF6DD58C), Color(0xFF00391A), Color(0xFF0E2A17), Color(0xFFB4F1C3)),
        warn = StatusColors(Color(0xFFFFC74D), Color(0xFF3F2E00), Color(0xFF2E2300), Color(0xFFFFE08C)),
        error = StatusColors(Color(0xFFFF8A80), Color(0xFF5C0A0A), Color(0xFF3A0C0C), Color(0xFFFFDAD6)),
        unknown = StatusColors(scheme.outline, Black, Color(0xFF1E1E1E), scheme.onSurfaceVariant),
        cardBorder = Color(0xFF2A2A2A),
        card = scheme.surfaceContainer,
        heroCard = scheme.surfaceContainer,
    )
} else {
    RebornColors(
        dark = false,
        ok = StatusColors(Color(0xFF1E8E3E), Color.White, Color(0xFFD3F5DC), Color(0xFF0B3D1D)),
        warn = StatusColors(Color(0xFFB26A00), Color.White, Color(0xFFFFE9B8), Color(0xFF3F2E00)),
        error = StatusColors(scheme.error, scheme.onError, scheme.errorContainer, scheme.onErrorContainer),
        unknown = StatusColors(scheme.outline, scheme.surface, scheme.surfaceContainerHigh, scheme.onSurfaceVariant),
        cardBorder = Color.Transparent,
        card = scheme.surfaceContainerLow,
        heroCard = scheme.surfaceContainer,
    )
}

/**
 * Material 3 Expressive with wallpaper (Monet) colours, Google Sans Flex type and the M3E shape
 * scale; in dark mode every surface is true black and the containers step up from #0A0A0A.
 */
@Composable
fun RebornTheme(
    darkTheme: Boolean = rebornDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val dynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme = remember(darkTheme, dynamic) {
        if (darkTheme) {
            amoled(if (dynamic) dynamicDarkColorScheme(context) else darkColorScheme())
        } else {
            if (dynamic) dynamicLightColorScheme(context) else lightColorScheme()
        }
    }
    val extra = remember(scheme, darkTheme) { extras(darkTheme, scheme) }
    CompositionLocalProvider(LocalRebornColors provides extra) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            // M3E scale: 4/8/12/16, largeIncreased 20, extraLarge 28, extraLargeIncreased 32,
            // extraExtraLarge 48. Use MaterialTheme.shapes.* (or ShapeDefaults.*) instead of literals.
            shapes = Shapes(),
            typography = RebornTypography,
            content = content,
        )
    }
}
