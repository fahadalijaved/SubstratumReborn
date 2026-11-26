/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import projekt.substratum.R
import projekt.substratum.common.RebornInit
import projekt.substratum.reborn.amoled.AmoledTab
import projekt.substratum.reborn.amoled.StudioState
import projekt.substratum.reborn.applied.AppliedTab
import projekt.substratum.reborn.components.LocalSnackbar
import projekt.substratum.reborn.components.RebornSnackbarHost
import projekt.substratum.reborn.home.HomeScreen
import projekt.substratum.reborn.setup.SetupScreen
import projekt.substratum.reborn.theme.RebornTheme
import projekt.substratum.reborn.theme.isDarkPreferred
import projekt.substratum.reborn.themes.ThemesTab
import projekt.substratum.util.helpers.LocaleHelper

enum class RebornTab(val key: String, @StringRes val label: Int, @DrawableRes val icon: Int) {
    Home("home", R.string.reborn_tab_home, R.drawable.ic_reborn_home),
    Amoled("amoled", R.string.reborn_tab_amoled, R.drawable.ic_reborn_amoled),
    Themes("themes", R.string.reborn_tab_themes, R.drawable.ic_reborn_themes),
    Applied("applied", R.string.reborn_tab_applied, R.drawable.ic_reborn_applied);

    companion object {
        fun of(key: String?): RebornTab? = entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
    }
}

/**
 * The Reborn shell: Home, AMOLED, Themes and Applied, and the setup checklist on the first
 * launch. Started by [projekt.substratum.LauncherActivity]; extra [EXTRA_TAB] picks a tab
 * (home, amoled, themes or applied) and [EXTRA_THEME] the rules theme AMOLED Studio applies with.
 */
class RebornActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TAB = "tab"
        const val EXTRA_THEME = "theme"
    }

    private val requestedTab = mutableStateOf<String?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val dark = isDarkPreferred(this, systemDark)
        // Bars drawn over the content, their icons matched to the theme the app uses (which can
        // differ from the system's when chosen in Settings)
        val bars = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
        else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        super.onCreate(savedInstanceState)
        RebornInit.start(this)
        if (savedInstanceState == null) {
            requestedTab.value = intent?.getStringExtra(EXTRA_TAB)
            StudioState.requestTheme(intent?.getStringExtra(EXTRA_THEME))
        }
        setContent {
            RebornTheme {
                RebornApp(requestedTab)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_TAB)?.let { requestedTab.value = it }
        StudioState.requestTheme(intent.getStringExtra(EXTRA_THEME))
    }
}

@Composable
fun RebornApp(requestedTab: MutableState<String?>) {
    var tab by rememberSaveable { mutableStateOf(RebornTab.Home) }
    var showSetup by rememberSaveable { mutableStateOf(!RebornInit.isSetupDone()) }
    val snackbar = remember { SnackbarHostState() }
    val tabs = rememberSaveableStateHolder()
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(requestedTab.value) {
        RebornTab.of(requestedTab.value)?.let { tab = it }
        requestedTab.value = null
    }

    BackHandler(enabled = !showSetup && tab != RebornTab.Home) { tab = RebornTab.Home }

    AnimatedContent(
        targetState = showSetup,
        transitionSpec = {
            (fadeIn(tween(320)) + scaleIn(tween(320), initialScale = 0.97f)) togetherWith fadeOut(tween(160))
        },
        label = "setup",
    ) { setup ->
        if (setup) {
            SetupScreen(onDone = {
                RebornInit.setSetupDone()
                showSetup = false
            })
        } else {
            CompositionLocalProvider(LocalSnackbar provides snackbar) {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets(0),
                    snackbarHost = { RebornSnackbarHost(snackbar) },
                    bottomBar = {
                        NavigationBar {
                            RebornTab.entries.forEach { item ->
                                NavigationBarItem(
                                    selected = tab == item,
                                    onClick = {
                                        if (tab != item) {
                                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                            tab = item
                                        }
                                    },
                                    icon = { Icon(painterResource(item.icon), contentDescription = null) },
                                    label = { Text(stringResource(item.label)) },
                                )
                            }
                        }
                    },
                ) { padding ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .consumeWindowInsets(padding),
                    ) {
                        AnimatedContent(
                            targetState = tab,
                            transitionSpec = { fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(90)) },
                            label = "tab",
                        ) { current ->
                            tabs.SaveableStateProvider(current.key) {
                                when (current) {
                                    RebornTab.Home -> HomeScreen(
                                        onOpenSetup = { showSetup = true },
                                        onOpenAmoled = { tab = RebornTab.Amoled },
                                    )
                                    RebornTab.Amoled -> AmoledTab()
                                    RebornTab.Themes -> ThemesTab()
                                    RebornTab.Applied -> AppliedTab()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
