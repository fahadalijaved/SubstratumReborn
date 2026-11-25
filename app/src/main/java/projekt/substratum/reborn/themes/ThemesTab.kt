/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.themes

import android.content.Context
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import projekt.substratum.R
import projekt.substratum.common.Packages
import projekt.substratum.common.Theming
import projekt.substratum.reborn.RebornActions
import projekt.substratum.reborn.RebornCache
import projekt.substratum.reborn.components.AppIcon
import projekt.substratum.reborn.components.SectionCard
import projekt.substratum.reborn.components.ShapedIcon
import projekt.substratum.reborn.components.rememberResumeCount
import projekt.substratum.reborn.components.rememberRoot
import projekt.substratum.reborn.components.rememberSnackbar

data class ThemeItem(val name: String, val author: String, val pkg: String)

/** Blocking: PackageManager. */
fun loadThemes(context: Context): List<ThemeItem> =
    Packages.getSubstratumPackages(context, null).orEmpty()
        .mapNotNull { (name, data) ->
            val pkg = data.getOrNull(1) ?: return@mapNotNull null
            ThemeItem(name ?: pkg, data.getOrNull(0).orEmpty(), pkg)
        }
        .sortedBy { it.name.lowercase() }

/**
 * Installed themes as full-width cards. A tap opens the theme the way Classic does: overlay
 * themes in InformationActivity, AMOLED rules themes in the AMOLED screen.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ThemesTab() {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val resume = rememberResumeCount()
    val snackbar = rememberSnackbar()
    val cannotOpen = stringResource(R.string.reborn_theme_failed)
    val themes by rememberRoot(resume, initial = RebornCache.themes) {
        loadThemes(context).also { RebornCache.themes = it }
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val background = MaterialTheme.colorScheme.background
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = background,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.reborn_tab_themes), fontWeight = FontWeight.Bold) },
                subtitle = {
                    themes?.let { Text(pluralThemes(it.size)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = background,
                    scrolledContainerColor = background,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val list = themes
        when {
            list == null -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { LoadingIndicator(Modifier.size(64.dp)) }
            list.isEmpty() -> Column(
                Modifier
                    .padding(padding)
                    .padding(16.dp),
            ) {
                SectionCard {
                    ShapedIcon(
                        icon = R.drawable.ic_reborn_themes,
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer,
                        size = 56.dp,
                        shape = MaterialShapes.Flower.toShape(),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.reborn_themes_empty), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.reborn_themes_empty_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    FilledTonalButton(onClick = { RebornActions.openClassic(context) }) {
                        Text(stringResource(R.string.reborn_classic))
                    }
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 4.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(list, key = { it.pkg }) { theme ->
                    SectionCard(
                        onClick = {
                            try {
                                Theming.launchTheme(activity ?: context, theme.pkg)
                            } catch (e: RuntimeException) {
                                snackbar(cannotOpen)
                            }
                        },
                        contentPadding = PaddingValues(16.dp),
                        modifier = Modifier.animateItem(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(theme.pkg, size = 56.dp, shape = RoundedCornerShape(16.dp))
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    theme.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    theme.author,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(
                                painterResource(R.drawable.ic_reborn_chevron),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun pluralThemes(count: Int): String {
    val context = LocalContext.current
    return context.resources.getQuantityString(R.plurals.reborn_themes_count, count, count)
}
