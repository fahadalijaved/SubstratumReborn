/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.home

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import projekt.substratum.BuildConfig
import projekt.substratum.R
import projekt.substratum.Substratum
import projekt.substratum.reborn.components.HeroShape
import projekt.substratum.reborn.components.MorphingShape
import projekt.substratum.reborn.components.ShapedIcon
import androidx.graphics.shapes.RoundedPolygon
import java.util.Locale

/** The "What's new" sheet: shown once per version, after the setup checklist. */
object WhatsNew {

    /** The version code the sheet was last shown for */
    const val PREF_SEEN = "reborn_whats_new_seen"

    /** This version's highlights were not shown yet. */
    fun isNew(): Boolean {
        val prefs = Substratum.getPreferences() ?: return false
        return prefs.getInt(PREF_SEEN, 0) < BuildConfig.VERSION_CODE
    }

    /** Shown: not again until the next version. */
    fun markSeen() {
        Substratum.getPreferences()?.edit()?.putInt(PREF_SEEN, BuildConfig.VERSION_CODE)?.apply()
    }
}

private class Highlight(
    @DrawableRes val icon: Int,
    @StringRes val title: Int,
    @StringRes val text: Int,
    val shape: RoundedPolygon,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val highlights: List<Highlight> by lazy {
    listOf(
        Highlight(R.drawable.ic_reborn_amoled, R.string.reborn_new_studio, R.string.reborn_new_studio_text,
            MaterialShapes.Clover4Leaf),
        Highlight(R.drawable.ic_reborn_palette, R.string.reborn_new_look, R.string.reborn_new_look_text,
            MaterialShapes.Cookie6Sided),
        Highlight(R.drawable.ic_reborn_search, R.string.reborn_new_inspector, R.string.reborn_new_inspector_text,
            MaterialShapes.Pentagon),
        Highlight(R.drawable.ic_reborn_undo, R.string.reborn_new_applied, R.string.reborn_new_applied_text,
            MaterialShapes.Cookie4Sided),
        Highlight(R.drawable.ic_reborn_check, R.string.reborn_new_safety, R.string.reborn_new_safety_text,
            MaterialShapes.Sunny),
        Highlight(R.drawable.ic_reborn_engine, R.string.reborn_new_engine, R.string.reborn_new_engine_text,
            MaterialShapes.Burst),
        Highlight(R.drawable.ic_reborn_refresh, R.string.reborn_new_retheme, R.string.reborn_new_retheme_text,
            MaterialShapes.Cookie9Sided),
    )
}

/**
 * Reborn 2.0's highlights in a bottom sheet, with a way into AMOLED Studio.
 *
 * @param onDismiss The sheet is gone (closed by a button, a swipe or back)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewSheet(onDismiss: () -> Unit, onOpenStudio: () -> Unit) {
    // Opens straight to full height: no half-open stop
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    fun close(then: () -> Unit = {}) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            then()
        }
    }
    val scheme = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
                    MorphingShape(HeroShape.Calm, scheme.primary, Modifier.fillMaxSize())
                    Icon(
                        painterResource(R.drawable.ic_reborn_amoled),
                        contentDescription = null,
                        tint = scheme.onPrimary,
                        modifier = Modifier.size(32.dp),
                    )
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.reborn_new_overline).uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp,
                        color = scheme.primary,
                    )
                    Text(
                        BuildConfig.VERSION_NAME,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.reborn_new_lead),
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(22.dp))
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                highlights.forEachIndexed { index, item ->
                    val (container, content) = when (index % 3) {
                        0 -> scheme.primaryContainer to scheme.onPrimaryContainer
                        1 -> scheme.secondaryContainer to scheme.onSecondaryContainer
                        else -> scheme.tertiaryContainer to scheme.onTertiaryContainer
                    }
                    HighlightRow(item, container, content)
                }
            }
            Spacer(Modifier.height(28.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { close() }) {
                    Text(stringResource(R.string.reborn_new_done))
                }
                Button(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    close(onOpenStudio)
                }) {
                    Text(stringResource(R.string.reborn_new_open_studio))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HighlightRow(item: Highlight, container: Color, content: Color) {
    Row(verticalAlignment = Alignment.Top) {
        ShapedIcon(item.icon, container, content, size = 44.dp, shape = item.shape.toShape())
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(item.title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(item.text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
