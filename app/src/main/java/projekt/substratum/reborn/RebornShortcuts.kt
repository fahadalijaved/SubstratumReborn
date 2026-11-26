/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import projekt.substratum.LauncherActivity
import projekt.substratum.R
import projekt.substratum.common.Packages
import projekt.substratum.common.platform.AmoledState
import projekt.substratum.reborn.nav.RebornLinks
import projekt.substratum.reborn.nav.ThemeId
import projekt.substratum.reborn.nav.enginePackage

/**
 * The launcher's dynamic "Customize <theme>" shortcut: published when an engine apply finishes
 * with the engine on, removed after Remove (and Undo everything, which removes too). The static
 * ones (Themes, Create a theme, Applied, Rescue Me!) are in res/xml/shortcuts.xml. The id is never
 * "studio": that was a manifest shortcut, and Android refuses to update such an id dynamically.
 */
object RebornShortcuts {

    const val CUSTOMIZE = "customize"
    private const val TAG = "RebornShortcuts"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun publishCustomize(context: Context) {
        val app = context.applicationContext
        scope.launch {
            try {
                val state = AmoledState.read(app)
                if (!state.enabled) {
                    ShortcutManagerCompat.removeDynamicShortcuts(app, listOf(CUSTOMIZE))
                    return@launch
                }
                val id = ThemeId.ofPackage(state.rulesTheme)
                val pkg = id.enginePackage
                val name = pkg?.let { Packages.getPackageName(app, it) } ?: if (pkg == null) {
                    app.getString(R.string.reborn_builtin_amoled_name)
                } else pkg
                val intent = Intent(app, LauncherActivity::class.java)
                    .setAction(Intent.ACTION_VIEW)
                    .putExtra(RebornActivity.EXTRA_TAB, RebornLinks.OPEN_STUDIO)
                    .putExtra(RebornActivity.EXTRA_THEME, pkg.orEmpty())
                val shortcut = ShortcutInfoCompat.Builder(app, CUSTOMIZE)
                    .setShortLabel(app.getString(R.string.shortcut_customize_short))
                    .setLongLabel(app.getString(R.string.shortcut_customize, name))
                    .setIcon(IconCompat.createWithResource(app, R.drawable.app_shortcut_amoled))
                    .setIntent(intent)
                    .build()
                ShortcutManagerCompat.pushDynamicShortcut(app, shortcut)
            } catch (e: Exception) {
                Log.w(TAG, "customize shortcut not published", e)
            }
        }
    }

    fun removeCustomize(context: Context) {
        val app = context.applicationContext
        scope.launch {
            try {
                ShortcutManagerCompat.removeDynamicShortcuts(app, listOf(CUSTOMIZE))
            } catch (e: Exception) {
                Log.w(TAG, "customize shortcut not removed", e)
            }
        }
    }
}
