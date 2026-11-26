/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.themes

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import projekt.substratum.R
import projekt.substratum.common.Broadcasts
import projekt.substratum.reborn.amoled.rememberActionSnackbar
import projekt.substratum.reborn.components.onIo
import projekt.substratum.util.helpers.Root
import projekt.substratum.util.helpers.StarterTheme

private const val APK_MIME = "application/vnd.android.package-archive"

/** What is being installed right now. */
enum class Installing { Starter, File }

/**
 * Installing themes: a theme APK picked from a file, and the bundled starter APK (Reborn AMOLED as
 * an app). Both install with root and say how it went in the shell's snackbar.
 */
@Stable
class ThemeInstaller internal constructor(
    private val onPickFile: () -> Unit,
    private val onStarter: () -> Unit,
) {
    var installing: Installing? by mutableStateOf(null)
        internal set

    fun pickFile() {
        if (installing == null) onPickFile()
    }

    fun installStarter() {
        if (installing == null) onStarter()
    }
}

/**
 * @param onChanged A theme was installed: read the list again
 * @param onOpen Opens an installed theme (the snackbar's "Open")
 */
@Composable
fun rememberThemeInstaller(onChanged: () -> Unit, onOpen: (pkg: String) -> Unit): ThemeInstaller {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = rememberActionSnackbar()
    val changed by rememberUpdatedState(onChanged)
    val open by rememberUpdatedState(onOpen)
    lateinit var installer: ThemeInstaller

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null || installer.installing != null) return@rememberLauncherForActivityResult
        scope.launch {
            installer.installing = Installing.File
            val result = try {
                onIo { installThemeFile(context, uri) }
            } finally {
                installer.installing = null
            }
            when (result) {
                is InstallResult.Installed -> {
                    Broadcasts.sendRefreshMessage(context.applicationContext)
                    changed()
                    snackbar(context.getString(R.string.reborn_install_done, result.name), context.getString(R.string.reborn_open)) {
                        open(result.pkg)
                    }
                }
                InstallResult.NotATheme -> snackbar(context.getString(R.string.reborn_install_not_theme), null, null)
                InstallResult.NoRoot -> snackbar(context.getString(R.string.reborn_needs_root), null, null)
                is InstallResult.Failed -> snackbar(
                    result.reason?.let { context.getString(R.string.reborn_install_failed, it) }
                        ?: context.getString(R.string.reborn_install_failed_plain),
                    null, null,
                )
            }
        }
    }

    installer = remember(picker) {
        ThemeInstaller(
            onPickFile = {
                try {
                    picker.launch(arrayOf(APK_MIME))
                } catch (e: Exception) {
                    Log.w("RebornThemes", "no document picker", e)
                    snackbar(context.getString(R.string.reborn_install_failed_plain), null, null)
                }
            },
            onStarter = {
                scope.launch {
                    installer.installing = Installing.Starter
                    val result = try {
                        onIo { if (!Root.checkRootAccess()) null else StarterTheme.installWithRoot(context) }
                    } finally {
                        installer.installing = null
                    }
                    when (result) {
                        // Without root the system installer takes the bundled APK
                        null -> StarterTheme.install(context)
                        true -> {
                            Broadcasts.sendRefreshMessage(context.applicationContext)
                            changed()
                            snackbar(context.getString(R.string.reborn_install_done, context.getString(R.string.reborn_builtin_amoled_name)), null, null)
                        }
                        false -> snackbar(context.getString(R.string.install_starter_theme_failed), null, null)
                    }
                }
            },
        )
    }
    return installer
}
