/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.themes

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.graphics.shapes.RoundedPolygon
import projekt.substratum.BuildConfig
import projekt.substratum.R
import projekt.substratum.reborn.nav.ThemeId

/**
 * A theme shipped inside Reborn: shown first in Themes, opened like any engine theme. Its
 * [hero] is drawn ([DrawnHero]) with its [polygon] as the icon.
 */
class BuiltInTheme(
    val id: ThemeId,
    @StringRes val name: Int,
    @StringRes val description: Int,
    val polygon: RoundedPolygon,
    /** The engine package its rules come from (null: the engine's own defaults) */
    val enginePackage: String?,
)

/**
 * The built-in themes, in the order Themes lists them. Reborn AMOLED is the engine's own rules;
 * a later built-in (Glass) is one more entry here.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
object BuiltInThemes {

    /** The accent the built-in card draws with when none is saved (One UI blue) */
    const val DEFAULT_LIGHT: Int = 0xFF387AFF.toInt()
    const val DEFAULT_DEEP: Int = 0xFF000000.toInt()

    val RebornAmoled = BuiltInTheme(
        id = ThemeId.RebornAmoled,
        name = R.string.reborn_builtin_amoled_name,
        description = R.string.reborn_builtin_amoled_text,
        polygon = MaterialShapes.Clover4Leaf,
        enginePackage = null,
    )

    val all: List<BuiltInTheme> = listOf(RebornAmoled)

    fun of(id: ThemeId): BuiltInTheme? = all.firstOrNull { it.id == id }

    /**
     * The built-ins as theme items. [inUse]: the engine applies with the built-in rules now (none
     * named, or the starter APK). [starter]: the starter APK's version, when it is installed.
     */
    fun items(context: Context, inUse: Boolean, starterUpdated: Long): List<ThemeItem> = all.map { theme ->
        ThemeItem(
            pkg = theme.id.key,
            name = context.getString(theme.name),
            author = context.getString(R.string.reborn_builtin_author),
            version = BuildConfig.VERSION_NAME,
            updated = starterUpdated,
            rules = true,
            overlays = false,
            wallpapers = false,
            removable = false,
            kind = ThemeKind.BUILT_IN,
            id = theme.id,
            inUse = inUse && theme.id == ThemeId.RebornAmoled,
        )
    }
}
