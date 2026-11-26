/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.setup

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import projekt.substratum.R
import projekt.substratum.common.platform.RebornStatus
import projekt.substratum.reborn.RebornActions
import projekt.substratum.reborn.RebornCache
import projekt.substratum.reborn.components.ChipState
import projekt.substratum.reborn.components.Entrance
import projekt.substratum.reborn.components.RebornSnackbarHost
import projekt.substratum.reborn.components.SectionCard
import projekt.substratum.reborn.components.ShapedIcon
import projekt.substratum.reborn.components.StatusChip
import projekt.substratum.reborn.components.rememberResumeCount
import projekt.substratum.reborn.components.rememberRoot
import projekt.substratum.reborn.components.LocalSnackbar
import projekt.substratum.reborn.components.rememberSnackbar
import projekt.substratum.reborn.components.statusColors

private enum class StepFix { RootManager, Lsposed, RestartSystemUi, ResetGuard, ResumeHooks }

private data class Step(
    val title: Int,
    val text: Int,
    val state: ChipState,
    val value: String,
    val fix: StepFix?,
    val detail: String? = null,
)

/** Labels for the packages the module's scope needs. */
private fun scopeLabel(pkg: String): Int = when (pkg) {
    RebornStatus.SYSTEMUI -> R.string.reborn_scope_systemui
    RebornStatus.SETTINGS -> R.string.reborn_scope_settings
    else -> R.string.reborn_scope_self
}

private fun steps(context: android.content.Context, s: RebornStatus.Snapshot): List<Step> {
    val root = s.root
    val lsp = s.lsposed
    val hb = s.heartbeat
    val list = ArrayList<Step>()
    list += Step(
        R.string.reborn_step_root, R.string.reborn_step_root_text,
        if (root.granted) ChipState.OK else ChipState.Error,
        if (root.granted) root.version ?: "root" else context.getString(R.string.reborn_value_not_granted),
        if (root.granted) null else StepFix.RootManager,
    )
    list += when (lsp.level) {
        RebornStatus.Level.OK -> Step(
            R.string.reborn_step_module, R.string.reborn_step_module_text, ChipState.OK,
            context.getString(R.string.reborn_value_enabled), null,
        )
        RebornStatus.Level.ERROR -> Step(
            R.string.reborn_step_module, R.string.reborn_step_module_text, ChipState.Error,
            context.getString(if (lsp.registered) R.string.reborn_value_disabled else R.string.reborn_value_not_added),
            StepFix.Lsposed,
        )
        else -> Step(
            R.string.reborn_step_module, R.string.reborn_step_module_text, ChipState.Unknown,
            context.getString(if (root.granted) R.string.reborn_value_unreadable else R.string.reborn_value_needs_root),
            if (root.granted) StepFix.Lsposed else null,
        )
    }
    val missing = lsp.missingScope()
    list += when {
        lsp.level == RebornStatus.Level.UNKNOWN -> Step(
            R.string.reborn_step_scope, R.string.reborn_step_scope_text, ChipState.Unknown,
            context.getString(if (root.granted) R.string.reborn_value_unreadable else R.string.reborn_value_needs_root),
            if (root.granted) StepFix.Lsposed else null,
        )
        missing.isEmpty() -> Step(
            R.string.reborn_step_scope, R.string.reborn_step_scope_text, ChipState.OK,
            context.resources.getQuantityString(R.plurals.reborn_value_scope_apps, lsp.scope.size, lsp.scope.size),
            null,
        )
        else -> Step(
            R.string.reborn_step_scope, R.string.reborn_step_scope_text, ChipState.Warn,
            context.resources.getQuantityString(R.plurals.reborn_value_scope_missing, missing.size, missing.size),
            StepFix.Lsposed,
            detail = context.getString(R.string.reborn_scope_missing_list,
                missing.joinToString("\n") { "•  " + context.getString(scopeLabel(it)) + "  (" + it + ")" }),
        )
    }
    val hooks = when {
        s.hooksPaused -> Step(
            R.string.reborn_step_hooks, R.string.reborn_step_hooks_text, ChipState.Warn,
            context.getString(R.string.reborn_value_paused), StepFix.ResumeHooks,
        )
        hb.fresh() -> Step(
            R.string.reborn_step_hooks, R.string.reborn_step_hooks_text, ChipState.OK,
            context.getString(R.string.reborn_value_hooked), null,
        )
        hb.guardTripped() -> Step(
            R.string.reborn_step_hooks, R.string.reborn_step_hooks_text, ChipState.Error,
            context.getString(R.string.reborn_value_guard_tripped), StepFix.ResetGuard,
        )
        hb.present && hb.sinceBoot && !hb.current -> Step(
            R.string.reborn_step_hooks, R.string.reborn_step_hooks_text, ChipState.Warn,
            context.getString(R.string.reborn_value_old_build, hb.version), StepFix.RestartSystemUi,
        )
        else -> Step(
            R.string.reborn_step_hooks, R.string.reborn_step_hooks_text, ChipState.Error,
            context.getString(if (hb.present && hb.sinceBoot && hb.guardOff()) R.string.reborn_value_paused
            else R.string.reborn_value_no_heartbeat),
            StepFix.RestartSystemUi,
        )
    }
    // Restarting System UI needs root: without it step 1 is the fix
    list += if (root.granted) hooks else hooks.copy(fix = null)
    return list
}

/**
 * The first-run checklist, also reachable from Home: root, the LSPosed module, its scope and the
 * System UI heartbeat, each checked live with a Fix. Replaces the old splash, intro slides and
 * permission dialog.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SetupScreen(onDone: () -> Unit) {
    BackHandler(onBack = onDone)
    val context = LocalContext.current
    val snackbarState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resume = rememberResumeCount()
    var refresh by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val status by rememberRoot(resume, refresh, initial = RebornCache.status) {
        RebornStatus.read(context).also { RebornCache.status = it }
    }
    val list = remember(status) { status?.let { steps(context, it) } }
    val done = list?.count { it.state == ChipState.OK } ?: 0
    val progress by animateFloatAsState(done / 4f, label = "setup")

    androidx.compose.runtime.CompositionLocalProvider(LocalSnackbar provides snackbarState) {
        val snackbar = rememberSnackbar()

        fun recheck(after: Long) = scope.launch {
            refresh++
            delay(after)
            refresh++
        }

        fun fix(fix: StepFix) {
            if (busy) return
            scope.launch {
                busy = true
                try {
                    when (fix) {
                        StepFix.RootManager -> if (!RebornActions.openRootManager(context)) {
                            snackbar(context.getString(R.string.reborn_no_root_manager))
                        }
                        StepFix.Lsposed -> if (!RebornActions.openLsposed(context)) {
                            snackbar(context.getString(R.string.reborn_no_lsposed))
                        }
                        StepFix.RestartSystemUi -> {
                            RebornActions.restartSystemUi()
                            snackbar(context.getString(R.string.reborn_restarting_systemui))
                            recheck(6000L)
                        }
                        StepFix.ResetGuard -> {
                            RebornActions.resetSystemUiGuard()
                            snackbar(context.getString(R.string.reborn_guard_reset))
                            recheck(6000L)
                        }
                        StepFix.ResumeHooks -> {
                            val resumed = RebornActions.setHooksPaused(false)
                            snackbar(context.getString(if (resumed) R.string.reborn_resumed_done else R.string.reborn_failed))
                            recheck(6000L)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    Log.e("RebornSetup", "fix failed", t)
                    snackbar(context.getString(R.string.reborn_failed))
                } finally {
                    busy = false
                }
            }
        }

        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        val background = MaterialTheme.colorScheme.background
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = background,
            snackbarHost = { RebornSnackbarHost(snackbarState) },
            topBar = {
                LargeFlexibleTopAppBar(
                    title = { Text(stringResource(R.string.reborn_setup_title), fontWeight = FontWeight.Bold) },
                    subtitle = { Text(stringResource(R.string.reborn_setup_subtitle, done)) },
                    navigationIcon = {
                        IconButton(onClick = onDone) {
                            Icon(painterResource(R.drawable.ic_reborn_close), stringResource(R.string.reborn_close))
                        }
                    },
                    actions = {
                        IconButton(onClick = { refresh++ }) {
                            Icon(painterResource(R.drawable.ic_reborn_refresh), stringResource(R.string.reborn_recheck))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = background,
                        scrolledContainerColor = background,
                    ),
                    scrollBehavior = scrollBehavior,
                )
            },
            bottomBar = {
                Surface(color = background) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                    ) {
                        if (done == 4) {
                            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                                Text(stringResource(R.string.reborn_setup_done), style = MaterialTheme.typography.titleMedium)
                            }
                        } else {
                            OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                                Text(stringResource(R.string.reborn_setup_skip), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 4.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "progress") {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        LinearWavyProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.reborn_setup_intro),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (list == null) {
                    item(key = "loading") {
                        SectionCard {
                            Text(stringResource(R.string.reborn_checking_text), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                } else {
                    itemsIndexed(list, key = { index, _ -> "step$index" }) { index, step ->
                        Entrance(index) {
                            StepCard(index + 1, step, busy) { step.fix?.let(::fix) }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StepCard(number: Int, step: Step, busy: Boolean, onFix: () -> Unit) {
    val colors = statusColors(step.state)
    val ok = step.state == ChipState.OK
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                ShapedIcon(
                    icon = if (ok) R.drawable.ic_reborn_check else step.state.icon,
                    container = if (ok) colors.accent else colors.container,
                    content = if (ok) colors.onAccent else colors.accent,
                    size = 48.dp,
                    shape = if (ok) MaterialShapes.Cookie9Sided.toShape() else MaterialShapes.Circle.toShape(),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.reborn_step_number, number),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(step.title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(step.text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusChip(step.value, step.state, Modifier.weight(1f, fill = false))
            Spacer(Modifier.weight(1f))
            if (step.fix != null) {
                FilledTonalButton(onClick = onFix, enabled = !busy) {
                    Icon(painterResource(R.drawable.ic_reborn_open), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.reborn_fix))
                }
            }
        }
        if (step.detail != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = step.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onContainer,
            )
        }
    }
}
