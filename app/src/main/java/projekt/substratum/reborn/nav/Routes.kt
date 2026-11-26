/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.nav

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import projekt.substratum.R
import projekt.substratum.reborn.RebornActions

/**
 * A theme, as routes, links and (later) the engine refer to it. The [key] strings are stable:
 * "builtin:reborn.amoled" for a theme shipped in the app, "apk:<package>" for an installed one.
 */
sealed interface ThemeId {
    val key: String

    /** A theme shipped in the app: Reborn AMOLED ("reborn.amoled"). */
    data class BuiltIn(val name: String) : ThemeId {
        override val key: String get() = BUILT_IN + name
    }

    /** An installed theme APK. */
    data class Apk(val pkg: String) : ThemeId {
        override val key: String get() = APK + pkg
    }

    companion object {
        const val BUILT_IN = "builtin:"
        const val APK = "apk:"

        /** The built-in Reborn AMOLED (the engine's own rules) */
        val RebornAmoled: ThemeId = BuiltIn("reborn.amoled")

        /** A [key] back to its id, or null when it is none. */
        fun parse(key: String?): ThemeId? = when {
            key == null -> null
            key.startsWith(BUILT_IN) -> key.substring(BUILT_IN.length).takeIf { it.isNotBlank() }?.let(::BuiltIn)
            key.startsWith(APK) -> key.substring(APK.length).takeIf { it.isNotBlank() }?.let(::Apk)
            else -> null
        }

        /**
         * The theme a package names: none (null or empty) and the bundled starter APK, which
         * carries the built-in rules, are Reborn AMOLED; anything else is that APK.
         */
        fun ofPackage(pkg: String?): ThemeId =
            if (pkg.isNullOrBlank() || pkg == RebornActions.AMOLED_THEME) RebornAmoled else Apk(pkg)
    }
}

/** The package the engine applies with for this theme, or null for the built-in rules. */
val ThemeId.enginePackage: String?
    get() = (this as? ThemeId.Apk)?.pkg

/** A part of the Studio a theme page's Options row scrolls to. */
enum class StudioSection { Look, QuickSettings, Apps }

/** A screen in a tab's back stack; [key] is its saved form and its saved-state key. */
sealed interface Route {
    val key: String

    data object Home : Route {
        override val key: String = "home"
    }

    data object Themes : Route {
        override val key: String = "themes"
    }

    data object Create : Route {
        override val key: String = "create"
    }

    data object Applied : Route {
        override val key: String = "applied"
    }

    /** A theme's page. */
    data class Theme(val id: ThemeId) : Route {
        override val key: String get() = THEME + id.key
    }

    /** The Studio of an engine theme, scrolled to [section] when one is given. */
    data class Studio(val id: ThemeId, val section: StudioSection? = null) : Route {
        override val key: String get() = STUDIO + id.key + (section?.let { "#" + it.name } ?: "")
    }

    /** Home › Engine: the engine's status, what it can do here, quick actions and rescue. */
    data object Engine : Route {
        override val key: String = "engine"
    }

    /** Home › Engine › Setup: the setup checklist as a page (the first launch shows it over the tabs). */
    data object Setup : Route {
        override val key: String = "setup"
    }

    /** Create › Inspect an app: the app list to pick from, or [pkg]'s colours. */
    data class Inspect(val pkg: String? = null) : Route {
        override val key: String get() = if (pkg == null) INSPECT else "$INSPECT/$pkg"
    }

    companion object {
        private const val THEME = "theme/"
        private const val STUDIO = "studio/"
        private const val INSPECT = "inspect"

        /** A [key] back to its route, or null when it is none. */
        fun parse(key: String?): Route? {
            if (key == null) return null
            return when {
                key == Home.key -> Home
                key == Themes.key -> Themes
                key == Create.key -> Create
                key == Applied.key -> Applied
                key == Engine.key -> Engine
                key == Setup.key -> Setup
                key.startsWith(THEME) -> ThemeId.parse(key.substring(THEME.length))?.let(::Theme)
                key.startsWith(STUDIO) -> {
                    val rest = key.substring(STUDIO.length)
                    val hash = rest.indexOf('#')
                    val id = ThemeId.parse(if (hash < 0) rest else rest.substring(0, hash)) ?: return null
                    val section = if (hash < 0) null
                    else StudioSection.entries.firstOrNull { it.name.equals(rest.substring(hash + 1), ignoreCase = true) }
                    Studio(id, section)
                }
                key == INSPECT -> Inspect(null)
                key.startsWith("$INSPECT/") -> key.substring(INSPECT.length + 1).takeIf { it.isNotBlank() }?.let(::Inspect)
                else -> null
            }
        }
    }
}

/** The bottom bar's tabs, in their order; each has its own back stack starting at [root]. */
enum class RebornTab(
    val root: Route,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    @DrawableRes val selectedIcon: Int,
) {
    Home(Route.Home, R.string.reborn_tab_home, R.drawable.ic_reborn_home, R.drawable.ic_reborn_home_filled),
    Themes(Route.Themes, R.string.reborn_tab_themes, R.drawable.ic_reborn_themes, R.drawable.ic_reborn_themes_filled),
    Create(Route.Create, R.string.reborn_tab_create, R.drawable.ic_reborn_create, R.drawable.ic_reborn_create_filled),
    Applied(Route.Applied, R.string.reborn_tab_applied, R.drawable.ic_reborn_applied, R.drawable.ic_reborn_applied_filled);

    /** The tab's name in links and saved state */
    val key: String get() = root.key

    companion object {
        fun of(key: String?): RebornTab? = entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
    }
}
