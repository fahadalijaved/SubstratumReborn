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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import projekt.substratum.reborn.components.rememberRoot
import projekt.substratum.reborn.theme.LocalRebornColors

/** This fork's repository (the git remote it is published from) */
private const val SOURCE_URL = "https://github.com/fahadalijaved/SubstratumReborn"
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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val background = scheme.background
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = background,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.reborn_about_title), fontWeight = FontWeight.Bold) },
                subtitle = { Text(stringResource(R.string.reborn_about_subtitle)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_reborn_back), stringResource(R.string.reborn_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = background,
                    scrolledContainerColor = background,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
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
                SectionCard(container = colors.heroCard, contentPadding = PaddingValues(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(context.packageName, size = 64.dp, shape = RoundedCornerShape(20.dp))
                        Spacer(Modifier.width(18.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.reborn_about_name),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                BuildConfig.VERSION_NAME,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = scheme.primary,
                            )
                        }
                    }
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
                        Text(
                            stringResource(R.string.reborn_checking),
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            stringResource(model.headline),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
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
            item(key = "more_header") { SectionHeader(stringResource(R.string.reborn_about_more)) }
            item(key = "links") {
                SectionCard(contentPadding = PaddingValues(vertical = 8.dp)) {
                    LinkRow(
                        icon = R.drawable.ic_reborn_amoled,
                        title = stringResource(R.string.reborn_about_whats_new),
                        subtitle = stringResource(R.string.reborn_about_whats_new_text),
                        container = scheme.primaryContainer,
                        content = scheme.onPrimaryContainer,
                        shape = MaterialShapes.Clover4Leaf.toShape(),
                        onClick = onWhatsNew,
                    )
                    LinkRow(
                        icon = R.drawable.ic_reborn_open,
                        title = stringResource(R.string.reborn_about_source),
                        subtitle = SOURCE_URL.removePrefix("https://"),
                        container = scheme.secondaryContainer,
                        content = scheme.onSecondaryContainer,
                        shape = MaterialShapes.Cookie6Sided.toShape(),
                        onClick = { openUrl(context, SOURCE_URL) },
                    )
                    LinkRow(
                        icon = R.drawable.ic_reborn_file,
                        title = stringResource(R.string.reborn_about_licences),
                        subtitle = stringResource(R.string.reborn_about_licences_text),
                        container = scheme.tertiaryContainer,
                        content = scheme.onTertiaryContainer,
                        shape = MaterialShapes.Pentagon.toShape(),
                        onClick = { openLicences(context, colors.dark) },
                    )
                    LinkRow(
                        icon = R.drawable.ic_reborn_layers,
                        title = stringResource(R.string.reborn_about_upstream),
                        subtitle = UPSTREAM_URL.removePrefix("https://"),
                        container = scheme.surfaceContainerHighest,
                        content = scheme.onSurfaceVariant,
                        shape = MaterialShapes.Cookie4Sided.toShape(),
                        onClick = { openUrl(context, UPSTREAM_URL) },
                    )
                }
            }
            item(key = "credits") {
                SectionCard {
                    Text(
                        stringResource(R.string.reborn_about_based_on),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
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
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun LinkRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    container: Color,
    content: Color,
    shape: Shape,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShapedIcon(icon, container, content, size = 44.dp, shape = shape)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
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
