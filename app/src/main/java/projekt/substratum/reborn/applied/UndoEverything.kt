/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.applied

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import projekt.substratum.R
import projekt.substratum.reborn.components.ChipState
import projekt.substratum.reborn.components.ConfirmDialog
import projekt.substratum.reborn.components.ShapedIcon
import projekt.substratum.reborn.components.statusColors

/**
 * Undo everything's dialogs, shown once for the whole shell: the question with what will be
 * undone, the steps while they run (the dialog cannot be dismissed), and the result.
 * [AppliedRepository.askUndo] starts it from Applied or Home.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UndoEverythingHost() {
    val context = LocalContext.current
    val stage by AppliedRepository.undo.collectAsStateWithLifecycle()
    when (val s = stage) {
        is UndoStage.Confirm -> ConfirmDialog(
            title = stringResource(R.string.reborn_undo_title),
            text = stringResource(R.string.reborn_undo_text),
            confirmLabel = stringResource(R.string.reborn_undo_confirm),
            icon = R.drawable.ic_reborn_undo,
            destructive = true,
            onDismiss = AppliedRepository::closeUndo,
            onConfirm = { AppliedRepository.runUndo(context, s.plan) },
        ) { PlanRows(s.plan) }
        is UndoStage.Running -> BasicAlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        ) { Progress(s.step) }
        is UndoStage.Done -> ResultDialog(s.result, AppliedRepository::closeUndo)
        null -> Unit
    }
}

@Composable
private fun PlanRows(plan: UndoPlan) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PlanRow(
            R.drawable.ic_reborn_amoled,
            stringResource(R.string.reborn_undo_amoled),
            if (plan.amoledOn) pluralStringResource(R.plurals.reborn_undo_amoled_count, plan.amoledApps, plan.amoledApps)
            else stringResource(R.string.reborn_undo_amoled_off),
            scheme.primaryContainer, scheme.onPrimaryContainer,
        )
        PlanRow(
            R.drawable.ic_reborn_layers,
            stringResource(R.string.reborn_undo_overlays),
            pluralStringResource(R.plurals.reborn_undo_overlays_count, plan.overlays, plan.overlays),
            scheme.secondaryContainer, scheme.onSecondaryContainer,
        )
        PlanRow(
            R.drawable.ic_reborn_tune,
            stringResource(R.string.reborn_undo_qs),
            pluralStringResource(R.plurals.reborn_undo_qs_count, plan.qs, plan.qs),
            scheme.tertiaryContainer, scheme.onTertiaryContainer,
        )
        PlanRow(
            R.drawable.ic_reborn_restart,
            stringResource(R.string.reborn_undo_systemui),
            stringResource(R.string.reborn_undo_systemui_text),
            scheme.surfaceContainerHighest, scheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlanRow(
    @DrawableRes icon: Int,
    title: String,
    value: String,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ShapedIcon(icon, container, content, size = 36.dp, shape = MaterialShapes.Cookie6Sided.toShape())
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLargeEmphasized, color = MaterialTheme.colorScheme.onSurface)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Progress(step: UndoStep) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    Surface(shape = AlertDialogDefaults.shape, color = AlertDialogDefaults.containerColor) {
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LoadingIndicator(Modifier.size(48.dp))
                Spacer(Modifier.width(16.dp))
                Text(
                    stringResource(R.string.reborn_undo_running),
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                )
            }
            Spacer(Modifier.height(20.dp))
            UndoStep.entries.forEach { s ->
                val done = s.ordinal < step.ordinal
                val now = s == step
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                ) {
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        AnimatedContent(
                            targetState = when {
                                done -> 2
                                now -> 1
                                else -> 0
                            },
                            transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
                            label = "undoStep",
                        ) { phase ->
                            when (phase) {
                                2 -> Icon(painterResource(R.drawable.ic_reborn_check), null,
                                    tint = statusColors(ChipState.OK).accent, modifier = Modifier.size(20.dp))
                                1 -> LoadingIndicator(Modifier.size(24.dp))
                                else -> Icon(painterResource(R.drawable.ic_reborn_unknown), null,
                                    tint = scheme.outline, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(
                        stringResource(stepLabel(s)),
                        style = if (now) MaterialTheme.typography.bodyLargeEmphasized else MaterialTheme.typography.bodyLarge,
                        color = if (done || now) scheme.onSurface else scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun stepLabel(step: UndoStep): Int = when (step) {
    UndoStep.Amoled -> R.string.reborn_undo_step_amoled
    UndoStep.Overlays -> R.string.reborn_undo_step_overlays
    UndoStep.QuickSettings -> R.string.reborn_undo_step_qs
    UndoStep.SystemUi -> R.string.reborn_undo_step_systemui
}

@Composable
private fun ResultDialog(result: UndoResult, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painterResource(if (result.noRoot || result.failed.isNotEmpty()) R.drawable.ic_reborn_warning else R.drawable.ic_reborn_check),
                contentDescription = null,
            )
        },
        title = {
            Text(stringResource(if (result.noRoot) R.string.reborn_state_error else R.string.reborn_undo_done_title))
        },
        text = {
            if (result.noRoot) {
                Text(stringResource(R.string.reborn_undo_no_root))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ResultRow(
                        if (UndoStep.Amoled in result.failed) ChipState.Error else ChipState.OK,
                        if (UndoStep.Amoled in result.failed) stringResource(R.string.reborn_undo_failed_step, stringResource(R.string.reborn_undo_step_amoled))
                        else stringResource(R.string.reborn_undo_done_amoled),
                    )
                    ResultRow(
                        when {
                            UndoStep.Overlays in result.failed -> ChipState.Error
                            result.overlaysLeft > 0 -> ChipState.Warn
                            else -> ChipState.OK
                        },
                        when {
                            UndoStep.Overlays in result.failed -> stringResource(R.string.reborn_undo_failed_step, stringResource(R.string.reborn_undo_step_overlays))
                            result.overlaysLeft > 0 -> pluralStringResource(R.plurals.reborn_undo_done_overlays_left, result.overlaysLeft, result.overlaysLeft)
                            result.overlaysOff == 0 -> stringResource(R.string.reborn_undo_done_overlays_none)
                            else -> pluralStringResource(R.plurals.reborn_undo_done_overlays, result.overlaysOff, result.overlaysOff)
                        },
                    )
                    ResultRow(
                        if (result.qsReset) ChipState.OK else ChipState.Error,
                        if (result.qsReset) stringResource(R.string.reborn_undo_done_qs)
                        else stringResource(R.string.reborn_undo_failed_step, stringResource(R.string.reborn_undo_step_qs)),
                    )
                    ResultRow(
                        if (result.restarted) ChipState.OK else ChipState.Error,
                        if (result.restarted) stringResource(R.string.reborn_undo_done_restart)
                        else stringResource(R.string.reborn_undo_failed_step, stringResource(R.string.reborn_undo_step_systemui)),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.reborn_undo_done_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.reborn_done)) }
        },
    )
}

@Composable
private fun ResultRow(state: ChipState, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(state.icon),
            contentDescription = stringResource(state.label),
            tint = statusColors(state).accent,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}
