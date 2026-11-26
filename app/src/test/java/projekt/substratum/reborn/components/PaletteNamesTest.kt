/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** The HCT values against material-color-utilities' reference colours, and the names they get. */
class PaletteNamesTest {

    private fun assertHct(argb: Int, hue: Double, chroma: Double, tone: Double) {
        val hct = PaletteNames.hct(argb)
        assertEquals("hue of ${Integer.toHexString(argb)}", hue, hct.hue, 0.5)
        assertEquals("chroma of ${Integer.toHexString(argb)}", chroma, hct.chroma, 0.5)
        assertEquals("tone of ${Integer.toHexString(argb)}", tone, hct.tone, 0.5)
    }

    @Test
    fun hctOfThePrimaries() {
        assertHct(0xffff0000.toInt(), 27.41, 113.36, 53.24)
        assertHct(0xff00ff00.toInt(), 142.14, 108.41, 87.74)
        assertHct(0xff0000ff.toInt(), 282.79, 87.23, 32.30)
        assertEquals(100.0, PaletteNames.hct(0xffffffff.toInt()).tone, 0.01)
        assertEquals(0.0, PaletteNames.hct(0xff000000.toInt()).tone, 0.01)
    }

    @Test
    fun greysByTone() {
        assertEquals(ColourName.BLACK, PaletteNames.name(0xff000000.toInt()))
        assertEquals(ColourName.BLACK, PaletteNames.name(0xff010102.toInt()))
        assertEquals(ColourName.NEAR_BLACK, PaletteNames.name(0xff141414.toInt()))
        assertEquals(ColourName.NEAR_BLACK, PaletteNames.name(0xff17171a.toInt()))
        assertEquals(ColourName.DARK_GREY, PaletteNames.name(0xff3a3a3d.toInt()))
        assertEquals(ColourName.GREY, PaletteNames.name(0xff8a8a8e.toInt()))
        assertEquals(ColourName.LIGHT_GREY, PaletteNames.name(0xffc8c8cc.toInt()))
        assertEquals(ColourName.WHITE, PaletteNames.name(0xfffcfcff.toInt()))
    }

    @Test
    fun huesWhenColourful() {
        assertEquals(ColourName.RED, PaletteNames.name(0xffff0000.toInt()))
        assertEquals(ColourName.ORANGE, PaletteNames.name(0xffff8000.toInt()))
        assertEquals(ColourName.AMBER, PaletteNames.name(0xffffbf00.toInt()))
        assertEquals(ColourName.YELLOW, PaletteNames.name(0xffffff00.toInt()))
        assertEquals(ColourName.LIME, PaletteNames.name(0xff80ff00.toInt()))
        assertEquals(ColourName.GREEN, PaletteNames.name(0xff00a000.toInt()))
        assertEquals(ColourName.TEAL, PaletteNames.name(0xff009587.toInt()))
        assertEquals(ColourName.TEAL, PaletteNames.name(0xff008080.toInt()))
        assertEquals(ColourName.CYAN, PaletteNames.name(0xff00ffff.toInt()))
        assertEquals(ColourName.BLUE, PaletteNames.name(0xff387aff.toInt()))
        assertEquals(ColourName.BLUE, PaletteNames.name(0xff0000ff.toInt()))
        assertEquals(ColourName.VIOLET, PaletteNames.name(0xff8000ff.toInt()))
        assertEquals(ColourName.PINK, PaletteNames.name(0xffff4081.toInt()))
    }

    @Test
    fun translucentColoursShowOverTheBackground() {
        // White at 8 % over black is a near-black hairline, not white
        assertEquals(ColourName.NEAR_BLACK, PaletteNames.name(0x14ffffff))
        assertEquals(ColourName.WHITE, PaletteNames.name(0x14ffffff, over = 0xffffffff.toInt()))
        assertEquals(0xff808080.toInt(), PaletteNames.composite(0x80ffffff.toInt(), 0xff000000.toInt()))
    }
}
