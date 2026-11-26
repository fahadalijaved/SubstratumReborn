/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.amoled

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import projekt.substratum.R
import projekt.substratum.Substratum
import projekt.substratum.common.Packages
import projekt.substratum.common.platform.AmoledChange
import projekt.substratum.common.platform.AmoledJobs
import projekt.substratum.common.platform.AmoledLevel
import projekt.substratum.common.platform.AmoledPalette
import projekt.substratum.common.platform.AmoledPresets
import projekt.substratum.common.platform.AmoledPreview
import projekt.substratum.common.platform.AmoledRules
import projekt.substratum.common.platform.AmoledState
import projekt.substratum.common.platform.AmoledUserRules
import projekt.substratum.common.platform.QsOptions
import projekt.substratum.common.platform.RebornStatus
import projekt.substratum.common.platform.SystemAmoled
import projekt.substratum.common.platform.XposedBackend
import projekt.substratum.reborn.RebornActions
import projekt.substratum.reborn.RebornCache
import projekt.substratum.reborn.components.mock.MockRoles
import projekt.substratum.util.helpers.Root
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Everything AMOLED Studio shows, for the whole process: the state file, the running job and its
 * progress (fed by an [AmoledJobs.Listener] while a Studio screen is attached), the app rows, the
 * look and its live preview, the rules themes and the LSPosed scope.
 *
 * Every read runs on [Dispatchers.IO] (files, PackageManager, the status root shell), through one
 * conflated request queue per kind: at most one read of each kind runs, and a request made
 * meanwhile reads again once afterwards, so the last answer always wins. Flows are only written on
 * the main thread.
 */
object StudioState {

    private const val TAG = "RebornStudio"
    /** A job asked for and not reported started yet */
    private const val PENDING_JOB = -1

    /** The active quick settings tile options, in the order the designer shows them */
    val TILE_OPTIONS = listOf(AmoledRules.TILE_WHITE, AmoledRules.TILE_ACCENT, AmoledRules.TILE_BLUE,
        AmoledRules.TILE_STOCK)

    @Immutable
    data class Summary(
        val enabled: Boolean,
        val themed: Int,
        val total: Int,
        val appliedAt: Long,
        val restoredAt: Long,
        val survivedReboot: Boolean,
        val restartNeeded: Int,
        val rulesTheme: String?,
        /** The look of the last apply, or null when it predates looks */
        val appliedLevel: String?,
        val appliedAccent: Int?,
    )

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

    @Immutable
    data class AppRow(
        val pkg: String,
        val label: String,
        val route: AmoledState.Route,
        val enabled: Boolean,
        val active: Boolean,
        val restartNeeded: Boolean,
        val colors: Int,
        val error: String?,
        val level: String?,
        /** Updated since its colours were read, or read with another level */
        val changed: Boolean,
    ) {
        val themed: Boolean
            get() = enabled && (route == AmoledState.Route.OVERLAY || route == AmoledState.Route.MODULE)
        /** Lower case label and package, for search */
        val search: String = (label + '\n' + pkg).lowercase()
    }

    @Immutable
    data class Look(
        val preset: String,
        val level: AmoledLevel,
        val accent: String,
        val accentLight: Boolean,
        val tile: String,
        /** The level was chosen here; otherwise it is the rules theme's own */
        val levelChosen: Boolean = true,
        /** The accent was chosen here; otherwise it is the rules theme's own */
        val accentChosen: Boolean = true,
    )

    @Immutable
    data class RulesTheme(val pkg: String, val label: String)

    @Immutable
    data class Previews(
        /** The saved look */
        val look: MockRoles,
        /** Each preset, by id */
        val presets: Map<String, MockRoles>,
        /** The saved look with each active tile option, by option */
        val tiles: Map<String, MockRoles>,
        /** The level Apply uses (the rules theme's where none was chosen here) */
        val level: AmoledLevel,
        /** The accent choice Apply uses, or null for none */
        val accent: String?,
    )

    @Immutable
    data class QsSaved(val aspect: Float, val shade: Boolean, val track: Boolean)

    /** A message for the snackbar; [kind] is the job it reports, if any. */
    class Event(val message: String, val kind: AmoledJobs.Kind? = null, val pkg: String? = null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var app: Context

    private val _summary = MutableStateFlow<Summary?>(null)
    val summary: StateFlow<Summary?> = _summary.asStateFlow()
    private val _job = MutableStateFlow<JobUi?>(null)
    val job: StateFlow<JobUi?> = _job.asStateFlow()
    private val _apps = MutableStateFlow<List<AppRow>?>(null)
    val apps: StateFlow<List<AppRow>?> = _apps.asStateFlow()
    private val _look = MutableStateFlow<Look?>(null)
    val look: StateFlow<Look?> = _look.asStateFlow()
    private val _previews = MutableStateFlow<Previews?>(null)
    val previews: StateFlow<Previews?> = _previews.asStateFlow()
    private val _scope = MutableStateFlow<Set<String>?>(null)
    /** The LSPosed module's scope, or null while unknown */
    val lsposedScope: StateFlow<Set<String>?> = _scope.asStateFlow()
    private val _themes = MutableStateFlow<List<RulesTheme>>(emptyList())
    val themes: StateFlow<List<RulesTheme>> = _themes.asStateFlow()
    private val _theme = MutableStateFlow<String?>(null)
    /** The rules theme Apply uses, or null for the built-in rules */
    val theme: StateFlow<String?> = _theme.asStateFlow()
    private val _lookDirty = MutableStateFlow(false)
    /** The look or the rules changed since the last apply in ways the state file cannot tell */
    val lookDirty: StateFlow<Boolean> = _lookDirty.asStateFlow()
    private val _qs = MutableStateFlow<QsSaved?>(null)
    val qs: StateFlow<QsSaved?> = _qs.asStateFlow()
    private val _records = MutableStateFlow(0)
    /** Goes up whenever a job finishes: change records may be new */
    val records: StateFlow<Int> = _records.asStateFlow()
    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 8)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    private var attached = 0
    private var themeChosen = false
    private var scopeWanted = true
    /** Switches made here that a read has not seen yet: shown over what it read */
    private val switched = HashMap<String, Boolean>()
    /** The rules theme's own level and accent (from the last preview), for parts never chosen */
    private var themeLevel: AmoledLevel? = null
    private var themeAccent: String? = null
    /** Quick settings options being written: shown instead of the file until the write is done */
    private var qsWrite: QsSaved? = null
    @Volatile
    private var stock: AmoledPreview.Stock? = null
    private val labels = ConcurrentHashMap<String, String>()

    private val summaryReads = Channel<Unit>(Channel.CONFLATED)
    private val appReads = Channel<Unit>(Channel.CONFLATED)
    private val previewReads = Channel<Unit>(Channel.CONFLATED)
    private val otherReads = Channel<Unit>(Channel.CONFLATED)

    private val listener = object : AmoledJobs.Listener {
        override fun onJobStarted(job: AmoledJobs.Job) {
            _job.value = JobUi(job.id, job.kind, null, 0, 0, AmoledJobs.snapshot().queued)
        }

        override fun onProgress(job: AmoledJobs.Job, pkg: String?, done: Int, total: Int) {
            _job.value = JobUi(job.id, job.kind, pkg?.let { AmoledJobs.labelOf(it) }, done, total,
                AmoledJobs.snapshot().queued)
        }

        override fun onJobFinished(job: AmoledJobs.Job, ok: Boolean, message: String?) {
            if (ok && job.kind == AmoledJobs.Kind.APPLY_ALL) {
                _lookDirty.value = false
                themeChosen = false
            }
            if (message != null) _events.tryEmit(Event(message, job.kind, job.packageName()))
            _records.value++
            summaryReads.trySend(Unit)
            appReads.trySend(Unit)
        }

        override fun onQueueIdle() {
            _job.value = null
            // The device's own stock colours may be known now
            stock = null
            refreshAll(scope = false)
        }
    }

    init {
        consume(summaryReads) { readSummary() }
        consume(appReads) { readApps() }
        consume(previewReads) { readPreviews() }
        consume(otherReads) { readOthers() }
    }

    private fun consume(requests: Channel<Unit>, read: suspend () -> Unit) {
        scope.launch {
            for (request in requests) {
                try {
                    read()
                } catch (t: Throwable) {
                    // Only a cancelled scope ends the loop: a CancellationException thrown by some
                    // API inside a read must not stop this kind of read for the whole process
                    currentCoroutineContext().ensureActive()
                    Log.e(TAG, "read failed", t)
                }
            }
        }
    }

    // Attaching (main thread)

    /** A Studio screen is shown: follow the queue and read everything. */
    fun attach(context: Context) {
        app = context.applicationContext
        if (attached++ == 0) AmoledJobs.addListener(listener)
        syncJob()
        if (_look.value == null) _look.value = readLook()
        if (_scope.value == null) {
            // An unread scope is empty, not "nothing in scope": every module app would need scope
            _scope.value = RebornCache.status?.lsposed
                ?.takeIf { it.level != RebornStatus.Level.UNKNOWN }?.scope?.toSet()
        }
        refreshAll()
    }

    fun detach() {
        if (attached > 0 && --attached == 0) AmoledJobs.removeListener(listener)
    }

    /** @param scope Read the LSPosed scope again too (a copy of its database) */
    fun refreshAll(scope: Boolean = true) {
        if (!::app.isInitialized) return
        if (scope) scopeWanted = true
        summaryReads.trySend(Unit)
        appReads.trySend(Unit)
        previewReads.trySend(Unit)
        otherReads.trySend(Unit)
    }

    fun refreshApps() = appReads.trySend(Unit)

    /** A screen asked for a rules theme (a theme's card, the Classic menu). */
    fun requestTheme(pkg: String?) {
        if (pkg == null) return
        _theme.value = pkg
        themeChosen = true
        if (::app.isInitialized) previewReads.trySend(Unit)
    }

    private fun syncJob() {
        val snapshot = AmoledJobs.snapshot()
        val current = snapshot.current
        when {
            current != null -> if (_job.value?.id != current.id) {
                _job.value = JobUi(current.id, current.kind, null, 0, 0, snapshot.queued)
            }
            snapshot.queued == 0 && _job.value?.id != PENDING_JOB -> _job.value = null
        }
    }

    // Reads (IO, results set on the main thread)

    private suspend fun readSummary() {
        val context = app
        val state = withContext(Dispatchers.IO) { AmoledState.read(context) }
        _summary.value = Summary(
            enabled = state.enabled,
            themed = state.countThemed(),
            total = state.perApp.size,
            appliedAt = state.appliedAt,
            restoredAt = state.restoredAt,
            survivedReboot = state.survivedReboot(),
            restartNeeded = state.perApp.values.count { it.userEnabled && it.restartNeeded },
            rulesTheme = state.rulesTheme,
            appliedLevel = state.level,
            appliedAccent = state.accent,
        )
    }

    private suspend fun readApps() {
        val context = app
        val rows = withContext(Dispatchers.IO) { loadApps(context) }
        // A switch the state file has caught up with needs no override any more
        rows.forEach { row -> if (switched[row.pkg] == row.enabled) switched.remove(row.pkg) }
        _apps.value = if (switched.isEmpty()) rows else rows.map { row ->
            switched[row.pkg]?.let { row.copy(enabled = it) } ?: row
        }
    }

    private fun loadApps(context: Context): List<AppRow> {
        if (!Root.checkRootAccess()) return emptyList()
        // The status shell: never waits behind a running job
        val list = SystemAmoled.listApps(context, true)
        val state = AmoledState.read(context)
        // The level a re-apply would use: the chosen one, else the rules theme's (as last applied)
        val level = AmoledPresets.look(prefs()).level?.key() ?: state.level ?: AmoledLevel.PURE.key()
        val pm = context.packageManager
        return list.map { app ->
            val pkg = app.packageName
            val info = try {
                pm.getPackageInfo(pkg, 0)
            } catch (e: Exception) {
                null
            }
            val label = labels[pkg] ?: (info?.applicationInfo?.loadLabel(pm)?.toString() ?: pkg)
                .also { labels[pkg] = it }
            val entry = state.perApp[pkg]
            val key = info?.applicationInfo?.let {
                XposedBackend.versionKey(it.sourceDir, PackageInfoCompat.getLongVersionCode(info))
            }
            val colored = app.enabled && (app.route == AmoledState.Route.OVERLAY ||
                app.route == AmoledState.Route.MODULE)
            val changed = colored && entry != null &&
                ((key != null && entry.versionKey != null && key != entry.versionKey) ||
                    (entry.level != null && entry.level != level))
            AppRow(
                pkg = pkg,
                label = label,
                route = app.route,
                enabled = app.enabled,
                active = app.active,
                restartNeeded = app.restartNeeded,
                colors = app.colors,
                error = app.error,
                level = entry?.level,
                changed = changed,
            )
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    private suspend fun readPreviews() {
        val context = app
        val look = readLook()
        _look.value = look
        val theme = _theme.value
        val previews = withContext(Dispatchers.IO) { computePreviews(context, theme, look) }
        _previews.value = previews
        // What was never chosen here follows the rules theme: the controls show what Apply uses
        val accent = previews.accent ?: AmoledPresets.ACCENT_NONE
        if (previews.level != themeLevel || accent != themeAccent) {
            themeLevel = previews.level
            themeAccent = accent
            if (!look.levelChosen || !look.accentChosen) _look.value = readLook()
        }
    }

    private fun computePreviews(context: Context, theme: String?, look: Look): Previews {
        val stock = stock ?: AmoledPreview.stock(context).also { stock = it }
        val prefs = prefs()
        val monet = AmoledPresets.monetAccent(context)
        val user = AmoledUserRules.read(context)
        fun rules(): AmoledRules {
            val base = theme?.let {
                try {
                    AmoledRules.fromTheme(context, it)
                } catch (e: Exception) {
                    null
                }
            } ?: AmoledRules.defaults()
            return base.withUserOptions(prefs, monet)
        }
        val savedRules = rules().withUserRules(user)
        val saved = MockRoles(AmoledPreview.roles(stock, savedRules))
        val presets = AmoledPresets.PRESETS.associate { preset ->
            val tile = preset.tile ?: if (look.tile == AmoledRules.TILE_ACCENT &&
                preset.accent == AmoledPresets.ACCENT_NONE) AmoledRules.TILE_WHITE else look.tile
            preset.id to MockRoles(AmoledPreview.roles(stock,
                rules().withLook(preset.level, preset.accent, null, tile, monet).withUserRules(user)))
        }
        // Only the quick settings roles differ between tile options
        val tiles = TILE_OPTIONS.associateWith { tile ->
            val roles = AmoledPreview.roles(stock, rules().withLook(null, null, null, tile, monet).withUserRules(user))
            saved.with(roles.filterKeys { it == AmoledPreview.QS_TILE_ON || it == AmoledPreview.QS_TILE_ICON_ON })
        }
        return Previews(saved, presets, tiles, savedRules.level() ?: AmoledLevel.PURE, savedRules.accentChoice())
    }

    private suspend fun readOthers() {
        val context = app
        val qs = withContext(Dispatchers.IO) { QsOptions.read() }
        // A write still waiting (behind a running job) is what the file is about to say
        _qs.value = qsWrite ?: QsSaved(if (qs.aspect > 0) qs.aspect else QsOptions.STOCK_ASPECT, qs.shade, qs.track)
        val themes = withContext(Dispatchers.IO) { loadThemes(context) }
        _themes.value = themes
        if (!themeChosen) {
            val state = withContext(Dispatchers.IO) { AmoledState.read(context) }
            val installed = themes.map { it.pkg }.toSet()
            val chosen = if (state.appliedAt > 0) state.rulesTheme?.takeIf { it in installed }
            else RebornActions.AMOLED_THEME.takeIf { it in installed }
            if (!themeChosen && chosen != _theme.value) {
                _theme.value = chosen
                previewReads.trySend(Unit)
            }
        }
        if (!scopeWanted) return
        scopeWanted = false
        val root = withContext(Dispatchers.IO) { Root.checkRootAccess() }
        if (root) {
            val lsposed = withContext(Dispatchers.IO) { RebornStatus.lsposed(context, true) }
            if (lsposed.level != RebornStatus.Level.UNKNOWN) _scope.value = lsposed.scope.toSet()
        }
    }

    private fun loadThemes(context: Context): List<RulesTheme> =
        Packages.getSubstratumPackages(context, null).orEmpty()
            .mapNotNull { (name, data) ->
                val pkg = data.getOrNull(1) ?: return@mapNotNull null
                if (!AmoledRules.isRulesTheme(context, pkg)) return@mapNotNull null
                RulesTheme(pkg, Packages.getPackageName(context, pkg) ?: name ?: pkg)
            }
            .sortedBy { it.label.lowercase() }

    // The look (main thread: preferences)

    private fun prefs(): SharedPreferences = Substratum.getPreferences()

    private fun readLook(): Look {
        val prefs = prefs()
        val look = AmoledPresets.look(prefs)
        val level = look.level ?: themeLevel ?: AmoledLevel.PURE
        val accent = look.accent ?: themeAccent ?: AmoledPresets.ACCENT_NONE
        val tile = prefs.getString(AmoledRules.PREF_ACTIVE_TILE, AmoledRules.TILE_WHITE) ?: AmoledRules.TILE_WHITE
        val preset = look.preset ?: AmoledPresets.matching(level, accent, tile)
        return Look(preset, level, accent, look.accentLight == true, tile,
            levelChosen = look.level != null, accentChosen = look.accent != null)
    }

    fun choosePreset(id: String) {
        AmoledPresets.choose(prefs(), id)
        lookChanged()
    }

    fun chooseLevel(level: AmoledLevel) {
        keepThemeLook()
        AmoledPresets.setLevel(prefs(), level)
        lookChanged()
    }

    fun chooseAccent(accent: String) {
        keepThemeLook()
        AmoledPresets.setAccent(prefs(), accent)
        lookChanged()
    }

    /**
     * Choosing a level saves an accent too, and the other way round (none, Pure when nothing was
     * chosen): the rules theme's own is saved first, so choosing one part keeps what the other
     * shows.
     */
    private fun keepThemeLook() {
        val prefs = prefs()
        val look = AmoledPresets.look(prefs)
        val edit = prefs.edit()
        if (look.level == null) themeLevel?.let { edit.putString(AmoledPresets.PREF_LEVEL, it.key()) }
        if (look.accent == null) themeAccent?.let { edit.putString(AmoledPresets.PREF_ACCENT, it) }
        edit.apply()
    }

    fun setAccentLight(on: Boolean) {
        AmoledPresets.setAccentLight(prefs(), on)
        lookChanged()
    }

    fun chooseTheme(pkg: String?) {
        if (pkg == _theme.value) return
        _theme.value = pkg
        themeChosen = true
        previewReads.trySend(Unit)
    }

    private fun lookChanged() {
        _look.value = readLook()
        _lookDirty.value = true
        previewReads.trySend(Unit)
    }

    /**
     * The saved look differs from the one last applied (as far as the state file tells: level and
     * accent), another rules theme was chosen, or something the state file cannot tell changed
     * since ([lookDirty]). A level or accent never chosen here is the rules theme's, which the
     * last apply used too: only chosen ones are compared.
     */
    fun pending(summary: Summary?, look: Look?, dirty: Boolean, theme: String?): Boolean {
        if (summary == null || !summary.enabled) return false
        if (dirty) return true
        if (themeChosen && theme != summary.rulesTheme) return true
        if (look == null || summary.appliedLevel == null) return false
        if (look.levelChosen && summary.appliedLevel != look.level.key()) return true
        if (!look.accentChosen) return false
        val monet = if (::app.isInitialized) AmoledPresets.monetAccent(app) else null
        return AmoledPresets.resolveAccent(look.accent, monet) != summary.appliedAccent
    }

    // Device actions: jobs run in SystemAmoledService; these return at once

    private fun say(message: String, kind: AmoledJobs.Kind? = null) {
        _events.tryEmit(Event(message, kind))
    }

    private fun string(id: Int, vararg args: Any): String = app.getString(id, *args)

    /** Runs [block] on IO once root is known to work; says so otherwise. */
    private fun withRoot(block: suspend () -> Unit) {
        scope.launch {
            val root = withContext(Dispatchers.IO) { Root.checkRootAccess() }
            if (!root) {
                say(string(R.string.system_amoled_needs_root))
                return@launch
            }
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "action failed", t)
                say(string(R.string.reborn_failed))
            }
        }
    }

    /**
     * Shows a job as starting at once, before it is queued (the listener takes over when it
     * starts), then queues it with [start] on IO.
     */
    private suspend fun queue(kind: AmoledJobs.Kind, start: () -> Unit) {
        if (_job.value == null) _job.value = JobUi(PENDING_JOB, kind, null, 0, 0, 0)
        scope.launch {
            delay(10_000)
            if (_job.value?.id == PENDING_JOB && AmoledJobs.snapshot().isIdle) _job.value = null
        }
        try {
            withContext(Dispatchers.IO) { start() }
        } catch (t: Throwable) {
            if (_job.value?.id == PENDING_JOB) _job.value = null
            throw t
        }
    }

    fun applyAll() {
        val theme = _theme.value
        withRoot {
            queue(AmoledJobs.Kind.APPLY_ALL) { AmoledJobs.applyAll(app, theme) }
            say(string(R.string.reborn_studio_applying))
        }
    }

    fun remove() {
        withRoot {
            queue(AmoledJobs.Kind.REMOVE) { AmoledJobs.remove(app) }
            say(string(R.string.reborn_studio_removing))
        }
    }

    /** Apps updated since their colours were read, or read with another level. */
    fun reapplyChanged() {
        val rows = _apps.value
        if (rows == null) {
            say(string(R.string.reborn_studio_apps_loading))
            refreshApps()
            return
        }
        val changed = rows.filter { it.changed }.map { it.pkg }
        if (changed.isEmpty()) {
            say(string(R.string.reborn_studio_nothing_changed))
            return
        }
        withRoot {
            queue(AmoledJobs.Kind.APPLY_ONE) { AmoledJobs.applyOne(app, changed) }
            say(string(R.string.reborn_studio_reapplying_changed, changed.size))
        }
    }

    fun reapply(row: AppRow) {
        withRoot {
            queue(AmoledJobs.Kind.APPLY_ONE) { AmoledJobs.applyOne(app, row.pkg) }
            say(string(R.string.reborn_studio_reapplying_app, row.label))
        }
    }

    /** Switch one app; the row shows the new state at once. */
    fun setEnabled(row: AppRow, on: Boolean) {
        switched[row.pkg] = on
        _apps.value = _apps.value?.map { if (it.pkg == row.pkg) it.copy(enabled = on) else it }
        scope.launch {
            try {
                // Saves the switch (a file) and queues the job
                withContext(Dispatchers.IO) { AmoledJobs.setEnabled(app, row.pkg, on) }
            } catch (t: Throwable) {
                Log.e(TAG, "switch failed", t)
                switched.remove(row.pkg)
                say(string(R.string.reborn_failed))
                appReads.trySend(Unit)
            }
        }
    }

    /** @return Whether it was restarted */
    suspend fun restart(row: AppRow): Boolean = withContext(Dispatchers.IO) {
        SystemAmoled.restartApp(app, row.pkg)
    }.also { appReads.trySend(Unit); summaryReads.trySend(Unit) }

    suspend fun safeToRestart(pkg: String): Boolean = withContext(Dispatchers.IO) {
        SystemAmoled.safeToRestart(app, pkg)
    }

    // The user's own rules (inspector)

    /** Keep a colour stock, pin it, or clear the rule (null [pin] and [keep] false), then read the app again. */
    fun setUserRule(row: AppRow, name: String, keep: Boolean, pin: Int?, onDone: (String) -> Unit) {
        withRoot {
            withContext(Dispatchers.IO) {
                AmoledUserRules.update(app) { rules ->
                    when {
                        keep -> rules.keep(row.pkg, name)
                        pin != null -> rules.pin(row.pkg, name, pin)
                        else -> rules.clear(row.pkg, name)
                    }
                }
            }
            queue(AmoledJobs.Kind.APPLY_ONE) { AmoledJobs.applyOne(app, row.pkg) }
            previewReads.trySend(Unit)
            onDone(string(when {
                keep -> R.string.reborn_studio_kept
                pin != null -> R.string.reborn_studio_pinned
                else -> R.string.reborn_studio_reset_done
            }, name))
        }
    }

    /** What the inspector shows of an app: its change records and the user's rules for it. */
    @Immutable
    class InspectorData(
        val record: AmoledChange.Record?,
        val kept: List<String>,
        val pinned: Map<String, Int>,
    )

    /** Blocking (files). */
    fun readInspector(context: Context, pkg: String): InspectorData {
        val record = AmoledChange.read(context, pkg)
        val kept = ArrayList<String>()
        val pinned = LinkedHashMap<String, Int>()
        try {
            val rules = JSONObject(AmoledUserRules.read(context).toJson())
            rules.optJSONObject("keep")?.optJSONArray(pkg)?.let { names ->
                for (i in 0 until names.length()) names.optString(i).takeIf { it.isNotEmpty() }?.let(kept::add)
            }
            rules.optJSONObject("pin")?.optJSONObject(pkg)?.let { names ->
                names.keys().forEach { name ->
                    try {
                        pinned[name] = AmoledPalette.parseColor(names.getString(name))
                    } catch (e: Exception) {
                        // not a colour
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "user rules unreadable", e)
        }
        return InspectorData(record, kept, pinned)
    }

    // Quick settings

    /**
     * Write the quick settings options and the active tile, then have System UI pick them up:
     * an apply of its colours and a restart while AMOLED is applied, else just a restart.
     */
    fun applyQuickSettings(aspect: Float, shade: Boolean, track: Boolean, tile: String) {
        val prefs = prefs()
        val previousTile = prefs.getString(AmoledRules.PREF_ACTIVE_TILE, AmoledRules.TILE_WHITE)
        prefs.edit().putString(AmoledRules.PREF_ACTIVE_TILE, tile)
            .putBoolean(AmoledRules.PREF_QS_CHOSEN, true).apply()
        if (tile != previousTile) {
            // The tile is part of a preset (Sprite): another tile makes the look custom
            val preset = AmoledPresets.preset(prefs.getString(AmoledPresets.PREF_PRESET, null))
            if (preset?.tile != null && preset.tile != tile) {
                prefs.edit().putString(AmoledPresets.PREF_PRESET, AmoledPresets.CUSTOM).apply()
            }
            _look.value = readLook()
            previewReads.trySend(Unit)
        }
        // System UI's colours are read again with the rules theme AMOLED was applied with (the
        // one chosen in the picker applies with the next Apply, to every app at once)
        val theme = _summary.value?.rulesTheme
        val enabled = _summary.value?.enabled == true
        // Shown as saved at once (the write may wait behind a running job); read again if it fails
        val wanted = QsSaved(aspect, shade, track)
        _qs.value = wanted
        withRoot {
            if (AmoledJobs.isBusy()) say(string(R.string.reborn_studio_qs_waiting))
            qsWrite = wanted
            val written = try {
                withContext(Dispatchers.IO) {
                    val chosen = QsOptions.read()
                    chosen.aspect = if (aspect >= QsOptions.STOCK_ASPECT - 0.005f) 0f else aspect
                    chosen.shade = shade
                    chosen.track = track
                    chosen.write(app)
                }
            } finally {
                if (qsWrite === wanted) qsWrite = null
            }
            if (!written) {
                say(string(R.string.system_amoled_failed))
                otherReads.trySend(Unit)
                return@withRoot
            }
            if (enabled) {
                queue(AmoledJobs.Kind.APPLY_SYSTEMUI) { AmoledJobs.applySystemUi(app, theme) }
            } else {
                withContext(Dispatchers.IO) { QsOptions.restartSystemUi() }
            }
            say(string(R.string.reborn_studio_qs_applied))
        }
    }
}
