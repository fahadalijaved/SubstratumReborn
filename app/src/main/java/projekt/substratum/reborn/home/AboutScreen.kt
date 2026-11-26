/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.LibsBuilder
import projekt.substratum.BuildConfig
import projekt.substratum.R
import projekt.substratum.common.platform.RebornStatus
import projekt.substratum.reborn.RebornCache
import projekt.substratum.reborn.components.AppIcon
import projekt.substratum.reborn.components.SectionCard
import projekt.substratum.reborn.components.SectionHeader
import projekt.substratum.reborn.components.ShapedIcon
import projekt.substratum.reborn.components.StatusChip
import projekt.substratum.reborn.components.design.BackIcon
import projekt.substratum.reborn.components.design.HeroCard
import projekt.substratum.reborn.components.design.RebornFlexibleScaffold
import projekt.substratum.reborn.components.design.SectionLabel
import projekt.substratum.reborn.components.design.SegmentedGroup
import projekt.substratum.reborn.components.design.rebornSegmentColors
import projekt.substratum.reborn.components.rememberRoot
import projekt.substratum.reborn.theme.LocalRebornColors

/** This fork's repository (the git remote it is published from; public) */
private const val SOURCE_URL = "https://github.com/fahadalijaved/SubstratumReborn"
/** The theme format and the engine, for themers */
private const val THEMERS_URL = "$SOURCE_URL/blob/oneui9-root/docs/oneui9/README.md"
/** The project it is based on */
private const val UPSTREAM_URL = "https://github.com/substratum/substratum"
private const val TAG = "RebornAbout"

/**
 * About Substratum Reborn: the version and build, what the engine and the module report right
 * now, the source, the open-source licences, What's new, and where it comes from.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AboutScreen(onBack: () -> Unit, onWhatsNew: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val status by rememberRoot(Unit, initial = RebornCache.status) {
        RebornStatus.read(context).also { RebornCache.status = it }
    }
    val model = remember(status) { status?.let { describe(context, it) } }
    val colors = LocalRebornColors.current
    val scheme = MaterialTheme.colorScheme
    RebornFlexibleScaffold(
        title = stringResource(R.string.reborn_about_title),
        subtitle = stringResource(R.string.reborn_about_subtitle),
        navigationIcon = { BackIcon(onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "identity") {
                HeroCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(context.packageName, size = 64.dp, shape = MaterialTheme.shapes.largeIncreased)
                        Spacer(Modifier.width(18.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.reborn_about_name),
                                style = MaterialTheme.typography.headlineSmallEmphasized,
                            )
                            Text(
                                BuildConfig.VERSION_NAME,
                                style = MaterialTheme.typography.titleMediumEmphasized,
                                color = scheme.primary,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.reborn_about_tagline),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.reborn_about_tested),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(18.dp))
                    HorizontalDivider(color = if (colors.dark) colors.cardBorder else scheme.outlineVariant)
                    Spacer(Modifier.height(6.dp))
                    InfoLine(
                        stringResource(R.string.reborn_about_version),
                        stringResource(R.string.reborn_about_version_value, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                    )
                    InfoLine(
                        stringResource(R.string.reborn_about_build),
                        listOf(
                            BuildConfig.GIT_HASH.takeIf { it.isNotBlank() },
                            DateUtils.formatDateTime(context, BuildConfig.buildTime.time,
                                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR),
                        ).filterNotNull().joinToString(" · "),
                    )
                    InfoLine(
                        stringResource(R.string.reborn_about_engine),
                        stringResource(R.string.reborn_about_engine_value),
                    )
                }
            }
            item(key = "status_header") { SectionHeader(stringResource(R.string.reborn_about_status)) }
            item(key = "status") {
                SectionCard {
                    if (model == null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LoadingIndicator(Modifier.size(40.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                stringResource(R.string.reborn_checking),
                                style = MaterialTheme.typography.bodyLarge,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Text(
                            stringResource(model.headline),
                            style = MaterialTheme.typography.titleMediumEmphasized,
                        )
                        Text(
                            model.supporting,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        model.rows.forEach { row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    painterResource(row.icon),
                                    contentDescription = null,
                                    tint = scheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    stringResource(row.label),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(8.dp))
                                StatusChip(row.value, row.state, Modifier.widthIn(max = 200.dp))
                            }
                        }
                    }
                }
            }
            item(key = "links") {
                // The Pixel-style segmented list: rows 2 dp apart under a section label
                val links = listOf(
                    Link(
                        R.drawable.ic_reborn_amoled,
                        stringResource(R.string.reborn_about_whats_new),
                        stringResource(R.string.reborn_about_whats_new_text),
                        scheme.primaryContainer, scheme.onPrimaryContainer, MaterialShapes.Clover4Leaf.toShape(),
                        external = false, onClick = onWhatsNew,
                    ),
                    Link(
                        R.drawable.ic_reborn_themes,
                        stringResource(R.string.reborn_about_themers),
                        stringResource(R.string.reborn_about_themers_text),
                        scheme.tertiaryContainer, scheme.onTertiaryContainer, MaterialShapes.Sunny.toShape(),
                        onClick = { openUrl(context, THEMERS_URL) },
                    ),
                    Link(
                        R.drawable.ic_reborn_open,
                        stringResource(R.string.reborn_about_source),
                        SOURCE_URL.removePrefix("https://"),
                        scheme.secondaryContainer, scheme.onSecondaryContainer, MaterialShapes.Cookie6Sided.toShape(),
                        onClick = { openUrl(context, SOURCE_URL) },
                    ),
                    Link(
                        R.drawable.ic_reborn_file,
                        stringResource(R.string.reborn_about_licences),
                        stringResource(R.string.reborn_about_licences_text),
                        scheme.tertiaryContainer, scheme.onTertiaryContainer, MaterialShapes.Pentagon.toShape(),
                        // An in-app screen, not a browser
                        external = false,
                        onClick = { openLicences(context, colors.dark) },
                    ),
                    Link(
                        R.drawable.ic_reborn_layers,
                        stringResource(R.string.reborn_about_upstream),
                        UPSTREAM_URL.removePrefix("https://"),
                        scheme.surfaceContainerHighest, scheme.onSurfaceVariant, MaterialShapes.Cookie4Sided.toShape(),
                        onClick = { openUrl(context, UPSTREAM_URL) },
                    ),
                )
                Column {
                    SectionLabel(stringResource(R.string.reborn_about_more))
                    SegmentedGroup(links.size) { i, shapes -> LinkRow(links[i], shapes) }
                }
            }
            item(key = "credits") {
                SectionCard {
                    Text(
                        stringResource(R.string.reborn_about_based_on),
                        style = MaterialTheme.typography.titleSmallEmphasized,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.reborn_about_licence_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.reborn_about_themes_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyLargeEmphasized,
        )
    }
}

/** One row of About's links: [external] ones leave the app (a browser) and show it. */
private class Link(
    @DrawableRes val icon: Int,
    val title: String,
    val subtitle: String,
    val container: Color,
    val content: Color,
    val shape: Shape,
    val external: Boolean = true,
    val onClick: () -> Unit,
)

@Composable
private fun LinkRow(link: Link, shapes: ListItemShapes) {
    SegmentedListItem(
        onClick = link.onClick,
        shapes = shapes,
        colors = rebornSegmentColors(),
        leadingContent = { ShapedIcon(link.icon, link.container, link.content, size = 44.dp, shape = link.shape) },
        supportingContent = { Text(link.subtitle, maxLines = 2) },
        trailingContent = {
            Icon(
                painterResource(if (link.external) R.drawable.ic_reborn_open else R.drawable.ic_reborn_chevron),
                contentDescription = null,
            )
        },
    ) {
        Text(link.title, style = MaterialTheme.typography.bodyLargeEmphasized)
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        Log.w(TAG, "No browser for $url", e)
    }
}

/** The open-source licences of every library in the app (AboutLibraries' own screen). */
private fun openLicences(context: Context, dark: Boolean) {
    try {
        LibsBuilder()
            .withFields(R.string::class.java.fields)
            .withActivityTitle(context.getString(R.string.reborn_about_licences))
            .withAboutIconShown(false)
            .withAboutVersionShown(false)
            .withLicenseShown(true)
            .withActivityStyle(if (dark) Libs.ActivityStyle.DARK else Libs.ActivityStyle.LIGHT_DARK_TOOLBAR)
            .start(context)
    } catch (e: Exception) {
        Log.e(TAG, "Licences did not open", e)
    }
}
