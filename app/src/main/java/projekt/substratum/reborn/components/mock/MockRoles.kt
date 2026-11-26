/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.components.mock

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import projekt.substratum.common.platform.AmoledPreview

/** Which colours a mock draws: One UI's own, or what AMOLED makes of them. */
enum class MockSide(val index: Int) {
    Stock(AmoledPreview.STOCK),
    Amoled(AmoledPreview.AMOLED),
}

/**
 * The colours of the preview roles ([AmoledPreview.ROLES]), stock and AMOLED, as
 * [AmoledPreview.roles] works them out. A role it has no colour for takes One UI 9's stock one.
 */
@Immutable
class MockRoles(private val roles: Map<String, IntArray>) {

    fun color(role: String, side: MockSide): Color {
        val pair = roles[role]
        if (pair != null && pair.size > side.index) return Color(pair[side.index])
        return Color(FALLBACK[role] ?: 0xff000000.toInt())
    }

    /** The same colours with some roles replaced (stock and AMOLED). */
    fun with(overrides: Map<String, IntArray>): MockRoles = MockRoles(roles + overrides)

    fun raw(role: String): IntArray? = roles[role]

    companion object {
        val Empty = MockRoles(emptyMap())

        /** One UI 9's dark mode (assets/reborn/preview_stock.json) */
        private val FALLBACK: Map<String, Int> = mapOf(
            AmoledPreview.QS_PANEL to 0xcc101010.toInt(),
            AmoledPreview.QS_TILE_OFF to 0x40ffffff,
            AmoledPreview.QS_TILE_ON to 0xffd5d5d5.toInt(),
            AmoledPreview.QS_TILE_ICON_OFF to 0x80fcfcff.toInt(),
            AmoledPreview.QS_TILE_ICON_ON to 0xd9252528.toInt(),
            AmoledPreview.QS_TRACK to 0x26ffffff,
            AmoledPreview.QS_FILL to 0xffe5e5e5.toInt(),
            AmoledPreview.QS_MEDIA to 0x66000000,
            AmoledPreview.SETTINGS_PAGE to 0xff010102.toInt(),
            AmoledPreview.SETTINGS_CARD to 0xff17171a.toInt(),
            AmoledPreview.SETTINGS_DIVIDER to 0xff3a3a3d.toInt(),
            AmoledPreview.SETTINGS_TITLE to 0xfffcfcff.toInt(),
            AmoledPreview.SETTINGS_SUMMARY to 0xffa3a3a7.toInt(),
            AmoledPreview.SETTINGS_SWITCH_ON to 0xff387aff.toInt(),
            AmoledPreview.SETTINGS_ACCENT to 0xff598fff.toInt(),
            AmoledPreview.POPUP_BG to 0xff252528.toInt(),
            AmoledPreview.POPUP_OUTLINE to 0x14ffffff,
            AmoledPreview.NOTIF_BG to 0x4d000000,
            AmoledPreview.LIST_PAGE to 0xff1a1b20.toInt(),
            AmoledPreview.LIST_ROW to 0xff2f3035.toInt(),
        )
    }
}

/** A mock colour that springs to its new value, so a change of look flows over the preview. */
@Composable
internal fun mockColor(target: Color, label: String): Color =
    animateColorAsState(
        targetValue = target,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = label,
    ).value
