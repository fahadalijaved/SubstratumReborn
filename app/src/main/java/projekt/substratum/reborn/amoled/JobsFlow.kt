/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.amoled

import android.content.Context
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import projekt.substratum.common.platform.AmoledJobs
import projekt.substratum.reborn.RebornShortcuts

/** The engine job on screen: its kind, the app it is at, its progress and what waits behind it. */
@Immutable
data class JobUi(
    val id: Int,
    val kind: AmoledJobs.Kind,
    val label: String?,
    val done: Int,
    val total: Int,
    val queued: Int,
) {
    /** Progress has been reported */
    val started: Boolean get() = total > 0
    val fraction: Float get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
}

/**
 * The engine's job queue for the whole process, as one [StateFlow]: fed by one
 * [AmoledJobs.Listener] registered once, so Home and a theme's page follow an apply while the
 * Studio is not on screen. The Studio reads its progress from here too. It also keeps the
 * launcher's "Customize" shortcut in step with the engine ([RebornShortcuts]).
 */
object JobsFlow {

    /** A job asked for and not reported started yet */
    internal const val PENDING_JOB = -1

    private val _job = MutableStateFlow<JobUi?>(null)
    val job: StateFlow<JobUi?> = _job.asStateFlow()

    @Volatile
    private var app: Context? = null

    private val listener = object : AmoledJobs.Listener {
        override fun onJobStarted(job: AmoledJobs.Job) {
            _job.value = JobUi(job.id, job.kind, null, 0, 0, AmoledJobs.snapshot().queued)
        }

        override fun onProgress(job: AmoledJobs.Job, pkg: String?, done: Int, total: Int) {
            _job.value = JobUi(job.id, job.kind, pkg?.let { AmoledJobs.labelOf(it) }, done, total,
                AmoledJobs.snapshot().queued)
        }

        override fun onJobFinished(job: AmoledJobs.Job, ok: Boolean, message: String?) {
            val context = app ?: return
            when (job.kind) {
                AmoledJobs.Kind.APPLY_ALL -> if (ok) RebornShortcuts.publishCustomize(context)
                AmoledJobs.Kind.REMOVE -> if (ok) RebornShortcuts.removeCustomize(context)
                else -> Unit
            }
        }

        override fun onQueueIdle() {
            _job.value = null
        }
    }

    init {
        AmoledJobs.addListener(listener)
    }

    /** Called once the app is up (the Reborn shell): the shortcut needs a context. */
    fun start(context: Context) {
        app = context.applicationContext
        sync()
    }

    /** Catches up with the queue (a job may have started before anyone listened). */
    fun sync() {
        val snapshot = AmoledJobs.snapshot()
        val current = snapshot.current
        when {
            current != null -> if (_job.value?.id != current.id) {
                _job.value = JobUi(current.id, current.kind, null, 0, 0, snapshot.queued)
            }
            snapshot.queued == 0 && _job.value?.id != PENDING_JOB -> _job.value = null
        }
    }

    /** Shows [kind] as starting at once, before it is queued (the listener takes over when it starts). */
    internal fun pending(kind: AmoledJobs.Kind) {
        if (_job.value == null) _job.value = JobUi(PENDING_JOB, kind, null, 0, 0, 0)
    }

    /** The job asked for never started (it failed to queue, or nothing came of it). */
    internal fun clearPending() {
        if (_job.value?.id == PENDING_JOB) _job.value = null
    }

    internal val isPending: Boolean get() = _job.value?.id == PENDING_JOB
}
