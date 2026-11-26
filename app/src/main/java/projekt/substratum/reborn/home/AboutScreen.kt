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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.LibsBuilder
import projekt.substratum.BuildConfig
import projekt.substratum.R
import projekt.substratum.common.platform.RebornStatus
import projekt.substratum.reborn.RebornCache
import projekt.substratum.reborn.components.BrandMark
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
import projekt.substratum.reborn.components.design.softTitle
import projekt.substratum.reborn.components.rememberRoot
import projekt.substratum.reborn.theme.LocalRebornColors

/** This fork's repository (the git remote it is published from; public) */
private const val SOURCE_URL = "https://github.com/fahadalijaved/SubstratumReborn"
/** The branch the app is built from: the docs links point at it */
private const val BRANCH_URL = "$SOURCE_URL/blob/oneui9-root"
/** The theme format and the engine, for themers */
internal const val THEMERS_URL = "$BRANCH_URL/docs/oneui9/README.md"
/** What changed in each version */
private const val CHANGELOG_URL = "$BRANCH_URL/CHANGELOG.md"
/** The project it is based on */
private const val UPSTREAM_URL = "https://github.com/substratum/substratum"
private const val TAG = "RebornAbout"

/**
 * About Reborn: the swatch-deck mark over "Reborn" and a quiet "Substratum", the tagline, the
 * version, build and engine, what the engine and the module report right now, the links (What's
 * new, the changelog, the source, the themers' guide, the open-source licences, the original
 * Substratum) as one segmented list, and the credits.
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
                        // The Substratum swatch deck, in the app's colours; it fans out on a press
                        BrandMark(size = 64.dp)
                        Spacer(Modifier.width(18.dp))
                        // "Reborn" over a quiet "Substratum", as on Home's brand bar, larger
                        Column(
                            Modifier
                                .weight(1f)
                                .semantics(mergeDescendants = true) { heading() },
                        ) {
                            Text(
                                stringResource(R.string.reborn_about_name),
                                style = MaterialTheme.typography.softTitle.copy(
                                    fontSize = 32.sp,
                                    lineHeight = 38.sp,
                                    letterSpacing = (-0.4).sp,
                                ),
                                maxLines = 1,
                            )
                            Text(
                                stringResource(R.string.reborn_home_brand_sub),
                                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.3.sp),
                                color = scheme.onSurfaceVariant.copy(alpha = 0.75f),
                                maxLines = 1,
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        stringResource(R.string.reborn_about_tagline),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        color = scheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.reborn_about_lead),
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
                        R.drawable.ic_reborn_setup,
                        stringResource(R.string.reborn_about_changelog),
                        stringResource(R.string.reborn_about_changelog_text),
                        scheme.secondaryContainer, scheme.onSecondaryContainer, MaterialShapes.Cookie9Sided.toShape(),
                        onClick = { openUrl(context, CHANGELOG_URL) },
                    ),
                    Link(
                        R.drawable.ic_reborn_open,
                        stringResource(R.string.reborn_about_source),
                        SOURCE_URL.removePrefix("https://"),
                        scheme.secondaryContainer, scheme.onSecondaryContainer, MaterialShapes.Cookie6Sided.toShape(),
                        onClick = { openUrl(context, SOURCE_URL) },
                    ),
                    Link(
                        R.drawable.ic_reborn_themes,
                        stringResource(R.string.reborn_about_themers),
                        stringResource(R.string.reborn_about_themers_text),
                        scheme.tertiaryContainer, scheme.onTertiaryContainer, MaterialShapes.Sunny.toShape(),
                        onClick = { openUrl(context, THEMERS_URL) },
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
                val credits = listOf(
                    Credit(
                        stringResource(R.string.reborn_about_credit_substratum),
                        stringResource(R.string.reborn_about_credit_substratum_text),
                        scheme.surfaceContainerHighest, scheme.onSurfaceVariant, MaterialShapes.Cookie4Sided.toShape(),
                        icon = R.drawable.ic_reborn_layers,
                    ),
                    Credit(
                        stringResource(R.string.reborn_about_credit_font),
                        stringResource(R.string.reborn_about_credit_font_text),
                        scheme.primaryContainer, scheme.onPrimaryContainer, MaterialShapes.Cookie7Sided.toShape(),
                        // The face itself stands for it
                        glyph = stringResource(R.string.reborn_about_credit_font_glyph),
                    ),
                    Credit(
                        stringResource(R.string.reborn_about_credit_symbols),
                        stringResource(R.string.reborn_about_credit_symbols_text),
                        scheme.tertiaryContainer, scheme.onTertiaryContainer, MaterialShapes.Clover4Leaf.toShape(),
                        icon = R.drawable.ic_reborn_apps,
                    ),
                )
                Column {
                    SectionLabel(stringResource(R.string.reborn_about_credits))
                    SegmentedGroup(credits.size) { i, shapes -> CreditRow(credits[i], shapes) }
                    Text(
                        stringResource(R.string.reborn_about_licence_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 14.dp),
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

/** One credit: what it is, who made it and its licence; an [icon], or a [glyph] in the app's type. */
private class Credit(
    val title: String,
    val subtitle: String,
    val container: Color,
    val content: Color,
    val shape: Shape,
    @DrawableRes val icon: Int? = null,
    val glyph: String? = null,
)

@Composable
private fun CreditRow(credit: Credit, shapes: ListItemShapes) {
    SegmentedListItem(
        shapes = shapes,
        colors = rebornSegmentColors(),
        leadingContent = {
            if (credit.icon != null) {
                ShapedIcon(credit.icon, credit.container, credit.content, size = 44.dp, shape = credit.shape)
            } else {
                // A sample of the face, not text to read out: the row's title names it
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(credit.shape)
                        .background(credit.container)
                        .clearAndSetSemantics { },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        credit.glyph.orEmpty(),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = credit.content,
                    )
                }
            }
        },
        supportingContent = { Text(credit.subtitle) },
    ) {
        Text(credit.title, style = MaterialTheme.typography.bodyLargeEmphasized)
    }
}

internal fun openUrl(context: Context, url: String) {
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
