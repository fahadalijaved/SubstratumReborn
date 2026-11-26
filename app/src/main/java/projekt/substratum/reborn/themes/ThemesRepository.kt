/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.themes

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
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
import projekt.substratum.reborn.RebornActions
import projekt.substratum.util.helpers.Root
import java.io.File
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

private const val TAG = "RebornThemes"

/**
 * An installed theme. [rules] marks an AMOLED rules theme (opens AMOLED Studio). [namesake]: another
 * installed theme has the same name (a port next to its original), so the card also shows the
 * package and version.
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
) {
    /** Lower case name, author and package, for search */
    val search: String = (name + '\n' + author + '\n' + pkg).lowercase(Locale.ROOT)
}

/** The Themes tab's read: the themes, and whether the bundled starter is installed. */
@Immutable
data class ThemesSnapshot(val themes: List<ThemeItem>, val starterInstalled: Boolean)

/** Blocking: PackageManager and each theme's asset folders. */
fun loadThemes(context: Context): ThemesSnapshot {
    val pm = context.packageManager
    val items = Packages.listSubstratumThemes(context).map { info -> themeOf(context, pm, info) }
    // Two themes may share a name (a ported theme next to the original): both are listed
    val names = items.groupingBy { it.name.lowercase(Locale.ROOT) }.eachCount()
    val themes = items
        .map { if ((names[it.name.lowercase(Locale.ROOT)] ?: 0) > 1) it.copy(namesake = true) else it }
        .sortedWith(compareBy<ThemeItem>({ it.name.lowercase(Locale.ROOT) }, { it.pkg }))
    return ThemesSnapshot(themes, Packages.isPackageInstalled(context, RebornActions.AMOLED_THEME))
}

private fun themeOf(context: Context, pm: PackageManager, info: ApplicationInfo): ThemeItem {
    val pkg = info.packageName
    val meta = info.metaData
    val packageInfo = runCatching { pm.getPackageInfo(pkg, 0) }.getOrNull()
    val folders = runCatching {
        context.createPackageContext(pkg, 0).assets.list("")?.toSet()
    }.getOrNull().orEmpty()
    val mask = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
    return ThemeItem(
        pkg = pkg,
        name = meta?.getString(References.metadataName) ?: pkg,
        author = meta?.getString(References.metadataAuthor).orEmpty(),
        version = packageInfo?.versionName,
        updated = packageInfo?.lastUpdateTime ?: 0L,
        rules = AmoledRules.isRulesTheme(context, pkg),
        overlays = References.overlaysFolder in folders,
        wallpapers = !meta?.get(References.metadataWallpapers)?.toString().isNullOrBlank(),
        removable = (info.flags and mask) == 0,
    )
}

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
        var out = Root.runCommand("pm install -r '${apk.absolutePath}' 2>&1")
        if (out?.contains("Success") != true) {
            Log.i(TAG, "install from the cache failed (${out?.trim()}), trying /data/local/tmp")
            val tmp = "/data/local/tmp/reborn_theme_import.apk"
            out = Root.runCommand("cp -f '${apk.absolutePath}' $tmp && chmod 644 $tmp && " +
                "pm install -r $tmp 2>&1; rm -f $tmp")
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

/** The reason in pm's output ("Failure [INSTALL_FAILED_...]"), or its last line. */
private fun failureOf(out: String?): String? {
    if (out.isNullOrBlank()) return null
    val reason = Regex("Failure \\[([^\\]]+)]").find(out)?.groupValues?.get(1)
        ?: out.lineSequence().lastOrNull { it.isNotBlank() }
    return reason?.trim()?.take(120)
}
