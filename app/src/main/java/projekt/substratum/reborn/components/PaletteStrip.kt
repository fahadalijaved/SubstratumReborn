/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import projekt.substratum.R
import projekt.substratum.common.platform.AmoledPreview
import projekt.substratum.reborn.components.mock.MockRoles
import projekt.substratum.reborn.components.mock.mockColor
import projekt.substratum.reborn.theme.LocalRebornColors

/** The roles a palette shows, in order: page, cards, accent, text and outline. */
val PaletteRoles = listOf(
    AmoledPreview.SETTINGS_PAGE,
    AmoledPreview.SETTINGS_CARD,
    AmoledPreview.SETTINGS_ACCENT,
    AmoledPreview.SETTINGS_TITLE,
    AmoledPreview.POPUP_OUTLINE,
)

/** The palette's first three: page, cards and accent (a compact tile's dots). */
val PaletteRolesShort = PaletteRoles.take(3)

/**
 * A theme look's palette: five overlapping circles (page, cards, accent, text, outline) with a
 * ring in the [ring] colour (the card it sits on). Colours spring on change. Its content
 * description names them ("Palette: black page, dark grey cards, teal accent"), so colour is
 * never the only signal. [shown]: the roles drawn ([PaletteRoles], or [PaletteRolesShort]).
 */
@Composable
fun PaletteStrip(
    roles: MockRoles,
    modifier: Modifier = Modifier,
    ring: Color = LocalRebornColors.current.card,
    dot: Dp = 14.dp,
    shown: List<String> = PaletteRoles,
) {
    val context = LocalContext.current
    val argb = shown.map(roles::after)
    val description = remember(argb) { paletteDescription(context, argb) }
    val colors = shown.mapIndexed { i, role -> mockColor(Color(argb[i]), "palette_$role") }
    PaletteDots(colors, modifier, ring = ring, dot = dot, description = description)
}

/** Overlapping colour dots with a ring; [description] replaces their semantics. */
@Composable
fun PaletteDots(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    ring: Color = LocalRebornColors.current.card,
    dot: Dp = 14.dp,
    overlap: Dp = 4.dp,
    description: String? = null,
) {
    Row(
        modifier.clearAndSetSemantics { if (description != null) contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(-overlap),
    ) {
        colors.forEach { c ->
            Box(
                Modifier
                    .size(dot + 4.dp)
                    .border(2.dp, ring, CircleShape)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(ring)
                    .background(c),
            )
        }
    }
}

/** "Palette: black page, dark grey cards, teal accent" for the five palette colours (ARGB). */
fun paletteDescription(context: android.content.Context, argb: List<Int>): String {
    if (argb.size < 3) return context.getString(R.string.reborn_palette_preview)
    val page = PaletteNames.composite(argb[0], 0xff000000.toInt())
    val parts = context.getString(
        R.string.reborn_palette_parts,
        context.getString(colourName(PaletteNames.name(page))),
        context.getString(colourName(PaletteNames.name(argb[1], over = page))),
        context.getString(colourName(PaletteNames.name(argb[2], over = page))),
    )
    return context.getString(R.string.reborn_palette_description, parts)
}

@StringRes
fun colourName(name: ColourName): Int = when (name) {
    ColourName.BLACK -> R.string.reborn_colour_black
    ColourName.NEAR_BLACK -> R.string.reborn_colour_near_black
    ColourName.DARK_GREY -> R.string.reborn_colour_dark_grey
    ColourName.GREY -> R.string.reborn_colour_grey
    ColourName.LIGHT_GREY -> R.string.reborn_colour_light_grey
    ColourName.WHITE -> R.string.reborn_colour_white
    ColourName.RED -> R.string.reborn_colour_red
    ColourName.ORANGE -> R.string.reborn_colour_orange
    ColourName.AMBER -> R.string.reborn_colour_amber
    ColourName.YELLOW -> R.string.reborn_colour_yellow
    ColourName.LIME -> R.string.reborn_colour_lime
    ColourName.GREEN -> R.string.reborn_colour_green
    ColourName.TEAL -> R.string.reborn_colour_teal
    ColourName.CYAN -> R.string.reborn_colour_cyan
    ColourName.BLUE -> R.string.reborn_colour_blue
    ColourName.INDIGO -> R.string.reborn_colour_indigo
    ColourName.VIOLET -> R.string.reborn_colour_violet
    ColourName.PINK -> R.string.reborn_colour_pink
}
