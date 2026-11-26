/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.components

import android.util.Log
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "Reborn"

/**
 * Runs [block] on [Dispatchers.IO] (root shell, SQLite, files, PackageManager) and holds its
 * result; [initial] (the last known result, or null) until the first result arrives. A change of
 * [keys] runs it again and keeps the previous result on screen meanwhile.
 */
@Composable
fun <T> rememberRoot(vararg keys: Any?, initial: T? = null, block: () -> T): State<T?> {
    val latest by rememberUpdatedState(block)
    return produceState(initialValue = initial, *keys) {
        val result = withContext(Dispatchers.IO) {
            try {
                latest()
            } catch (t: Throwable) {
                Log.e(TAG, "background read failed", t)
                null
            }
        }
        if (result != null) value = result
    }
}

/** Runs a blocking root action off the main thread. */
suspend fun <T> onIo(block: () -> T): T = withContext(Dispatchers.IO) { block() }

/**
 * Counts returns to the screen (onResume after the first), to read the state again after the
 * user comes back from LSPosed, KernelSU or Classic.
 */
@Composable
fun rememberResumeCount(): Int {
    val owner = LocalLifecycleOwner.current
    var count by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        var first = true
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (first) first = false else count++
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return count
}

val LocalSnackbar = staticCompositionLocalOf<SnackbarHostState> {
    error("No snackbar host")
}

/** Shows a message in the screen's snackbar host, replacing the one on screen. */
@Composable
fun rememberSnackbar(): (String) -> Unit {
    val host = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    return remember(host, scope) {
        val show: (String) -> Unit = { message ->
            scope.launch {
                host.currentSnackbarData?.dismiss()
                host.showSnackbar(message)
            }
        }
        show
    }
}

@Composable
fun RebornSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data ->
        Snackbar(data, shape = MaterialTheme.shapes.large)
    }
}
