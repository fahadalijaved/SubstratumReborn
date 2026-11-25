/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.amoled

import android.text.format.DateUtils
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import projekt.substratum.R
import projekt.substratum.reborn.RebornActions
import projekt.substratum.reborn.RebornCache
import projekt.substratum.reborn.components.ChipState
import projekt.substratum.reborn.components.HeroShape
import projekt.substratum.reborn.components.MorphingShape
import projekt.substratum.reborn.components.SectionCard
import projekt.substratum.reborn.components.StatusChip
import projekt.substratum.reborn.components.rememberResumeCount
import projekt.substratum.reborn.components.rememberRoot
import projekt.substratum.reborn.theme.LocalRebornColors

/**
 * AMOLED for every app, in summary, with the way into its settings. AMOLED Studio replaces this
 * tab later.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AmoledTab() {
    val context = LocalContext.current
    val resume = rememberResumeCount()
    val summary by rememberRoot(resume, initial = RebornCache.amoled) {
        readAmoledSummary(context).also { RebornCache.amoled = it }
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val background = MaterialTheme.colorScheme.background
    val colors = LocalRebornColors.current
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = background,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.reborn_tab_amoled), fontWeight = FontWeight.Bold) },
                subtitle = { Text(stringResource(R.string.reborn_amoled_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = background,
                    scrolledContainerColor = background,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val s = summary
            val on = s?.enabled == true
            SectionCard(container = colors.heroCard, contentPadding = PaddingValues(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                        MorphingShape(
                            shape = if (on) HeroShape.Calm else HeroShape.Idle,
                            color = if (on) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Icon(
                            painterResource(R.drawable.ic_reborn_amoled),
                            contentDescription = null,
                            tint = if (on) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                    Spacer(Modifier.width(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(if (on) R.string.reborn_on else R.string.reborn_off),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (on) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = when {
                                s == null -> stringResource(R.string.reborn_reading)
                                on -> stringResource(R.string.reborn_amoled_apps, s.themed, s.total)
                                else -> stringResource(R.string.reborn_amoled_off_summary)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (s != null) {
                    Spacer(Modifier.height(18.dp))
                    HorizontalDivider(color = if (colors.dark) colors.cardBorder else MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(6.dp))
                    DetailRow(stringResource(R.string.reborn_amoled_rules), s.themeName?.toString()
                        ?: stringResource(R.string.reborn_amoled_no_rules))
                    DetailRow(stringResource(R.string.reborn_amoled_applied), when (s.appliedAt) {
                        0L -> stringResource(R.string.reborn_never)
                        else -> DateUtils.getRelativeTimeSpanString(s.appliedAt, System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS).toString()
                    })
                    DetailRow(stringResource(R.string.reborn_amoled_restored), when (s.restoredAt) {
                        0L -> stringResource(R.string.reborn_never)
                        else -> DateUtils.getRelativeTimeSpanString(s.restoredAt, System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS).toString()
                    })
                    if (s.survivedReboot || s.restartNeeded > 0 || s.unsupported > 0) {
                        Spacer(Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (s.survivedReboot) StatusChip(stringResource(R.string.reborn_survived_reboot), ChipState.OK)
                            if (s.restartNeeded > 0) StatusChip(
                                stringResource(R.string.reborn_restart_needed, s.restartNeeded), ChipState.Warn)
                            if (s.unsupported > 0) StatusChip(
                                stringResource(R.string.reborn_amoled_unsupported, s.unsupported), ChipState.Off)
                        }
                    }
                }
            }
            Button(
                onClick = { RebornActions.openAmoledSettings(context, s?.theme, s?.themeName) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(stringResource(R.string.reborn_amoled_open), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
    }
}
