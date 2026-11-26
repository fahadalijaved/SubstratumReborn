/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Every row of RebornLinks' table, plus the theme ids and route keys the links rely on. */
class RebornLinksTest {

    private val starter = "projekt.substratum.theme.amoled"
    private val engine = "com.example.engine.theme"
    private val classic = "com.example.classic.theme"
    private val builtIn = ThemeId.RebornAmoled

    private fun lands(tab: RebornTab, vararg routes: Route) = tab to routes.toList()

    // | home / none | – | – | Home |

    @Test
    fun homeTab() {
        assertEquals(lands(RebornTab.Home), RebornLinks.resolve("home", null, null, null))
    }

    @Test
    fun noTab() {
        assertEquals(lands(RebornTab.Home), RebornLinks.resolve(null, null, null, engine))
        assertEquals(lands(RebornTab.Home), RebornLinks.resolve("", "", null, null))
    }

    @Test
    fun unknownTab() {
        assertEquals(lands(RebornTab.Home), RebornLinks.resolve("nowhere", null, null, null))
    }

    // | themes | – | – | Themes |

    @Test
    fun themesTab() {
        assertEquals(lands(RebornTab.Themes), RebornLinks.resolve("themes", null, null, null))
        assertEquals(lands(RebornTab.Themes), RebornLinks.resolve("themes", "", null, engine))
    }

    // | themes | pkg | detail (default) | Themes › Theme(pkg) |

    @Test
    fun themesWithThemeOpensItsPage() {
        val id = ThemeId.Apk(classic)
        assertEquals(lands(RebornTab.Themes, Route.Theme(id)), RebornLinks.resolve("themes", classic, null, null))
        assertEquals(lands(RebornTab.Themes, Route.Theme(id)), RebornLinks.resolve("themes", classic, "detail", engine))
    }

    @Test
    fun themesWithStarterOpensTheBuiltIn() {
        assertEquals(lands(RebornTab.Themes, Route.Theme(builtIn)), RebornLinks.resolve("themes", starter, null, null))
    }

    // | amoled (old) or studio | none | – | Themes › Theme(engine theme in use, else builtin:reborn.amoled) › Studio |

    @Test
    fun amoledWithoutThemeOpensTheStudioOfTheThemeInUse() {
        val id = ThemeId.Apk(engine)
        assertEquals(
            lands(RebornTab.Themes, Route.Theme(id), Route.Studio(id)),
            RebornLinks.resolve("amoled", null, null, engine),
        )
        assertEquals(
            lands(RebornTab.Themes, Route.Theme(id), Route.Studio(id)),
            RebornLinks.resolve("studio", "", null, engine),
        )
    }

    @Test
    fun amoledWithoutThemeOrEngineOpensTheBuiltIn() {
        assertEquals(
            lands(RebornTab.Themes, Route.Theme(builtIn), Route.Studio(builtIn)),
            RebornLinks.resolve("amoled", null, null, null),
        )
        assertEquals(
            lands(RebornTab.Themes, Route.Theme(builtIn), Route.Studio(builtIn)),
            RebornLinks.resolve("studio", null, null, starter),
        )
    }

    // | amoled / studio | pkg | studio (default for these) | Themes › Theme(pkg) › Studio(pkg) |

    @Test
    fun amoledWithThemeOpensItsStudio() {
        val id = ThemeId.Apk(engine)
        val expected = lands(RebornTab.Themes, Route.Theme(id), Route.Studio(id))
        assertEquals(expected, RebornLinks.resolve("amoled", engine, null, null))
        assertEquals(expected, RebornLinks.resolve("studio", engine, "studio", null))
        assertEquals(expected, RebornLinks.resolve("AMOLED", engine, null, classic))
    }

    // | amoled / studio | pkg | detail | Themes › Theme(pkg) |

    @Test
    fun amoledWithThemeAndDetailOpensItsPage() {
        val id = ThemeId.Apk(engine)
        assertEquals(lands(RebornTab.Themes, Route.Theme(id)), RebornLinks.resolve("amoled", engine, "detail", null))
        assertEquals(lands(RebornTab.Themes, Route.Theme(id)), RebornLinks.resolve("studio", engine, "detail", null))
    }

    // | create | – | – | Create |

    @Test
    fun createTab() {
        assertEquals(lands(RebornTab.Create), RebornLinks.resolve("create", null, null, engine))
    }

    // | applied | – | – | Applied |

    @Test
    fun appliedTab() {
        assertEquals(lands(RebornTab.Applied), RebornLinks.resolve("applied", null, null, null))
    }

    // Package to ThemeId

    @Test
    fun packagesToThemeIds() {
        assertEquals(builtIn, ThemeId.ofPackage(null))
        assertEquals(builtIn, ThemeId.ofPackage(""))
        assertEquals(builtIn, ThemeId.ofPackage(starter))
        assertEquals(ThemeId.Apk(engine), ThemeId.ofPackage(engine))
        assertEquals("builtin:reborn.amoled", builtIn.key)
        assertEquals("apk:$engine", ThemeId.Apk(engine).key)
        assertNull(builtIn.enginePackage)
        assertEquals(engine, ThemeId.Apk(engine).enginePackage)
    }

    @Test
    fun keysRoundTrip() {
        val routes = listOf(
            Route.Home, Route.Themes, Route.Create, Route.Applied,
            Route.Theme(builtIn), Route.Theme(ThemeId.Apk(classic)),
            Route.Studio(builtIn), Route.Studio(ThemeId.Apk(engine), StudioSection.QuickSettings),
            Route.Studio(builtIn, StudioSection.Apps), Route.Inspect(), Route.Inspect("com.android.settings"),
        )
        routes.forEach { assertEquals(it, Route.parse(it.key)) }
        assertEquals("theme/builtin:reborn.amoled", Route.Theme(builtIn).key)
        assertEquals("studio/apk:$engine#Look", Route.Studio(ThemeId.Apk(engine), StudioSection.Look).key)
        assertEquals("inspect/com.android.settings", Route.Inspect("com.android.settings").key)
        assertNull(Route.parse("theme/nothing"))
        assertNull(Route.parse("elsewhere"))
        assertEquals(builtIn, ThemeId.parse("builtin:reborn.amoled"))
        assertNull(ThemeId.parse("apk:"))
    }
}
