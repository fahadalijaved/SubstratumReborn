/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.themes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** OverlayPreview on tiny overlays written here: lookup order, references and what stays stock. */
class OverlayPreviewTest {

    private fun res(vararg colors: Pair<String, String>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n")
        colors.forEach { (name, value) -> append("    <color name=\"$name\">$value</color>\n") }
        append("</resources>\n")
    }

    private val settings = "com.android.settings"
    private val systemui = "com.android.systemui"

    private fun target(role: String, pkg: String, name: String) = OverlayPreview.Target(role, pkg, name)

    @Test
    fun layersFollowTheDefaultBuild() {
        assertEquals(listOf("res"), OverlayPreview.layers(listOf("res", "type1a_Blue.xml", "version")))
        // The default variant is built from res alone: type3-common only joins a chosen type 3 variant
        assertEquals(listOf("res"), OverlayPreview.layers(listOf("type3_Dark", "type3-common", "res")))
        // No res: the first type 3 folder by name, copied over type3-common
        assertEquals(listOf("type3_Amber", "type3-common"), OverlayPreview.layers(listOf("type3_Night", "type3-common", "type3_Amber")))
        assertEquals(listOf("type3-common"), OverlayPreview.layers(listOf("type3-common", "version")))
        assertEquals(emptyList<String>(), OverlayPreview.layers(listOf("type1a", "version")))
    }

    @Test
    fun valuesNightComesFirst() {
        assertEquals(listOf("values-night", "values-night-v31", "values"),
            OverlayPreview.valuesFolders(listOf("values", "values-v27", "values-night-v31", "values-night", "drawable")))
    }

    @Test
    fun literalColoursInEveryForm() {
        assertEquals(0xff112233.toInt(), OverlayPreview.parseHex("#112233"))
        assertEquals(0x80112233.toInt(), OverlayPreview.parseHex(" #80112233 "))
        assertEquals(0xffaabbcc.toInt(), OverlayPreview.parseHex("#abc"))
        assertEquals(0x88aabbcc.toInt(), OverlayPreview.parseHex("#8abc"))
        assertNull(OverlayPreview.parseHex("#12345"))
        assertNull(OverlayPreview.parseHex("@color/x"))
        assertNull(OverlayPreview.parseHex("#zzzzzz"))
    }

    @Test
    fun firstFoundWinsAcrossFoldersAndQualifiers() {
        val overlay = mapOf(
            "res/values/colors.xml" to res("page" to "#ff000001", "card" to "#ff000002", "title" to "#ff000003"),
            "res/values-night/colors.xml" to res("card" to "#ff000012"),
            "type3-common/values/colors.xml" to res("title" to "#ff000023", "summary" to "#ff000024"),
            "type3_Other/values/colors.xml" to res("page" to "#ff000031"),
        )
        val colors = OverlayPreview.resolve(
            listOf(
                target("page", settings, "page"),
                target("card", settings, "card"),
                target("title", settings, "title"),
                target("summary", settings, "summary"),
            ),
            mapOf(settings to overlay),
        )
        // res is there: the default build is res alone, neither type3-common nor a variant
        assertEquals(0xff000001.toInt(), colors["page"])
        assertEquals(0xff000012.toInt(), colors["card"])
        assertEquals(0xff000003.toInt(), colors["title"])
        assertFalse("summary" in colors)
    }

    @Test
    fun defaultType3FolderWithoutRes() {
        val overlay = mapOf(
            "type3_Zinc/values/colors.xml" to res("page" to "#ff000009"),
            "type3_Amber/values/colors.xml" to res("page" to "#ff0000aa"),
            "type3-common/values/colors.xml" to res("page" to "#ff0000bb", "card" to "#ff0000cc"),
        )
        val colors = OverlayPreview.resolve(
            listOf(target("page", settings, "page"), target("card", settings, "card")),
            mapOf(settings to overlay),
        )
        // The variant's files win over the common ones; what only type3-common names still counts
        assertEquals(0xff0000aa.toInt(), colors["page"])
        assertEquals(0xff0000cc.toInt(), colors["card"])
    }

    @Test
    fun theDefaultType1aFileIsMerged() {
        val overlay = mapOf(
            "res/values/colors.xml" to res("page" to "#ff101010"),
            "res/values/type1a.xml" to res("accent" to "#ff00aaff"),
        )
        val colors = OverlayPreview.resolve(
            listOf(target("page", settings, "page"), target("accent", settings, "accent")),
            mapOf(settings to overlay),
        )
        assertEquals(0xff101010.toInt(), colors["page"])
        assertEquals(0xff00aaff.toInt(), colors["accent"])
    }

    @Test
    fun referencesInsideTheOverlayAndThroughAndroid() {
        val overlays = mapOf(
            systemui to mapOf(
                "res/values/colors.xml" to res(
                    "panel" to "@color/my_black",
                    "my_black" to "#ff000000",
                    "tile" to "@android:color/shared_tile",
                    "icon" to "@*android:color/shared_icon",
                ),
            ),
            OverlayPreview.ANDROID to mapOf(
                "res/values/colors.xml" to res(
                    "shared_tile" to "@color/base",
                    "base" to "#ff222222",
                    "shared_icon" to "#ccffffff",
                ),
            ),
        )
        val colors = OverlayPreview.resolve(
            listOf(target("panel", systemui, "panel"), target("tile", systemui, "tile"), target("icon", systemui, "icon")),
            overlays,
        )
        assertEquals(0xff000000.toInt(), colors["panel"])
        assertEquals(0xff222222.toInt(), colors["tile"])
        assertEquals(0xccffffff.toInt(), colors["icon"])
    }

    @Test
    fun whatDoesNotResolveStaysStock() {
        val overlays = mapOf(
            settings to mapOf(
                "res/values/colors.xml" to res(
                    "a" to "@android:color/not_in_theme",
                    "b" to "?android:attr/colorAccent",
                    "c" to "@color/c",
                    "d" to "@null",
                    "e" to "@color/missing",
                ),
            ),
        )
        val colors = OverlayPreview.resolve(
            listOf("a", "b", "c", "d", "e", "f").map { target(it, settings, it) },
            overlays,
        )
        assertTrue(colors.isEmpty())
    }

    @Test
    fun itemsOfTypeColourAndBrokenFiles() {
        val overlay = mapOf(
            "res/values/colors.xml" to "<resources><item name=\"page\" type=\"color\">#ff050505</item><item name=\"dimen\" type=\"dimen\">4dp</item></resources>",
            "res/values/broken.xml" to "<resources><color name=\"card\">#ff",
            "res/values/notes.txt" to "not xml",
        )
        val colors = OverlayPreview.resolve(
            listOf(target("page", settings, "page"), target("card", settings, "card"), target("dimen", settings, "dimen")),
            mapOf(settings to overlay),
        )
        assertEquals(0xff050505.toInt(), colors["page"])
        assertFalse("card" in colors)
        assertFalse("dimen" in colors)
    }

    @Test
    fun noOverlayForAnApp() {
        val colors = OverlayPreview.resolve(listOf(target("page", settings, "page")), emptyMap())
        assertTrue(colors.isEmpty())
    }

    @Test
    fun doctypesAreRefused() {
        val xml = "<?xml version=\"1.0\"?><!DOCTYPE r [<!ENTITY x \"#ff123456\">]><resources><color name=\"page\">&x;</color></resources>"
        val colors = OverlayPreview.resolve(listOf(target("page", settings, "page")), mapOf(settings to mapOf("res/values/c.xml" to xml)))
        assertNull(colors["page"])
    }
}
