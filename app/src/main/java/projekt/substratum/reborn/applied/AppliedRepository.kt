/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.applied

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import projekt.substratum.R
import projekt.substratum.Substratum
import projekt.substratum.common.References
import projekt.substratum.common.platform.AmoledJobs
import projekt.substratum.common.platform.AmoledPresets
import projekt.substratum.common.platform.AmoledRules
import projekt.substratum.common.platform.AmoledState
import projekt.substratum.common.platform.QsOptions
import projekt.substratum.common.platform.ThemeManager
import projekt.substratum.common.platform.XposedBackend
import projekt.substratum.reborn.amoled.AmoledSummary
import projekt.substratum.reborn.amoled.amoledSummaryOf
import projekt.substratum.util.helpers.Root
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

enum class AppliedSource { THEME_OVERLAY, AMOLED, QS }

/** The Quick Settings options the module carries out in System UI, and the active tile colour. */
enum class QsOption { SHADE, TRACK, ROWS, TILE }

/**
 * One thing applied to [targetPkg]: a compiled overlay, the app's AMOLED colours or a Quick
 * Settings option. [enabled] false: known, but off right now.
 */
@Immutable
data class AppliedEntry(
    val source: AppliedSource,
    val targetPkg: String,
    val title: String,
    val enabled: Boolean,
    /** The theme a compiled overlay came from, or null */
    val themeLabel: String?,
    val detail: String,
    /** THEME_OVERLAY: the stored overlay's name */
    val overlay: String? = null,
    val fabricated: Boolean = false,
    /** AMOLED: how the app gets its colours, and how many */
    val route: AmoledState.Route? = null,
    val colors: Int = 0,
    val restartNeeded: Boolean = false,
    val level: String? = null,
    /** QS: which option, and its value while on (the rows' aspect, the tile option) */
    val qs: QsOption? = null,
    val value: String? = null,
) {
    val key: String = when (source) {
        AppliedSource.THEME_OVERLAY -> "overlay:$overlay"
        AppliedSource.AMOLED -> "amoled:$targetPkg"
        AppliedSource.QS -> "qs:$qs"
    }

    /** Lower case text to search in */
    val search: String = listOfNotNull(title, themeLabel, detail, targetPkg, overlay)
        .joinToString("\n").lowercase(Locale.ROOT)
}

/** Everything applied right now: one read for Applied and for Home's "Applied now". */
@Immutable
data class AppliedSnapshot(
    val entries: List<AppliedEntry>,
    /** Target package to its name ("Android system" for the framework) */
    val labels: Map<String, String>,
    val amoled: AmoledSummary,
    val readAt: Long,
) {
    fun count(source: AppliedSource? = null, enabled: Boolean? = null): Int =
        entries.count { (source == null || it.source == source) && (enabled == null || it.enabled == enabled) }

    fun qs(option: QsOption): AppliedEntry? =
        entries.firstOrNull { it.source == AppliedSource.QS && it.qs == option }

    fun label(pkg: String): String = labels[pkg] ?: pkg

    /** What Undo everything would take back. */
    fun undoPlan(): UndoPlan = UndoPlan(
        amoledOn = amoled.enabled,
        amoledApps = count(AppliedSource.AMOLED, true),
        overlays = count(AppliedSource.THEME_OVERLAY, true),
        qs = count(AppliedSource.QS, true),
    )
}

@Immutable
data class UndoPlan(val amoledOn: Boolean, val amoledApps: Int, val overlays: Int, val qs: Int)

// In the order they run: AMOLED's removal goes last because its job ends with the one System UI
// restart (several restarts in a row count towards LSPosed's safe mode)
enum class UndoStep { Overlays, QuickSettings, Amoled, SystemUi }

@Immutable
data class UndoResult(
    val noRoot: Boolean = false,
    /** The AMOLED removal was queued (it runs in the AMOLED service) */
    val amoledQueued: Boolean = false,
    val overlaysOff: Int = 0,
    val overlaysLeft: Int = 0,
    val qsReset: Boolean = false,
    val restarted: Boolean = false,
    val failed: List<UndoStep> = emptyList(),
)

/** Undo everything, from the question to the result; null when nothing is shown. */
sealed interface UndoStage {
    data class Confirm(val plan: UndoPlan) : UndoStage
    data class Running(val plan: UndoPlan, val step: UndoStep) : UndoStage
    data class Done(val result: UndoResult) : UndoStage
}

/**
 * Reads everything applied (compiled overlays, AMOLED, Quick Settings options) and turns single
 * entries off and on again; runs Undo everything. Reads are blocking (files, PackageManager);
 * actions run on [Dispatchers.IO] and bump [version], which screens read again on.
 */
object AppliedRepository {

    private const val TAG = "RebornApplied"
    private const val FRAMEWORK = "android"
    private const val SYSTEMUI = "com.android.systemui"
    private const val SETTINGS = "com.android.settings"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val labels = ConcurrentHashMap<String, String>()
    private val themeLabels = ConcurrentHashMap<String, String>()
    private val overlays = ConcurrentHashMap<String, OverlayMeta>()

    private val _version = MutableStateFlow(0)
    /** Goes up after every change made here, and whenever an AMOLED job finishes */
    val version: StateFlow<Int> = _version.asStateFlow()

    private val _restartPending = MutableStateFlow(false)
    /** Quick Settings options were written: System UI picks them up when it restarts */
    val restartPending: StateFlow<Boolean> = _restartPending.asStateFlow()

    private val _undo = MutableStateFlow<UndoStage?>(null)
    val undo: StateFlow<UndoStage?> = _undo.asStateFlow()

    init {
        // AMOLED switches and removals finish in the AMOLED service: read again then
        AmoledJobs.addListener(object : AmoledJobs.Listener {
            override fun onJobFinished(job: AmoledJobs.Job, ok: Boolean, message: String?) {
                // These restart System UI when they are done
                if (job.kind == AmoledJobs.Kind.REMOVE || (job.kind == AmoledJobs.Kind.APPLY_SYSTEMUI && job.restart)) {
                    restartDone()
                }
                changed()
            }
        })
    }

    private fun changed() = _version.update { it + 1 }

    /** System UI was restarted (Restart here, on Home or in the Studio). */
    fun restartDone() {
        _restartPending.value = false
    }

    // Reading

    private class OverlayMeta(val stamp: Long, val parent: String?, val fabricated: Boolean)

    /** Blocking: the cache folder, the state file, PackageManager. */
    fun read(context: Context): AppliedSnapshot {
        val res = context.resources
        val entries = ArrayList<AppliedEntry>()

        // Compiled overlays, as the LSPosed module applies them
        for (name in XposedBackend.listAll()) {
            val target = XposedBackend.getTarget(name) ?: continue
            val meta = overlayMeta(context, name)
            val theme = meta.parent?.let { themeLabel(context, it) }
            entries += AppliedEntry(
                source = AppliedSource.THEME_OVERLAY,
                targetPkg = target,
                title = context.getString(R.string.reborn_applied_overlay_title),
                enabled = XposedBackend.isEnabled(name),
                themeLabel = theme,
                detail = if (meta.fabricated) context.getString(R.string.reborn_applied_overlay_fabricated, name) else name,
                overlay = name,
                fabricated = meta.fabricated,
            )
        }

        // AMOLED: every app with colours, switched on or off
        val state = AmoledState.read(context)
        for ((pkg, app) in state.perApp) {
            if (app.route != AmoledState.Route.OVERLAY && app.route != AmoledState.Route.MODULE) continue
            val route = context.getString(
                if (app.route == AmoledState.Route.OVERLAY) R.string.reborn_applied_route_overlay
                else R.string.reborn_applied_route_module
            )
            var detail = if (app.colors > 0) {
                context.getString(R.string.reborn_applied_detail_join, route,
                    res.getQuantityString(R.plurals.reborn_studio_colours, app.colors, app.colors))
            } else route
            if (app.restartNeeded && app.userEnabled) {
                detail = context.getString(R.string.reborn_applied_detail_join, detail,
                    context.getString(R.string.reborn_applied_restart_app))
            }
            entries += AppliedEntry(
                source = AppliedSource.AMOLED,
                targetPkg = pkg,
                title = context.getString(R.string.reborn_applied_amoled_title),
                enabled = state.enabled && app.userEnabled,
                themeLabel = null,
                detail = detail,
                route = app.route,
                colors = app.colors,
                restartNeeded = app.restartNeeded,
                level = app.level,
            )
        }

        // Quick Settings: the rows act on their own, the rest only while AMOLED is applied
        val qs = QsOptions.read()
        // 0 is stock, and so is the stock aspect itself (an older Studio or a theme may write it)
        if (qs.aspect > 0f && abs(qs.aspect - QsOptions.STOCK_ASPECT) > 0.005f) {
            val aspect = String.format(Locale.US, "%.2f", qs.aspect)
            entries += qsEntry(QsOption.ROWS, true,
                context.getString(R.string.reborn_applied_qs_rows_title),
                context.getString(R.string.reborn_applied_qs_rows_detail, aspect),
                String.format(Locale.US, "%.3f", qs.aspect))
        }
        if (state.enabled) {
            entries += qsEntry(QsOption.SHADE, qs.shade,
                context.getString(R.string.reborn_qs_shade),
                context.getString(R.string.reborn_applied_qs_shade_detail), null)
            entries += qsEntry(QsOption.TRACK, qs.track,
                context.getString(R.string.reborn_qs_tracks),
                context.getString(R.string.reborn_applied_qs_track_detail), null)
            val tile = Substratum.getPreferences().getString(AmoledRules.PREF_ACTIVE_TILE, null)
            if (tile != null) {
                entries += qsEntry(QsOption.TILE, tile != AmoledRules.TILE_STOCK,
                    context.getString(R.string.reborn_applied_qs_tile_title),
                    context.getString(R.string.reborn_applied_qs_tile_detail, tileName(context, tile)), tile)
            }
        }

        val names = HashMap<String, String>()
        for (entry in entries) names.getOrPut(entry.targetPkg) { appLabel(context, entry.targetPkg) }
        return AppliedSnapshot(
            entries = entries,
            labels = names,
            amoled = amoledSummaryOf(context, state),
            readAt = System.currentTimeMillis(),
        )
    }

    private fun qsEntry(option: QsOption, on: Boolean, title: String, detail: String, value: String?) =
        AppliedEntry(
            source = AppliedSource.QS,
            targetPkg = SYSTEMUI,
            title = title,
            enabled = on,
            themeLabel = null,
            detail = detail,
            qs = option,
            value = value,
        )

    private fun tileName(context: Context, tile: String): String = context.getString(
        when (tile) {
            AmoledRules.TILE_BLUE -> R.string.amoled_qs_tile_blue
            AmoledRules.TILE_ACCENT -> R.string.amoled_qs_tile_accent
            AmoledRules.TILE_STOCK -> R.string.amoled_qs_tile_stock
            else -> R.string.amoled_qs_tile_white
        }
    ).lowercase(Locale.getDefault())

    /** The overlay's parent theme (metadata of the stored APK) and whether it has fabricated values, read once per file. */
    private fun overlayMeta(context: Context, name: String): OverlayMeta {
        val apk = File(XposedBackend.DIR, name + XposedBackend.APK)
        val index = File(XposedBackend.DIR, name + XposedBackend.INDEX)
        val stamp = apk.lastModified() * 31 + index.lastModified()
        overlays[name]?.takeIf { it.stamp == stamp }?.let { return it }
        val parent = try {
            XposedBackend.getMetadata(context, name, References.metadataOverlayParent)
        } catch (e: Exception) {
            null
        }
        return OverlayMeta(stamp, parent, isFabricated(index)).also { overlays[name] = it }
    }

    /** The index's header lines (key=value) carry the fabricated marker; resources follow. */
    private fun isFabricated(index: File): Boolean = try {
        BufferedReader(FileReader(index)).use { reader ->
            var line = reader.readLine()
            var found = false
            while (line != null && line.contains('=')) {
                if (line.trim() == XposedBackend.FABRICATED) {
                    found = true
                    break
                }
                line = reader.readLine()
            }
            found
        }
    } catch (e: Exception) {
        false
    }

    /**
     * A theme's name (its Substratum_Name, else its app name), or the package if it is gone. Only
     * found names are kept: a theme installed again later is named on the next read.
     */
    private fun themeLabel(context: Context, pkg: String): String {
        themeLabels[pkg]?.let { return it }
        return try {
            val info = context.packageManager.getApplicationInfo(pkg, PackageManager.GET_META_DATA)
            (info.metaData?.getString(References.metadataName)
                ?: info.loadLabel(context.packageManager).toString()).also { themeLabels[pkg] = it }
        } catch (e: Exception) {
            pkg
        }
    }

    private fun appLabel(context: Context, pkg: String): String {
        if (pkg == FRAMEWORK) return context.getString(R.string.reborn_applied_android)
        return labels.getOrPut(pkg) {
            try {
                val pm = context.packageManager
                pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
            } catch (e: Exception) {
                pkg
            }
        }
    }

    /** Groups first by the ones people look at most, then by name. */
    fun groupOrder(pkg: String, label: String): Pair<Int, String> {
        val first = listOf(FRAMEWORK, SYSTEMUI, SETTINGS).indexOf(pkg)
        return (if (first < 0) 3 else first) to label.lowercase(Locale.ROOT)
    }

    // Single entries

    /**
     * Blocking. Turns an entry off or (Undo) on again the way each part does it: a compiled
     * overlay through ThemeManager (which restarts its target as the legacy flow does), an app's
     * AMOLED through its job, a Quick Settings option in the options file (System UI picks it up
     * when it restarts; the active tile re-reads System UI's colours and restarts it).
     *
     * @return Whether it worked
     */
    fun setEnabled(context: Context, entry: AppliedEntry, on: Boolean): Boolean {
        val app = context.applicationContext
        val done = try {
            when (entry.source) {
                AppliedSource.THEME_OVERLAY -> {
                    val name = entry.overlay ?: return false
                    if (!Root.checkRootAccess()) return false
                    if (on) ThemeManager.enableOverlay(app, arrayListOf(name))
                    else ThemeManager.disableOverlay(app, arrayListOf(name))
                    XposedBackend.isEnabled(name) == on
                }
                AppliedSource.AMOLED -> {
                    AmoledJobs.setEnabled(app, entry.targetPkg, on)
                    true
                }
                AppliedSource.QS -> setQs(app, entry, on)
            }
        } catch (e: Exception) {
            Log.e(TAG, "switch ${entry.key} ${if (on) "on" else "off"} failed", e)
            false
        }
        changed()
        return done
    }

    private fun setQs(context: Context, entry: AppliedEntry, on: Boolean): Boolean {
        if (!Root.checkRootAccess()) return false
        val options = QsOptions.read()
        when (entry.qs) {
            QsOption.TILE -> return setTile(context, if (on) entry.value ?: AmoledRules.TILE_WHITE else AmoledRules.TILE_STOCK)
            QsOption.SHADE -> options.shade = on
            QsOption.TRACK -> options.track = on
            QsOption.ROWS -> options.aspect = if (on) entry.value?.toFloatOrNull() ?: 0f else 0f
            null -> return false
        }
        val written = options.write(context)
        if (written) _restartPending.value = true
        return written
    }

    private fun setTile(context: Context, tile: String): Boolean {
        val prefs = Substratum.getPreferences()
        prefs.edit()
            .putString(AmoledRules.PREF_ACTIVE_TILE, tile)
            .putBoolean(AmoledRules.PREF_QS_CHOSEN, true)
            .apply()
        // As in the Studio: a preset with its own tile (Sprite) becomes a custom look
        val preset = AmoledPresets.preset(prefs.getString(AmoledPresets.PREF_PRESET, null))
        if (preset?.tile != null && preset.tile != tile) {
            prefs.edit().putString(AmoledPresets.PREF_PRESET, AmoledPresets.CUSTOM).apply()
        }
        // System UI's colours are read again with the tile, then it restarts
        val state = AmoledState.read(context)
        if (state.enabled) AmoledJobs.applySystemUi(context, state.rulesTheme, true)
        return true
    }

    // Undo everything

    /** Shows the question for [plan]. */
    fun askUndo(plan: UndoPlan) {
        if (_undo.value is UndoStage.Running) return
        _undo.value = UndoStage.Confirm(plan)
    }

    fun closeUndo() {
        if (_undo.value !is UndoStage.Running) _undo.value = null
    }

    /**
     * Undo everything, one step after the other on IO: AMOLED's removal is queued (it keeps a
     * snapshot of the definitions), theme overlays are turned off (then any other compiled
     * overlay still on), the Quick Settings options go back to stock, and System UI restarts.
     * Runs for the process, not for a screen.
     */
    fun runUndo(context: Context, plan: UndoPlan) {
        if (_undo.value is UndoStage.Running) return
        val app = context.applicationContext
        scope.launch {
            _undo.value = UndoStage.Running(plan, UndoStep.Overlays)
            val root = withContext(Dispatchers.IO) { Root.checkRootAccess() }
            if (!root) {
                _undo.value = UndoStage.Done(UndoResult(noRoot = true))
                return@launch
            }
            val failed = ArrayList<UndoStep>()
            suspend fun <T> step(step: UndoStep, fallback: T, block: () -> T): T {
                _undo.value = UndoStage.Running(plan, step)
                return try {
                    withContext(Dispatchers.IO) { block() }
                } catch (e: Exception) {
                    Log.e(TAG, "undo everything: $step failed", e)
                    failed += step
                    fallback
                }
            }

            // 1. Theme overlays (the legacy "Disable all"), then compiled overlays without a theme
            val overlays = step(UndoStep.Overlays, 0 to -1) {
                val before = XposedBackend.listAll().count { XposedBackend.isEnabled(it) }
                ThemeManager.disableAllThemeOverlays(app)
                val left = XposedBackend.listAll().filter { XposedBackend.isEnabled(it) }
                if (left.isNotEmpty()) ThemeManager.disableOverlay(app, ArrayList(left))
                val after = XposedBackend.listAll().count { XposedBackend.isEnabled(it) }
                (before - after).coerceAtLeast(0) to after
            }
            // 2. Quick Settings back to stock: stock rows, see-through shade, stock tracks. Written
            // before AMOLED's removal is queued, so it never waits behind that job for the root shell
            val qs = step(UndoStep.QuickSettings, false) {
                val options = QsOptions.read()
                // 0 is how the options file says "stock rows" (the stock aspect, STOCK_ASPECT)
                options.aspect = 0f
                options.shade = false
                options.track = false
                options.write(app)
            }
            if (!qs && UndoStep.QuickSettings !in failed) failed += UndoStep.QuickSettings
            // 3. AMOLED, always: a removal also cleans up what a lost state file no longer knows.
            // The removal job restarts System UI once when it is done, which also loads the
            // stock Quick Settings options written above
            val amoled = step(UndoStep.Amoled, false) {
                AmoledJobs.remove(app)
                true
            }
            // 4. System UI: only when AMOLED's removal could not be queued (it restarts it itself)
            val restarted = if (amoled) true else step(UndoStep.SystemUi, false) {
                QsOptions.restartSystemUi()
                true
            }
            if (restarted) _restartPending.value = false
            _undo.value = UndoStage.Done(
                UndoResult(
                    amoledQueued = amoled,
                    overlaysOff = overlays.first,
                    overlaysLeft = overlays.second.coerceAtLeast(0),
                    qsReset = qs,
                    restarted = restarted,
                    failed = failed,
                )
            )
            changed()
        }
    }
}
