/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.nav

/**
 * Where an intent lands: its "tab", "theme" and "open" extras as a tab and the routes pushed on
 * that tab's root. Pure Kotlin (RebornLinksTest checks every row).
 *
 * | tab                | theme | open                       | lands on                                   |
 * |--------------------|-------|----------------------------|--------------------------------------------|
 * | home / none        | –     | –                          | Home                                       |
 * | themes             | –     | –                          | Themes                                     |
 * | themes             | pkg   | detail (default)           | Themes › Theme(pkg)                        |
 * | amoled or studio   | none  | –                          | Themes › Theme(in use) › Studio            |
 * | amoled / studio    | pkg   | studio (default for these) | Themes › Theme(pkg) › Studio(pkg)          |
 * | amoled / studio    | pkg   | detail                     | Themes › Theme(pkg)                        |
 * | create             | –     | –                          | Create                                     |
 * | applied            | –     | –                          | Applied                                    |
 *
 * "amoled" is the tab of earlier versions (old shortcuts and notifications still send it).
 */
object RebornLinks {

    const val OPEN_DETAIL = "detail"
    const val OPEN_STUDIO = "studio"

    private const val TAB_AMOLED = "amoled"
    private const val TAB_STUDIO = "studio"

    /**
     * @param engineThemeInUse The package the engine theme in use applies with (null: none or the
     *   built-in rules); a Studio link without a theme opens that theme's Studio
     */
    fun resolve(tab: String?, theme: String?, open: String?, engineThemeInUse: String?): Pair<RebornTab, List<Route>> {
        val named = theme?.takeIf { it.isNotBlank() }
        val wanted = tab?.trim()?.lowercase()
        return when (wanted) {
            TAB_AMOLED, TAB_STUDIO -> {
                val id = ThemeId.ofPackage(named ?: engineThemeInUse)
                if (named != null && open.equals(OPEN_DETAIL, ignoreCase = true)) {
                    RebornTab.Themes to listOf(Route.Theme(id))
                } else {
                    RebornTab.Themes to listOf(Route.Theme(id), Route.Studio(id))
                }
            }
            RebornTab.Themes.key -> when {
                named == null -> RebornTab.Themes to emptyList()
                open.equals(OPEN_STUDIO, ignoreCase = true) -> {
                    val id = ThemeId.ofPackage(named)
                    RebornTab.Themes to listOf(Route.Theme(id), Route.Studio(id))
                }
                else -> RebornTab.Themes to listOf(Route.Theme(ThemeId.ofPackage(named)))
            }
            RebornTab.Create.key -> RebornTab.Create to emptyList()
            RebornTab.Applied.key -> RebornTab.Applied to emptyList()
            null, "" -> if (named != null) {
                // A theme without a tab: its page
                RebornTab.Themes to listOf(Route.Theme(ThemeId.ofPackage(named)))
            } else RebornTab.Home to emptyList()
            else -> RebornTab.Home to emptyList()
        }
    }
}
