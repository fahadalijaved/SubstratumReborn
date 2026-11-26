/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.themes

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import projekt.substratum.common.Packages
import projekt.substratum.common.References
import projekt.substratum.common.platform.AmoledRules
import projekt.substratum.common.platform.AmoledState
import projekt.substratum.reborn.RebornActions
import projekt.substratum.reborn.RebornCache
import projekt.substratum.reborn.applied.AppliedRepository
import projekt.substratum.reborn.nav.ThemeId
import projekt.substratum.util.helpers.Root
import java.io.File
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

private const val TAG = "RebornThemes"

/** What a theme is to the engine: built into Reborn, a Reborn engine (rules) APK, a classic overlay APK, or both. */
enum class ThemeKind {
    BUILT_IN,
    /** An APK with assets/amoled/rules.json */
    ENGINE,
    /** An APK with assets/overlays */
    CLASSIC,
    /** Both of the above */
    HYBRID;

    /** Applied by the engine (one at a time) */
    val engine: Boolean get() = this != CLASSIC
    /** Has overlays to choose (InformationActivity) */
    val classic: Boolean get() = this == CLASSIC || this == HYBRID

    companion object {
        fun of(rules: Boolean, overlays: Boolean): ThemeKind = when {
            rules && overlays -> HYBRID
            rules -> ENGINE
            else -> CLASSIC
        }
    }
}

/**
 * A theme: built in ([ThemeKind.BUILT_IN], whose [pkg] is its id key) or installed. [rules] marks a
 * Reborn engine theme (assets/amoled/rules.json). [inUse]: the engine applies with it now, or
 * (classic) some of its overlays are on. [namesake]: another installed theme has the same name (a
 * port next to its original), so the card also shows the package and version.
 */
@Immutable
data class ThemeItem(
    val pkg: String,
    val name: String,
    val author: String,
    val version: String?,
    /** Last install or update, so a new hero image is read after an update */
    val updated: Long,
    val rules: Boolean,
    val overlays: Boolean,
    val wallpapers: Boolean,
    /** A user app: can be uninstalled */
    val removable: Boolean,
    val namesake: Boolean = false,
    val kind: ThemeKind = ThemeKind.of(rules, overlays),
    val id: ThemeId = ThemeId.ofPackage(pkg),
    val inUse: Boolean = false,
) {
    /** Lower case name, author and package, for search */
    val search: String = (name + '\n' + author + '\n' + pkg).lowercase(Locale.ROOT)

    val builtIn: Boolean get() = kind == ThemeKind.BUILT_IN
}

/**
 * The Themes tab's read: built-ins first, then the installed themes; whether the bundled starter
 * APK is installed (it is shown as the built-in Reborn AMOLED, not again under Installed), and
 * the engine theme in use.
 */
@Immutable
data class ThemesSnapshot(
    val themes: List<ThemeItem>,
    val starterInstalled: Boolean,
    /** The engine theme applied now, or null when the engine is off */
    val engineInUse: ThemeId? = null,
) {
    val builtIns: List<ThemeItem> get() = themes.filter { it.builtIn }
    val installed: List<ThemeItem> get() = themes.filter { !it.builtIn }

    fun find(id: ThemeId): ThemeItem? = themes.firstOrNull { it.id == id }
}

/** Blocking: PackageManager, each theme's asset folders, the engine's state and the applied overlays. */
fun loadThemes(context: Context): ThemesSnapshot {
    val pm = context.packageManager
    val state = runCatching { AmoledState.read(context) }.getOrNull()
    val engineInUse = state?.takeIf { it.enabled }?.let { ThemeId.ofPackage(it.rulesTheme) }
    val classicInUse = runCatching { AppliedRepository.themesInUse(context) }.getOrDefault(emptySet())
    val starter = runCatching { pm.getPackageInfo(RebornActions.AMOLED_THEME, 0) }.getOrNull()
    val items = Packages.listSubstratumThemes(context)
        // The starter APK is the built-in Reborn AMOLED: one card
        .filter { it.packageName != RebornActions.AMOLED_THEME }
        .map { info -> themeOf(context, pm, info, engineInUse, classicInUse) }
    // Two themes may share a name (a ported theme next to the original): both are listed
    val names = items.groupingBy { it.name.lowercase(Locale.ROOT) }.eachCount()
    val themes = items
        .map { if ((names[it.name.lowercase(Locale.ROOT)] ?: 0) > 1) it.copy(namesake = true) else it }
        .sortedWith(compareBy<ThemeItem>({ it.name.lowercase(Locale.ROOT) }, { it.pkg }))
    val builtIns = BuiltInThemes.items(context, engineInUse == ThemeId.RebornAmoled, starter?.lastUpdateTime ?: 0L)
    return ThemesSnapshot(builtIns + themes, starter != null, engineInUse)
}

private fun themeOf(
    context: Context,
    pm: PackageManager,
    info: ApplicationInfo,
    engineInUse: ThemeId?,
    classicInUse: Set<String>,
): ThemeItem {
    val pkg = info.packageName
    val meta = info.metaData
    val packageInfo = runCatching { pm.getPackageInfo(pkg, 0) }.getOrNull()
    val folders = runCatching {
        context.createPackageContext(pkg, 0).assets.list("")?.toSet()
    }.getOrNull().orEmpty()
    val mask = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
    val rules = AmoledRules.isRulesTheme(context, pkg)
    val overlays = References.overlaysFolder in folders
    val id = ThemeId.Apk(pkg)
    return ThemeItem(
        pkg = pkg,
        name = meta?.getString(References.metadataName) ?: pkg,
        author = meta?.getString(References.metadataAuthor).orEmpty(),
        version = packageInfo?.versionName,
        updated = packageInfo?.lastUpdateTime ?: 0L,
        rules = rules,
        overlays = overlays,
        wallpapers = !meta?.get(References.metadataWallpapers)?.toString().isNullOrBlank(),
        removable = (info.flags and mask) == 0,
        id = id,
        inUse = (rules && engineInUse == id) || pkg in classicInUse,
    )
}

/** Blocking: the themes read again (and cached), for one theme's page. */
fun loadThemesCached(context: Context): ThemesSnapshot = loadThemes(context).also { RebornCache.themes = it }

// Hero images

/**
 * A theme's hero: its own image, or (when it has none) two colours from its icon for a drawn
 * one. Colours are ARGB.
 */
class Hero(val image: ImageBitmap?, val light: Int, val deep: Int)

/** Heroes by package, update and width: a few full-width images at a time. */
private val heroCache = object : LruCache<String, Hero>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Hero): Int =
        value.image?.let { it.width * it.height * 4 } ?: 64
}

/** Two decodes at a time: a fast scroll does not flood the IO pool. */
@OptIn(ExperimentalCoroutinesApi::class)
val heroLoader = Dispatchers.IO.limitedParallelism(2)

private fun heroKey(theme: ThemeItem, width: Int) = "${theme.pkg}@${theme.updated}@$width"

fun cachedHero(theme: ThemeItem, width: Int): Hero? = heroCache.get(heroKey(theme, width))

/**
 * Blocking. The hero image the Classic theme cards show ([Packages.getPackageHeroImage]), scaled
 * down to [width] pixels, or colours from the icon when the theme has none.
 */
fun loadHero(context: Context, theme: ThemeItem, width: Int): Hero {
    val key = heroKey(theme, width)
    heroCache.get(key)?.let { return it }
    val image = try {
        val drawable = Packages.getPackageHeroImage(context, theme.pkg, true)
        val w = drawable?.intrinsicWidth ?: 0
        val h = drawable?.intrinsicHeight ?: 0
        if (drawable != null && w > 1 && h > 1) {
            val scale = min(1f, width.toFloat() / w)
            drawable.toBitmap((w * scale).roundToInt().coerceAtLeast(1), (h * scale).roundToInt().coerceAtLeast(1))
                .asImageBitmap()
        } else null
    } catch (e: Exception) {
        Log.w(TAG, "hero of ${theme.pkg}: $e")
        null
    } catch (e: OutOfMemoryError) {
        // A huge hero image: the drawn artwork instead of a crash
        Log.w(TAG, "hero of ${theme.pkg} is too large: $e")
        null
    }
    var light = 0xFF3A3F58.toInt()
    var deep = 0xFF0B0C12.toInt()
    if (image == null) {
        try {
            val icon = context.packageManager.getApplicationIcon(theme.pkg).toBitmap(96, 96)
            val palette = Palette.from(icon).generate()
            light = palette.getVibrantColor(palette.getLightVibrantColor(palette.getDominantColor(light)))
            deep = palette.getDarkMutedColor(palette.getDarkVibrantColor(deep))
        } catch (e: Exception) {
            // the default colours
        }
    }
    return Hero(image, light, deep).also { heroCache.put(key, it) }
}

// Installing

sealed interface InstallResult {
    data class Installed(val pkg: String, val name: String, val rules: Boolean) : InstallResult
    data object NotATheme : InstallResult
    data object NoRoot : InstallResult
    data class Failed(val reason: String?) : InstallResult
}

/**
 * Blocking. Copies a picked APK to the cache, checks it is a Substratum theme, and installs it
 * as root: from the cache, or through /data/local/tmp when the package manager cannot read the
 * app's cache.
 */
fun installThemeFile(context: Context, uri: Uri): InstallResult {
    val dir = File(context.cacheDir, "theme-import").apply { mkdirs() }
    val apk = File(dir, "theme.apk")
    try {
        val input = context.contentResolver.openInputStream(uri) ?: return InstallResult.Failed(null)
        input.use { from -> apk.outputStream().use { from.copyTo(it) } }
        val info = context.packageManager.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_META_DATA)
        val name = info?.applicationInfo?.metaData?.getString(References.metadataName)
        if (info == null || name == null) return InstallResult.NotATheme
        if (!Root.checkRootAccess()) return InstallResult.NoRoot
        var out = pmInstall(apk.absolutePath)
        if (out?.contains("Success") != true) {
            Log.i(TAG, "install from the cache failed (${out?.trim()}), trying /data/local/tmp")
            val tmp = "/data/local/tmp/reborn_theme_import.apk"
            val copied = Root.runCommand("cp -f '${apk.absolutePath}' $tmp && chmod 644 $tmp && echo copied=ok")
            out = if (copied?.contains("copied=ok") == true) pmInstall(tmp) else copied
            Root.runCommand("rm -f $tmp")
        }
        return if (out?.contains("Success") == true) {
            InstallResult.Installed(info.packageName, name, AmoledRules.isRulesTheme(context, info.packageName))
        } else {
            InstallResult.Failed(failureOf(out))
        }
    } catch (e: Exception) {
        Log.e(TAG, "install from file failed", e)
        return InstallResult.Failed(null)
    } finally {
        apk.delete()
    }
}

/**
 * `pm install -r` as root. Android 14 and later refuse apps built for an old SDK, which many
 * Substratum themes are: those are installed again with the block bypassed.
 */
private fun pmInstall(path: String): String? {
    val out = Root.runCommand("pm install -r '$path' 2>&1")
    if (Build.VERSION.SDK_INT < 34 || out?.contains("INSTALL_FAILED_DEPRECATED_SDK_VERSION") != true) return out
    Log.i(TAG, "$path targets an old SDK, installing with the low target SDK block bypassed")
    return Root.runCommand("pm install -r --bypass-low-target-sdk-block '$path' 2>&1")
}

/** The reason in pm's output ("Failure [INSTALL_FAILED_...]"), or its last line. */
private fun failureOf(out: String?): String? {
    if (out.isNullOrBlank()) return null
    val reason = Regex("Failure \\[([^\\]]+)]").find(out)?.groupValues?.get(1)
        ?: out.lineSequence().lastOrNull { it.isNotBlank() }
    return reason?.trim()?.take(120)
}
