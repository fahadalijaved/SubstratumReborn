/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn

import projekt.substratum.common.platform.RebornStatus
import projekt.substratum.reborn.amoled.AmoledSummary
import projekt.substratum.reborn.amoled.AppliedNow
import projekt.substratum.reborn.themes.ThemeItem

/**
 * The last reads, kept for the process: a screen shown again starts from them while it reads
 * the device again, instead of from a loading state.
 */
object RebornCache {
    @Volatile
    var status: RebornStatus.Snapshot? = null

    @Volatile
    var applied: AppliedNow? = null

    @Volatile
    var amoled: AmoledSummary? = null

    @Volatile
    var themes: List<ThemeItem>? = null
}
