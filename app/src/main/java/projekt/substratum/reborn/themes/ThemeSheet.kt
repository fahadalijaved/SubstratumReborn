/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.themes

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import projekt.substratum.R
import projekt.substratum.common.References
import projekt.substratum.common.Theming
import projekt.substratum.reborn.components.design.SegmentedGroup
import projekt.substratum.reborn.components.design.rebornSegmentColors
import projekt.substratum.reborn.components.onIo

/** What can be done to an installed theme app, from its sheet and its page. */
object ThemeActions {

    private const val TAG = "RebornThemes"

    fun appInfo(context: Context, pkg: String) =
        start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null)))

    /** The system's uninstall dialog; the list is read again when it returns here. */
    fun uninstall(context: Context, pkg: String) =
        start(context, Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))

    /** The theme's overlays in InformationActivity (Classic's theme screen). */
    fun chooseOverlays(context: Context, pkg: String): Boolean = try {
        Theming.launchOverlayTheme(context, pkg)
        true
    } catch (e: RuntimeException) {
        Log.w(TAG, "cannot open $pkg", e)
        false
    }

    /**
     * Pins the theme to the home screen. The label is the shortcut's id too: namesakes get their
     * version, or their package when the versions match, so one does not replace the other's.
     *
     * @return Whether the launcher was asked
     */
    suspend fun pinShortcut(context: Context, theme: ThemeItem, all: List<ThemeItem>): Boolean {
        val label = if (theme.namesake) {
            val others = all.filter { it.pkg != theme.pkg && it.name.equals(theme.name, ignoreCase = true) }
            val suffix = theme.version?.takeIf { v -> others.none { it.version == v } } ?: theme.pkg
            context.getString(R.string.reborn_theme_shortcut_label, theme.name, suffix)
        } else theme.name
        return runCatching { onIo { References.createLauncherIcon(context, theme.pkg, label) } }.isSuccess
    }

    private fun start(context: Context, intent: Intent) {
        try {
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "cannot start $intent", e)
        }
    }
}

/**
 * The long-press sheet of an installed theme: what it is, then open its page, open it in Classic
 * (classic themes), app info, shortcut, and uninstall.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ThemeSheet(
    theme: ThemeItem,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onOpenClassic: () -> Unit,
    onAppInfo: () -> Unit,
    onShortcut: () -> Unit,
    onUninstall: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val sheet = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = scheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 24.dp),
            ) {
                KindAvatar(theme, size = 56.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        theme.name,
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        byline(theme),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        theme.pkg,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            FlowRow(
                modifier = Modifier.padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) { ThemeChips(theme) }
            Spacer(Modifier.height(16.dp))
            val actions = buildList {
                add(
                    SheetActionRow(
                        R.drawable.ic_reborn_open,
                        stringResource(R.string.reborn_theme_open),
                        stringResource(R.string.reborn_theme_open_page),
                        onOpen,
                    ),
                )
                if (theme.kind.classic) {
                    add(
                        SheetActionRow(
                            R.drawable.ic_reborn_layers,
                            stringResource(R.string.reborn_theme_open_classic),
                            stringResource(R.string.reborn_theme_open_overlays),
                            onOpenClassic,
                        ),
                    )
                }
                add(
                    SheetActionRow(
                        R.drawable.ic_reborn_info,
                        stringResource(R.string.reborn_theme_app_info),
                        stringResource(R.string.reborn_theme_app_info_text),
                        onAppInfo,
                    ),
                )
                add(
                    SheetActionRow(
                        R.drawable.ic_reborn_shortcut,
                        stringResource(R.string.reborn_theme_shortcut),
                        stringResource(R.string.reborn_theme_shortcut_text),
                        onShortcut,
                    ),
                )
            }
            SegmentedGroup(actions.size, Modifier.padding(horizontal = 16.dp)) { i, shapes ->
                SheetAction(actions[i], shapes, rebornSegmentColors())
            }
            if (theme.removable) {
                // Destructive, so in its own group, in the error colours
                Spacer(Modifier.height(12.dp))
                SegmentedGroup(1, Modifier.padding(horizontal = 16.dp)) { _, shapes ->
                    SheetAction(
                        SheetActionRow(
                            R.drawable.ic_reborn_delete,
                            stringResource(R.string.reborn_theme_uninstall),
                            stringResource(R.string.reborn_theme_uninstall_text),
                            onUninstall,
                        ),
                        shapes,
                        ListItemDefaults.segmentedColors(
                            containerColor = scheme.errorContainer,
                            contentColor = scheme.onErrorContainer,
                            leadingContentColor = scheme.onErrorContainer,
                            supportingContentColor = scheme.onErrorContainer.copy(alpha = 0.8f),
                        ),
                    )
                }
            }
        }
    }
}

private class SheetActionRow(@DrawableRes val icon: Int, val title: String, val text: String, val onClick: () -> Unit)

/** One segmented action row of the theme sheet. */
@Composable
private fun SheetAction(row: SheetActionRow, shapes: ListItemShapes, colors: ListItemColors) {
    val haptics = LocalHapticFeedback.current
    SegmentedListItem(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            row.onClick()
        },
        shapes = shapes,
        colors = colors,
        leadingContent = { Icon(painterResource(row.icon), contentDescription = null) },
        supportingContent = { Text(row.text) },
    ) {
        Text(row.title, style = MaterialTheme.typography.bodyLargeEmphasized)
    }
}
