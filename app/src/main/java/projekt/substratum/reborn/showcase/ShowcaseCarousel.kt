/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.showcase

import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import projekt.substratum.R
import projekt.substratum.Substratum
import projekt.substratum.common.platform.AmoledPresets
import projekt.substratum.common.platform.AmoledRules
import projekt.substratum.reborn.RebornCache
import projekt.substratum.reborn.amoled.StudioState
import projekt.substratum.reborn.components.SectionCard
import projekt.substratum.reborn.components.mock.BeforeAfter
import projekt.substratum.reborn.components.mock.ListMock
import projekt.substratum.reborn.components.mock.MockRoles
import projekt.substratum.reborn.components.mock.PopupMock
import projekt.substratum.reborn.components.mock.QsMock
import projekt.substratum.reborn.components.mock.SettingsMock
import projekt.substratum.reborn.components.rememberResumeCount
import projekt.substratum.reborn.components.rememberRoot

/** What the carousel shows, one page each. */
private enum class ShowcasePage(@StringRes val title: Int, @StringRes val detail: Int) {
    QuickSettings(R.string.reborn_showcase_qs, R.string.reborn_showcase_qs_text),
    Settings(R.string.reborn_showcase_settings, R.string.reborn_showcase_settings_text),
    Apps(R.string.reborn_showcase_apps, R.string.reborn_showcase_apps_text),
    Popups(R.string.reborn_showcase_popups, R.string.reborn_showcase_popups_text),
}

/** The previews and which preset the saved look is (or [AmoledPresets.CUSTOM]). */
class ShowcaseLooks(val previews: StudioState.Previews, val savedPreset: String)

/** The looks "Try a look" offers: the built-in presets, never one named after a third-party theme */
private val SHOWN_PRESETS = AmoledPresets.PRESETS.filter { it.id != AmoledPresets.SPRITE }

/** Every page is this tall, so paging never moves Home below it; taller mocks fade out. */
private val PageHeight = 344.dp
private const val ADVANCE_MILLIS = 5600L

/** Blocking (files): the saved look and the presets, as the Studio computes them. */
private fun loadLooks(context: Context, theme: String?): ShowcaseLooks {
    val previews = StudioState.showcasePreviews(context, theme)
    val prefs = Substratum.getPreferences()
    val tile = prefs.getString(AmoledRules.PREF_ACTIVE_TILE, AmoledRules.TILE_WHITE) ?: AmoledRules.TILE_WHITE
    val saved = AmoledPresets.look(prefs).preset?.takeIf { it == AmoledPresets.CUSTOM || AmoledPresets.preset(it) != null }
        ?: AmoledPresets.matching(previews.level, previews.accent, tile)
    return ShowcaseLooks(previews, saved)
}

/**
 * Home's "See the difference": a before/after carousel of Quick Settings, Settings, an app and
 * notifications with a popup, drawn with the engine's own colours for the saved look. It pages on
 * its own (paused while touched, while the app is in the background, and with animations off);
 * the divider drags from its handle, and a tap opens AMOLED Studio. "Try a look" previews a
 * preset in place: nothing is saved or applied.
 *
 * @param theme The rules theme AMOLED uses (null: the built-in rules)
 * @param active Home is in front: false while something covers it (About, a sheet), which pauses
 *   the auto-advance
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShowcaseCarousel(
    theme: String?,
    onOpenStudio: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = true,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val resume = rememberResumeCount()
    val looks by rememberRoot(theme, resume, initial = RebornCache.showcase) {
        loadLooks(context, theme).also { RebornCache.showcase = it }
    }
    val current = looks
    // A preset tried here (its id), or null for the saved look
    var trying by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = trying ?: current?.savedPreset
    val roles = when {
        current == null -> MockRoles.Empty
        trying != null -> current.previews.presets[trying] ?: current.previews.look
        else -> current.previews.look
    }

    val pages = ShowcasePage.entries
    val pager = rememberPagerState { pages.size }
    // Animations off in the system settings, or a screen reader on: no auto-advance and no sweeps
    val motion = remember {
        val animations = Settings.Global.getFloat(context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        val reader = context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true
        animations && !reader
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifeState by lifecycle.currentStateAsState()
    val resumed = lifeState.isAtLeast(Lifecycle.State.RESUMED)
    var touched by remember { mutableStateOf(false) }
    val paging = MaterialTheme.motionScheme.slowSpatialSpec<Float>()

    LaunchedEffect(resumed, touched, motion, active) {
        if (!resumed || touched || !motion || !active) return@LaunchedEffect
        while (true) {
            delay(ADVANCE_MILLIS)
            if (pager.isScrollInProgress) continue
            pager.animateScrollToPage((pager.currentPage + 1) % pages.size, animationSpec = paging)
        }
    }

    SectionCard(
        modifier = modifier.pointerInput(Unit) {
            // Watches every touch on the card without taking it: auto-advance waits meanwhile
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                touched = true
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                } while (event.changes.any { it.pressed })
                touched = false
            }
        },
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 2.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.reborn_showcase_title),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                )
                Text(
                    stringResource(R.string.reborn_showcase_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalIconButton(onClick = onOpenStudio) {
                Icon(
                    painterResource(R.drawable.ic_reborn_chevron),
                    contentDescription = stringResource(R.string.reborn_showcase_open),
                )
            }
        }

        HorizontalPager(
            state = pager,
            pageSpacing = 12.dp,
            beyondViewportPageCount = 1,
            key = { pages[it].name },
            modifier = Modifier.fillMaxWidth(),
        ) { index ->
            val page = pages[index]
            BeforeAfter(
                beforeLabel = stringResource(R.string.reborn_studio_stock),
                afterLabel = stringResource(R.string.reborn_studio_amoled),
                description = stringResource(R.string.reborn_showcase_page_description, stringResource(page.title)),
                frameShape = RoundedCornerShape(22.dp),
                onClick = onOpenStudio,
                handleOnly = true,
                // Each page sweeps its divider across as it comes into view
                sweep = motion && pager.settledPage == index,
            ) { side ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(PageHeight)
                        .background(Color.Black),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(Alignment.Top, unbounded = true)
                            .padding(6.dp),
                    ) {
                        val fill = Modifier.fillMaxWidth()
                        when (page) {
                            ShowcasePage.QuickSettings -> QsMock(roles, side, fill)
                            ShowcasePage.Settings -> SettingsMock(roles, side, fill)
                            ShowcasePage.Apps -> ListMock(roles, side, fill)
                            ShowcasePage.Popups -> PopupMock(roles, side, fill)
                        }
                    }
                    // What does not fit fades into the frame
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(40.dp)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black))),
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        val motion = MaterialTheme.motionScheme
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(
                targetState = pages[pager.currentPage],
                transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
                label = "showcasePage",
                modifier = Modifier.weight(1f),
            ) { page ->
                Column {
                    Text(
                        stringResource(page.title),
                        style = MaterialTheme.typography.titleSmallEmphasized,
                    )
                    Text(
                        stringResource(page.detail),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            PageDots(
                count = pages.size,
                current = pager.currentPage,
                onPick = { index ->
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    scope.launch { pager.animateScrollToPage(index) }
                },
            )
        }

        Spacer(Modifier.height(18.dp))
        Text(
            stringResource(R.string.reborn_showcase_try),
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // The saved look when it has no chip of its own here (custom, or a look this row
            // leaves out)
            val saved = current?.savedPreset
            if (saved != null && SHOWN_PRESETS.none { it.id == saved }) {
                LookChip(
                    label = stringResource(R.string.reborn_showcase_yours),
                    selected = trying == null,
                    onClick = { trying = null },
                )
            }
            SHOWN_PRESETS.forEach { preset ->
                LookChip(
                    label = stringResource(presetName(preset.id)),
                    selected = shown == preset.id,
                    saved = current?.savedPreset == preset.id,
                    enabled = current != null,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        trying = if (preset.id == current?.savedPreset) null else preset.id
                    },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        AnimatedContent(
            targetState = trying != null && trying != current?.savedPreset,
            transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
            label = "tryCaption",
            modifier = Modifier.padding(horizontal = 8.dp),
        ) { previewing ->
            Text(
                stringResource(if (previewing) R.string.reborn_showcase_try_preview else R.string.reborn_showcase_try_saved),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@StringRes
private fun presetName(id: String): Int = when (id) {
    AmoledPresets.PURE_BLACK -> R.string.amoled_preset_pure
    AmoledPresets.ELEVATED_CARDS -> R.string.amoled_preset_elevated
    AmoledPresets.DIM -> R.string.amoled_preset_dim
    else -> R.string.amoled_preset_custom
}

@Composable
private fun LookChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    saved: Boolean = false,
    enabled: Boolean = true,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label, maxLines = 1) },
        leadingIcon = if (saved) {
            {
                Icon(
                    painterResource(R.drawable.ic_reborn_check),
                    contentDescription = stringResource(R.string.reborn_showcase_saved),
                    modifier = Modifier.size(18.dp),
                )
            }
        } else null,
        shape = CircleShape,
    )
}

/** The page indicator: the current page is a pill, the others dots; each one jumps to its page. */
@Composable
private fun PageDots(count: Int, current: Int, onPick: (Int) -> Unit) {
    val description = stringResource(R.string.reborn_showcase_page_of, current + 1, count)
    Row(
        modifier = Modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val on = index == current
            val width by animateDpAsState(
                if (on) 22.dp else 8.dp,
                spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
                label = "dotWidth",
            )
            val color by animateColorAsState(
                if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                label = "dotColor",
            )
            Box(
                Modifier
                    .clip(CircleShape)
                    .clickable { onPick(index) }
                    .semantics { selected = on }
                    .padding(horizontal = 3.dp, vertical = 10.dp),
            ) {
                Box(
                    Modifier
                        .width(width)
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(color),
                )
            }
        }
    }
}
