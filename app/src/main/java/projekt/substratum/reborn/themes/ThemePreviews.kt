/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.themes

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.withContext
import projekt.substratum.R
import projekt.substratum.Substratum
import projekt.substratum.common.platform.AmoledLevel
import projekt.substratum.common.platform.AmoledPresets
import projekt.substratum.common.platform.AmoledPreview
import projekt.substratum.common.platform.AmoledRules
import projekt.substratum.common.platform.AmoledUserRules
import projekt.substratum.reborn.RebornCache
import projekt.substratum.reborn.amoled.StudioState
import projekt.substratum.reborn.components.mock.MockRoles
import projekt.substratum.reborn.components.rememberResumeCount
import projekt.substratum.reborn.nav.ThemeId
import projekt.substratum.reborn.nav.enginePackage
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/** One look of a theme: its key, its name and the preview colours (stock and the theme's). */
@Immutable
class ThemeLook(val key: String, val label: String, val roles: MockRoles)

/**
 * A theme's previews: its looks, the one that is saved ([current]) and how many of the preview
 * roles ([total]) the theme gives a colour ([coverage]). Engine themes cover every role; a classic
 * theme only the ones its own overlays name ([drawnFromTheme]).
 */
@Immutable
class ThemeLooks(
    val id: ThemeId,
    val name: String,
    val looks: List<ThemeLook>,
    val current: String?,
    val coverage: Int,
    /** Classic: the colours come from the theme's overlay XML, not from the engine */
    val drawnFromTheme: Boolean = false,
    /** Engine themes: the level and accent choice Apply uses */
    val level: AmoledLevel? = null,
    val accent: String? = null,
) {
    val total: Int get() = AmoledPreview.ROLES.size

    /** The saved look (the first one when none is) */
    val shown: ThemeLook? get() = looks.firstOrNull { it.key == current } ?: looks.firstOrNull()

    fun look(key: String?): ThemeLook? = looks.firstOrNull { it.key == key }

    /** Enough of the theme's own colours for a before and after to mean something */
    val previewable: Boolean get() = coverage >= ThemePreviews.MIN_COVERAGE
}

/**
 * Reads [ThemeLooks]: built-in and engine themes through the engine's own preview
 * ([StudioState.showcasePreviews]: the saved look first, then each preset); classic themes from
 * their overlays' XML ([OverlayPreview]), reading only the preview roles' packages. Blocking; cached
 * in [RebornCache.looks] by theme, update and look.
 */
object ThemePreviews {

    /** Fewer roles than this: a classic theme for another One UI (its names moved), no before and after */
    const val MIN_COVERAGE = 6

    /** The saved look when no preset is it */
    const val SAVED = "saved"

    /** A classic theme's one look: its own colours */
    const val THEME_COLOURS = "theme"

    private const val TAG = "RebornPreviews"
    private const val MAX_XML = 512 * 1024

    /** The looks offered: the built-in presets, never one named after a third-party theme */
    private val SHOWN_PRESETS = AmoledPresets.PRESETS.filter { it.id != AmoledPresets.SPRITE }

    /** The last result per theme id, whatever look it was drawn with: what a screen starts from */
    private val latest = ConcurrentHashMap<String, ThemeLooks>()

    /** The last previews of [theme] read in this process, or null (main thread safe). */
    fun last(theme: ThemeItem): ThemeLooks? = latest[theme.id.key]

    /** Blocking (files, the theme's assets): [theme]'s previews, or null when they cannot be read. */
    fun load(context: Context, theme: ThemeItem): ThemeLooks? {
        val app = context.applicationContext
        val key = theme.id.key + "@" + theme.updated + "@" + fingerprint(app, theme)
        RebornCache.looks[key]?.let { return it }
        val looks = try {
            if (theme.kind.engine) engineLooks(app, theme) else classicLooks(app, theme)
        } catch (e: Exception) {
            Log.w(TAG, "no previews for ${theme.id.key}: $e")
            return null
        }
        // One entry per theme: an older look's previews go
        val prefix = theme.id.key + "@"
        RebornCache.looks.keys.removeAll { it.startsWith(prefix) }
        RebornCache.looks[key] = looks
        latest[theme.id.key] = looks
        return looks
    }

    /** Blocking: the previews of every theme that has some, in [themes]' order. */
    fun loadAll(context: Context, themes: List<ThemeItem>): List<ThemeLooks> = themes.mapNotNull { load(context, it) }

    /** What engine previews depend on besides the theme: the chosen look, the tile, Monet, user rules. */
    private fun fingerprint(context: Context, theme: ThemeItem): String {
        if (!theme.kind.engine) return ""
        val prefs = Substratum.getPreferences() ?: return ""
        val look = AmoledPresets.look(prefs)
        return listOf(
            look.preset, look.level?.key(), look.accent, look.accentLight,
            prefs.getString(AmoledRules.PREF_ACTIVE_TILE, null),
            AmoledPresets.monetAccent(context),
            AmoledUserRules.lastWritten(context),
        ).joinToString("|")
    }

    private fun engineLooks(context: Context, theme: ThemeItem): ThemeLooks {
        val previews = StudioState.showcasePreviews(context, theme.id.enginePackage)
        val prefs = Substratum.getPreferences()
        val tile = prefs?.getString(AmoledRules.PREF_ACTIVE_TILE, AmoledRules.TILE_WHITE) ?: AmoledRules.TILE_WHITE
        val saved = prefs?.let { AmoledPresets.look(it).preset }
            ?.takeIf { it == AmoledPresets.CUSTOM || AmoledPresets.preset(it) != null }
            ?: AmoledPresets.matching(previews.level, previews.accent, tile)
        val presets = SHOWN_PRESETS.map { preset ->
            ThemeLook(
                key = preset.id,
                label = context.getString(presetName(preset.id)),
                roles = if (preset.id == saved) previews.look else previews.presets[preset.id] ?: previews.look,
            )
        }
        val savedIsPreset = presets.any { it.key == saved }
        val looks = if (savedIsPreset) presets
        else listOf(ThemeLook(SAVED, context.getString(R.string.reborn_showcase_yours), previews.look)) + presets
        return ThemeLooks(
            id = theme.id,
            name = theme.name,
            looks = looks,
            current = if (savedIsPreset) saved else SAVED,
            coverage = AmoledPreview.ROLES.size,
            level = previews.level,
            accent = previews.accent,
        )
    }

    private fun classicLooks(context: Context, theme: ThemeItem): ThemeLooks {
        val stock = AmoledPreview.stock(context)
        val targets = AmoledPreview.ROLES.mapNotNull { role ->
            val pkg = stock.packageOf(role) ?: return@mapNotNull null
            val name = stock.nameOf(role) ?: return@mapNotNull null
            OverlayPreview.Target(role, pkg, name)
        }
        val assets = context.createPackageContext(theme.pkg, 0).assets
        val packages = targets.map { it.pkg }.toSet() + OverlayPreview.ANDROID
        val overlays = packages.associateWith { readOverlay(assets, "overlays/$it") }.filterValues { it.isNotEmpty() }
        val themed = OverlayPreview.resolve(targets, overlays)
        val stockColours = AmoledPreview.ROLES.mapNotNull { role -> stock.stock(role)?.let { role to it } }.toMap()
        return ThemeLooks(
            id = theme.id,
            name = theme.name,
            looks = listOf(ThemeLook(THEME_COLOURS, theme.name, MockRoles.of(stockColours, themed))),
            current = THEME_COLOURS,
            coverage = themed.size,
            drawnFromTheme = true,
        )
    }

    /** The values XML of an overlay's default build (see [OverlayPreview.layers]), by relative path. */
    private fun readOverlay(assets: AssetManager, base: String): Map<String, String> {
        val top = runCatching { assets.list(base)?.toList() }.getOrNull().orEmpty()
        if (top.isEmpty()) return emptyMap()
        val files = HashMap<String, String>()
        for (layer in OverlayPreview.layers(top)) {
            val folders = runCatching { assets.list("$base/$layer")?.toList() }.getOrNull().orEmpty()
            for (values in OverlayPreview.valuesFolders(folders)) {
                val names = runCatching { assets.list("$base/$layer/$values")?.toList() }.getOrNull().orEmpty()
                for (file in names) {
                    if (!file.endsWith(".xml")) continue
                    val text = runCatching { assets.open("$base/$layer/$values/$file").use(::readCapped) }.getOrNull()
                    if (text != null) files["$layer/$values/$file"] = text
                }
            }
        }
        return files
    }

    /** The file as text, or null when it is too large for a colours file. */
    private fun readCapped(input: InputStream): String? {
        val bytes = input.readBytes()
        return if (bytes.size > MAX_XML) null else String(bytes, Charsets.UTF_8)
    }

    @StringRes
    fun presetName(id: String): Int = when (id) {
        AmoledPresets.PURE_BLACK -> R.string.amoled_preset_pure
        AmoledPresets.ELEVATED_CARDS -> R.string.amoled_preset_elevated
        AmoledPresets.DIM -> R.string.amoled_preset_dim
        AmoledPresets.SPRITE -> R.string.amoled_preset_sprite
        else -> R.string.amoled_preset_custom
    }
}

/**
 * [theme]'s previews, read on the theme image loader (two at a time) and again when the look
 * changes or the app comes back; the last known ones meanwhile.
 */
@Composable
fun rememberThemeLooks(theme: ThemeItem?): State<ThemeLooks?> {
    val context = LocalContext.current
    val look by StudioState.look.collectAsState()
    val resume = rememberResumeCount()
    return produceState(theme?.let(ThemePreviews::last), theme?.id?.key, theme?.updated, look, resume) {
        if (theme == null) {
            value = null
            return@produceState
        }
        withContext(heroLoader) { ThemePreviews.load(context, theme) }?.let { value = it }
    }
}
