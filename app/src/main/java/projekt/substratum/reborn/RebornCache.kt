/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn

import projekt.substratum.common.platform.RebornStatus
import projekt.substratum.reborn.amoled.AmoledSummary
import projekt.substratum.reborn.applied.AppliedSnapshot
import projekt.substratum.reborn.themes.ThemeLooks
import projekt.substratum.reborn.themes.ThemesSnapshot
import java.util.concurrent.ConcurrentHashMap

/**
 * The last reads, kept for the process: a screen shown again starts from them while it reads
 * the device again, instead of from a loading state.
 */
object RebornCache {
    @Volatile
    var status: RebornStatus.Snapshot? = null

    @Volatile
    var applied: AppliedSnapshot? = null

    @Volatile
    var amoled: AmoledSummary? = null

    @Volatile
    var themes: ThemesSnapshot? = null

    /** Apps installed since the engine theme was applied that it has not read yet (Home) */
    @Volatile
    var newApps: List<String>? = null

    /**
     * Each theme's previews ([projekt.substratum.reborn.themes.ThemePreviews]), keyed by its id,
     * its last update and the look they were drawn with
     */
    val looks: MutableMap<String, ThemeLooks> = ConcurrentHashMap()
}
