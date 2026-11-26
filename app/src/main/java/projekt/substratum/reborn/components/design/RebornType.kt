/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.components.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import projekt.substratum.R

// Google Sans Flex (SIL OFL 1.1), the face of Google's Material 3 Expressive apps. One variable
// file (axes wght, wdth, opsz, ROND, slnt) serves every weight; without it the Galaxy draws
// everything in SamsungOne, the biggest "this is One UI" tell.
private fun flex(weight: Int, round: Float) = Font(
    resId = R.font.google_sans_flex,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("ROND", round),
    ),
)

/** Body, titles and labels. */
val RebornSans = FontFamily(flex(400, 0f), flex(500, 0f), flex(600, 0f), flex(700, 0f))

/** Display and headline sizes: the same face with rounded terminals (ROND 100). */
val RebornRounded = FontFamily(flex(400, 100f), flex(500, 100f), flex(600, 100f), flex(700, 100f))

private fun TextStyle.sans() = copy(fontFamily = RebornSans)
private fun TextStyle.round() = copy(fontFamily = RebornRounded)

/**
 * Every baseline and every *Emphasized style in Google Sans Flex. Emphasized is the M3E token
 * weight (Medium for display/headline/titleLarge/body, Bold for titleMedium/titleSmall/labels):
 * use those styles instead of Bold or SemiBold weight overrides.
 */
val RebornTypography: Typography = Typography().let { b ->
    Typography(
        displayLarge = b.displayLarge.round(),
        displayMedium = b.displayMedium.round(),
        displaySmall = b.displaySmall.round(),
        headlineLarge = b.headlineLarge.round(),
        headlineMedium = b.headlineMedium.round(),
        headlineSmall = b.headlineSmall.round(),
        titleLarge = b.titleLarge.sans(),
        titleMedium = b.titleMedium.sans(),
        titleSmall = b.titleSmall.sans(),
        bodyLarge = b.bodyLarge.sans(),
        bodyMedium = b.bodyMedium.sans(),
        bodySmall = b.bodySmall.sans(),
        labelLarge = b.labelLarge.sans(),
        labelMedium = b.labelMedium.sans(),
        labelSmall = b.labelSmall.sans(),
        displayLargeEmphasized = b.displayLargeEmphasized.round(),
        displayMediumEmphasized = b.displayMediumEmphasized.round(),
        displaySmallEmphasized = b.displaySmallEmphasized.round(),
        headlineLargeEmphasized = b.headlineLargeEmphasized.round(),
        headlineMediumEmphasized = b.headlineMediumEmphasized.round(),
        headlineSmallEmphasized = b.headlineSmallEmphasized.round(),
        titleLargeEmphasized = b.titleLargeEmphasized.sans(),
        titleMediumEmphasized = b.titleMediumEmphasized.sans(),
        titleSmallEmphasized = b.titleSmallEmphasized.sans(),
        bodyLargeEmphasized = b.bodyLargeEmphasized.sans(),
        bodyMediumEmphasized = b.bodyMediumEmphasized.sans(),
        bodySmallEmphasized = b.bodySmallEmphasized.sans(),
        labelLargeEmphasized = b.labelLargeEmphasized.sans(),
        labelMediumEmphasized = b.labelMediumEmphasized.sans(),
        labelSmallEmphasized = b.labelSmallEmphasized.sans(),
    )
}

/** Sentence-case overline above a hero title ("System status"); replaces the 1.4 sp all-caps ones. */
val Typography.overline: TextStyle get() = labelLargeEmphasized

/** The label above a segmented group. */
val Typography.sectionLabel: TextStyle get() = titleSmallEmphasized

/** Big counts ("229 apps", "3 of 4"): tabular figures so they do not jitter while counting. */
val Typography.heroNumber: TextStyle get() = displayMediumEmphasized.copy(fontFeatureSettings = "tnum")

private val SystemTypography = Typography()

/**
 * The One UI mocks (components/mock) draw One UI, so they keep the system font (SamsungOne on a
 * Galaxy). Wrap each mock's root in this.
 */
@Composable
fun SystemFont(content: @Composable () -> Unit) {
    MaterialTheme(typography = SystemTypography, content = content)
}
