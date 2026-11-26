/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.engine

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import projekt.substratum.R
import projekt.substratum.reborn.RebornActions
import projekt.substratum.reborn.components.ConfirmDialog
import projekt.substratum.reborn.components.rememberSnackbar
import projekt.substratum.reborn.home.Fix
import projekt.substratum.reborn.home.HomeStatus

/** The engine actions that ask first. */
enum class Confirm { Pause, Resume, SoftReboot, RestartSystemUi }

/**
 * The device actions Home's banners and safety row and the Engine screen share: one runs at a
 * time ([busy] is its key), its result goes to the snackbar, and the status is read again after
 * it ([onRefresh], again a few seconds later, when System UI reports the module).
 */
@Stable
class EngineActions internal constructor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val snackbar: (String) -> Unit,
    private val model: () -> HomeStatus?,
    private val onRefresh: () -> Unit,
) {
    /** The key of the action running now, or null */
    var busy by mutableStateOf<String?>(null)
        private set

    /** The question on screen, or null */
    var confirm by mutableStateOf<Confirm?>(null)

    /** The module is paused (the kill switch is on) */
    val paused: Boolean get() = model()?.hooksPaused == true

    /** Runs a device action once at a time; [block] returns the message to show. */
    fun act(key: String, needsRoot: Boolean = true, recheckAfter: Long = 6000L,
            block: suspend () -> String?) {
        if (busy != null) return
        if (needsRoot && model()?.rootGranted != true) {
            snackbar(context.getString(R.string.reborn_needs_root))
            return
        }
        scope.launch {
            busy = key
            val message = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e("RebornEngine", "$key failed", t)
                context.getString(R.string.reborn_failed)
            } finally {
                busy = null
            }
            message?.let(snackbar)
            onRefresh()
            if (recheckAfter > 0) {
                // System UI reports the module a few seconds after it starts again
                delay(recheckAfter)
                onRefresh()
            }
        }
    }

    /** The [act] key a fix runs under (null: it opens another app), for its banner's indicator. */
    fun actionKey(fix: Fix): String? = when (fix) {
        Fix.Engine -> "engine"
        Fix.RestartSystemUi -> "restart"
        Fix.ResetGuard -> "guard"
        Fix.ResumeHooks -> "pause"
        Fix.RootManager, Fix.Lsposed -> null
    }

    fun fix(fix: Fix) {
        when (fix) {
            Fix.RootManager -> scope.launch {
                if (!RebornActions.openRootManager(context)) {
                    snackbar(context.getString(R.string.reborn_no_root_manager))
                }
            }
            Fix.Lsposed -> openLsposed()
            Fix.Engine -> act("engine", needsRoot = false, recheckAfter = 0L) {
                RebornActions.useLsposedEngine()
                context.getString(R.string.reborn_engine_switched)
            }
            Fix.RestartSystemUi -> restartSystemUi()
            Fix.ResetGuard -> act("guard") {
                RebornActions.resetSystemUiGuard()
                context.getString(R.string.reborn_guard_reset)
            }
            Fix.ResumeHooks -> confirm = Confirm.Resume
        }
    }

    fun openLsposed() {
        scope.launch {
            if (!RebornActions.openLsposed(context)) {
                snackbar(context.getString(R.string.reborn_no_lsposed))
            }
        }
    }

    fun restartSystemUi() = act("restart") {
        RebornActions.restartSystemUi()
        context.getString(R.string.reborn_restarting_systemui)
    }

    /** Pause or resume the module, asking first. */
    fun askPause() {
        confirm = if (paused) Confirm.Resume else Confirm.Pause
    }
}

@Composable
fun rememberEngineActions(model: HomeStatus?, onRefresh: () -> Unit): EngineActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = rememberSnackbar()
    val currentModel by rememberUpdatedState(model)
    val refresh by rememberUpdatedState(onRefresh)
    return remember(context, scope, snackbar) {
        EngineActions(context, scope, snackbar, { currentModel }, { refresh() })
    }
}

/** The questions the engine actions ask: pause, resume, restart System UI and soft reboot. */
@Composable
fun EngineDialogs(actions: EngineActions) {
    val context = LocalContext.current
    when (actions.confirm) {
        Confirm.Pause -> ConfirmDialog(
            title = stringResource(R.string.reborn_pause_title),
            text = stringResource(R.string.reborn_pause_text),
            confirmLabel = stringResource(R.string.reborn_pause),
            icon = R.drawable.ic_reborn_pause,
            onDismiss = { actions.confirm = null },
            onConfirm = {
                actions.act("pause") {
                    context.getString(if (RebornActions.setHooksPaused(true)) R.string.reborn_paused_done
                    else R.string.reborn_failed)
                }
            },
        )
        Confirm.Resume -> ConfirmDialog(
            title = stringResource(R.string.reborn_resume_title),
            text = stringResource(R.string.reborn_resume_text),
            confirmLabel = stringResource(R.string.reborn_resume),
            icon = R.drawable.ic_reborn_play,
            onDismiss = { actions.confirm = null },
            onConfirm = {
                actions.act("pause") {
                    context.getString(if (RebornActions.setHooksPaused(false)) R.string.reborn_resumed_done
                    else R.string.reborn_failed)
                }
            },
        )
        Confirm.RestartSystemUi -> ConfirmDialog(
            title = stringResource(R.string.reborn_restart_title),
            text = stringResource(R.string.reborn_restart_text),
            confirmLabel = stringResource(R.string.reborn_restart_confirm),
            icon = R.drawable.ic_reborn_restart,
            onDismiss = { actions.confirm = null },
            onConfirm = { actions.restartSystemUi() },
        )
        Confirm.SoftReboot -> ConfirmDialog(
            title = stringResource(R.string.reborn_soft_reboot_title),
            text = stringResource(R.string.reborn_soft_reboot_text),
            confirmLabel = stringResource(R.string.reborn_soft_reboot),
            icon = R.drawable.ic_reborn_power,
            destructive = true,
            onDismiss = { actions.confirm = null },
            onConfirm = {
                actions.act("reboot", recheckAfter = 0L) {
                    RebornActions.softReboot()
                    null
                }
            },
        )
        null -> Unit
    }
}
