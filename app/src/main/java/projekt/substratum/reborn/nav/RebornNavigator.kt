/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList

/** How the top route last changed, for the direction of its transition. */
enum class NavMove { Push, Pop, Tab }

/**
 * One back stack per tab and the current tab. Only the top route of a stack is composed. Saved as
 * lists of route keys ([Saver]), so process death restores every stack.
 */
@Stable
class RebornNavigator internal constructor(initialTab: RebornTab, saved: Map<RebornTab, List<Route>>) {

    private val stacks: Map<RebornTab, SnapshotStateList<Route>> = RebornTab.entries.associateWith { tab ->
        val routes = saved[tab].orEmpty().filter { it != tab.root }
        (listOf(tab.root) + routes).toMutableStateList()
    }
    private val reselects = RebornTab.entries.associateWith { mutableIntStateOf(0) }

    var tab: RebornTab by mutableStateOf(initialTab)
        private set

    /** How the top route last changed (read by the transitions; not observed) */
    var lastMove: NavMove = NavMove.Tab
        private set

    /** Called with the keys of routes that left a stack, so their saved state can go too */
    var onRemoved: (tab: RebornTab, keys: List<String>) -> Unit = { _, _ -> }

    fun stack(tab: RebornTab): List<Route> = stacks.getValue(tab)

    fun top(tab: RebornTab = this.tab): Route = stacks.getValue(tab).last()

    /** The current tab shows more than its root. */
    val canPop: Boolean get() = stacks.getValue(tab).size > 1

    /** Goes up each time the user reselects [tab] (scroll its root to the top, expand its bar). */
    fun reselectSignal(tab: RebornTab): Int = reselects.getValue(tab).intValue

    /** Opens [route] on the current tab (nothing when it is on top already). */
    fun push(route: Route) {
        val stack = stacks.getValue(tab)
        if (stack.last() == route) return
        lastMove = NavMove.Push
        // The same route lower in the stack moves up, so one key is never shown twice
        val removed = stack.indexOf(route)
        if (removed >= 0) stack.removeAt(removed)
        stack.add(route)
    }

    /** Replaces the top route (a picked app's colours over the list it was picked from). */
    fun replaceTop(route: Route) {
        val stack = stacks.getValue(tab)
        if (stack.size <= 1) return push(route)
        lastMove = NavMove.Push
        val old = stack.removeAt(stack.lastIndex)
        stack.add(route)
        if (old.key != route.key) onRemoved(tab, listOf(old.key))
    }

    /** @return Whether a route was popped (false at the tab's root) */
    fun pop(): Boolean {
        val stack = stacks.getValue(tab)
        if (stack.size <= 1) return false
        lastMove = NavMove.Pop
        val old = stack.removeAt(stack.lastIndex)
        onRemoved(tab, listOf(old.key))
        return true
    }

    /** Back to [tab]'s root. */
    fun popToRoot(tab: RebornTab = this.tab) {
        val stack = stacks.getValue(tab)
        if (stack.size <= 1) return
        if (tab == this.tab) lastMove = NavMove.Pop
        val removed = stack.subList(1, stack.size).map { it.key }
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
        onRemoved(tab, removed)
    }

    /** A tab from the bar: reselecting the current one pops it to its root and signals a scroll to the top. */
    fun select(tab: RebornTab) {
        if (tab == this.tab) {
            popToRoot(tab)
            reselects.getValue(tab).intValue++
            return
        }
        lastMove = NavMove.Tab
        this.tab = tab
    }

    /** Opens [tab] showing [routes] over its root (a link). */
    fun open(tab: RebornTab, routes: List<Route>) {
        val stack = stacks.getValue(tab)
        val wanted = listOf(tab.root) + routes.filter { it != tab.root }.distinctBy { it.key }
        lastMove = when {
            tab != this.tab -> NavMove.Tab
            wanted.size < stack.size -> NavMove.Pop
            else -> NavMove.Push
        }
        if (stack.toList() != wanted) {
            val gone = stack.filter { r -> wanted.none { it.key == r.key } }.map { it.key }
            stack.clear()
            stack.addAll(wanted)
            if (gone.isNotEmpty()) onRemoved(tab, gone)
        }
        this.tab = tab
    }

    companion object {
        private const val SEPARATOR = "\n"

        /** [tab, then one line-separated list of route keys per tab] */
        val Saver: Saver<RebornNavigator, ArrayList<String>> = Saver(
            save = { nav ->
                arrayListOf(nav.tab.name).apply {
                    RebornTab.entries.forEach { t -> add(nav.stack(t).joinToString(SEPARATOR) { it.key }) }
                }
            },
            restore = { saved ->
                val tab = RebornTab.entries.firstOrNull { it.name == saved.firstOrNull() } ?: RebornTab.Home
                val stacks = RebornTab.entries.mapIndexed { i, t ->
                    t to saved.getOrNull(i + 1).orEmpty().split(SEPARATOR).mapNotNull { Route.parse(it) }
                }.toMap()
                RebornNavigator(tab, stacks)
            },
        )
    }
}

/** The shell's navigator, saved across configuration changes and process death. */
@Composable
fun rememberRebornNavigator(): RebornNavigator =
    rememberSaveable(saver = RebornNavigator.Saver) { RebornNavigator(RebornTab.Home, emptyMap()) }

/**
 * Runs [action] when [signal] (a tab's reselect count) goes up while this screen is shown, not
 * when the screen comes back with a count it has already seen.
 */
@Composable
fun OnReselect(signal: Int, action: suspend () -> Unit) {
    var handled by rememberSaveable { mutableIntStateOf(signal) }
    LaunchedEffect(signal) {
        if (signal != handled) {
            handled = signal
            action()
        }
    }
}
