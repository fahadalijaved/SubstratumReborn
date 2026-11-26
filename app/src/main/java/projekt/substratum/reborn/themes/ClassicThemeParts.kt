/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.themes

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import projekt.substratum.R
import projekt.substratum.common.platform.ClassicDarkMode
import projekt.substratum.common.platform.ClassicParts
import projekt.substratum.common.platform.XposedBackend
import projekt.substratum.reborn.applied.AppliedRepository
import projekt.substratum.reborn.components.SectionCard
import projekt.substratum.reborn.components.design.RebornConfirmDialog
import projekt.substratum.reborn.components.design.RebornSwitch
import projekt.substratum.reborn.components.design.SegmentedGroup
import projekt.substratum.reborn.components.design.rebornSegmentColors
import projekt.substratum.reborn.components.onIo
import projekt.substratum.reborn.components.rememberResumeCount
import projekt.substratum.reborn.components.rememberRoot

/** Turning a classic theme off and on again, and its parts one by one. Root work on IO. */
object ClassicThemeActions {

    suspend fun turnOff(context: Context, pkg: String): ClassicParts.TurnedOff = onIo {
        ClassicParts.turnOff(context.applicationContext, pkg).also { AppliedRepository.changedNow() }
    }

    suspend fun undo(context: Context, off: ClassicParts.TurnedOff) = onIo {
        ClassicParts.undo(context.applicationContext, off)
        AppliedRepository.changedNow()
    }

    suspend fun setPart(context: Context, overlay: String, on: Boolean): Boolean = onIo {
        ClassicParts.setEnabled(context.applicationContext, overlay, on).also { AppliedRepository.changedNow() }
    }

    /**
     * Turns [theme] off and says so with Undo (the parts stay in Applied, switched off, for later).
     */
    fun turnOffWithUndo(
        scope: CoroutineScope,
        context: Context,
        theme: ThemeItem,
        snackbar: (String, String?, (() -> Unit)?) -> Unit,
        onChanged: () -> Unit,
    ) {
        scope.launch {
            val off = runCatching { turnOff(context, theme.pkg) }.getOrNull()
            onChanged()
            when {
                off == null -> snackbar(context.getString(R.string.reborn_failed), null, null)
                off.count() == 0 -> snackbar(context.getString(R.string.reborn_theme_turn_off_none, theme.name), null, null)
                else -> snackbar(
                    context.resources.getQuantityString(R.plurals.reborn_theme_turned_off, off.count(), theme.name, off.count()),
                    context.getString(R.string.reborn_studio_undo),
                ) {
                    scope.launch {
                        runCatching { undo(context, off) }
                        onChanged()
                    }
                }
            }
        }
    }
}

/** The question before a classic theme is turned off. */
@Composable
fun TurnOffDialog(themeName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    RebornConfirmDialog(
        title = stringResource(R.string.reborn_theme_turn_off_title, themeName),
        text = stringResource(R.string.reborn_theme_turn_off_text),
        confirmLabel = stringResource(R.string.reborn_theme_turn_off),
        icon = R.drawable.ic_reborn_off,
        destructive = true,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/**
 * A classic theme's own setting: dark mode only (on by default, so light mode stays stock). It
 * takes effect the next time its overlays are applied.
 */
@Composable
fun ClassicOptions(theme: ThemeItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    // Read again on return: Classic's theme menu has the same switch
    val resumes = rememberResumeCount()
    val stored by rememberRoot(theme.pkg, version, resumes) { ClassicDarkMode.isOn(context, theme.pkg) }
    var local by remember(theme.pkg, resumes) { mutableStateOf<Boolean?>(null) }
    val on = local ?: stored ?: true
    SegmentedGroup(1, modifier) { _, shapes ->
        SegmentedListItem(
            checked = on,
            onCheckedChange = { checked ->
                local = checked
                scope.launch {
                    onIo { ClassicDarkMode.set(theme.pkg, checked) }
                    version++
                }
            },
            shapes = shapes,
            colors = rebornSegmentColors(),
            leadingContent = { Icon(painterResource(R.drawable.ic_reborn_dark_mode), contentDescription = null) },
            supportingContent = {
                Text(stringResource(if (on) R.string.reborn_classic_dark_only_on else R.string.reborn_classic_dark_only_off))
            },
            trailingContent = { RebornSwitch(checked = on, onCheckedChange = null) },
        ) {
            Text(stringResource(R.string.classic_dark_only), style = MaterialTheme.typography.bodyLargeEmphasized)
        }
    }
}

/**
 * The parts of a classic theme on this phone, each with its switch: one per target app or part
 * of System UI. A part held back after it crashed System UI says so and loads again when switched
 * on. "Turn off this theme" switches all of them off at once.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ClassicPartsCard(
    theme: ThemeItem,
    refresh: Int,
    onTurnOff: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    val parts by rememberRoot(theme.pkg, refresh, version) {
        ClassicParts.withSkipped(ClassicParts.ofTheme(context, theme.pkg).filter { XposedBackend.isStored(it.overlay) })
    }
    var busy by remember { mutableStateOf<String?>(null) }
    val list = parts ?: return
    if (list.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SegmentedGroup(list.size) { i, shapes ->
            val part = list[i]
            val on = part.enabled
            SegmentedListItem(
                checked = on,
                onCheckedChange = { checked ->
                    if (busy != null) return@SegmentedListItem
                    busy = part.overlay
                    scope.launch {
                        runCatching { ClassicThemeActions.setPart(context, part.overlay, checked) }
                        busy = null
                        version++
                    }
                },
                shapes = shapes,
                colors = rebornSegmentColors(),
                enabled = busy == null,
                supportingContent = {
                    val state = stringResource(
                        when {
                            part.held -> R.string.reborn_part_paused
                            on -> R.string.reborn_part_on
                            else -> R.string.reborn_part_off
                        }
                    )
                    Text(
                        if (part.skipped > 0 && !part.held) {
                            stringResource(R.string.reborn_applied_detail_join, state,
                                pluralStringResource(R.plurals.reborn_part_skipped, part.skipped, part.skipped))
                        } else state,
                        color = if (part.held) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingContent = { RebornSwitch(checked = on, onCheckedChange = null) },
            ) {
                Text(part.label, style = MaterialTheme.typography.bodyLargeEmphasized)
            }
        }
        val active = list.count { it.enabled || it.held }
        if (active > 0) {
            SectionCard {
                Text(
                    pluralStringResource(R.plurals.reborn_theme_parts_on, active, active),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
                Text(
                    stringResource(R.string.reborn_theme_turn_off_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = onTurnOff,
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Icon(painterResource(R.drawable.ic_reborn_off), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.reborn_theme_turn_off))
                }
            }
        }
    }
}
