/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.home

import android.text.format.DateUtils
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import projekt.substratum.R
import projekt.substratum.common.platform.AmoledJobs
import projekt.substratum.common.platform.AmoledLevel
import projekt.substratum.common.platform.AmoledPresets
import projekt.substratum.reborn.RebornActions
import projekt.substratum.reborn.amoled.AmoledSummary
import projekt.substratum.reborn.amoled.JobProgress
import projekt.substratum.reborn.amoled.JobUi
import projekt.substratum.reborn.applied.AppliedSnapshot
import projekt.substratum.reborn.applied.AppliedSource
import projekt.substratum.reborn.components.ChipState
import projekt.substratum.reborn.components.HeroShape
import projekt.substratum.reborn.components.MorphingShape
import projekt.substratum.reborn.components.PaletteStrip
import projekt.substratum.reborn.components.StatusChip
import projekt.substratum.reborn.components.design.overline
import projekt.substratum.reborn.nav.ThemeId
import projekt.substratum.reborn.theme.LocalRebornColors
import projekt.substratum.reborn.themes.ThemeLooks
import projekt.substratum.reborn.themes.ThemesSnapshot

/** The engine theme's name: the one in use, else the built-in Reborn AMOLED (also for its starter APK). */
@Composable
internal fun amoledName(amoled: AmoledSummary?): String {
    val theme = amoled?.theme
    return if (amoled == null || theme == null || theme == RebornActions.AMOLED_THEME || amoled.themeName.isNullOrBlank()) {
        stringResource(R.string.reborn_builtin_amoled_name)
    } else amoled.themeName.toString()
}

/** The classic theme with the most overlays on now: its package, name (as Applied knows it) and count. */
internal class ClassicInUse(val pkg: String, val label: String?, val overlays: Int)

internal fun classicInUse(applied: AppliedSnapshot?): ClassicInUse? {
    val on = applied?.entries.orEmpty().filter { it.source == AppliedSource.THEME_OVERLAY && it.enabled && it.themePkg != null }
    val top = on.groupBy { it.themePkg!! }.maxByOrNull { it.value.size } ?: return null
    return ClassicInUse(top.key, top.value.firstNotNullOfOrNull { it.themeLabel }, top.value.size)
}

/**
 * The theme the Now card leads with: the engine theme when it is on, else the classic theme with
 * the most overlays on, else none (stock).
 */
internal fun nowTheme(applied: AppliedSnapshot?): ThemeId? = when {
    applied == null -> null
    applied.amoled.enabled -> ThemeId.ofPackage(applied.amoled.theme)
    else -> classicInUse(applied)?.let { ThemeId.Apk(it.pkg) }
}

/** What the Now card shows. */
private sealed interface Now {
    data object Reading : Now
    data object Stock : Now
    data class Applying(val job: JobUi, val name: String) : Now
    data class Engine(
        val id: ThemeId,
        val name: String,
        val amoled: AmoledSummary,
        val classic: ClassicInUse?,
        val classicName: String?,
    ) : Now {
        /** On, but no apply ever finished recording its time */
        val interrupted: Boolean get() = amoled.appliedAt <= 0L
        val needsReapply: Boolean get() = interrupted || amoled.restartNeeded > 0
    }
    data class Classic(val id: ThemeId, val name: String, val overlays: Int) : Now
}

/**
 * Home's lead: the theme in use, as a colour-blocked card. Its name and look on the accent block
 * (with the look's palette and a slowly breathing shape), the count of apps it themes, when it was
 * applied (and whether it survived a reboot, needs a restart or was interrupted), then Customize
 * and Change theme. While the engine works it shows the job's progress; on a stock phone it
 * points to Themes.
 *
 * @param looks The previews of the theme in use ([nowTheme]), for its palette and look summary
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NowCard(
    applied: AppliedSnapshot?,
    themes: ThemesSnapshot?,
    job: JobUi?,
    looks: ThemeLooks?,
    onCustomize: () -> Unit,
    onChangeTheme: () -> Unit,
    onOpenTheme: (ThemeId) -> Unit,
    onReapply: () -> Unit,
    onOpenStudio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val builtIn = stringResource(R.string.reborn_builtin_amoled_name)
    val engineName = amoledName(applied?.amoled)
    val running = job?.takeIf { it.kind != AmoledJobs.Kind.SET_ENABLED }
    val now: Now = when {
        running != null -> {
            val id = ThemeId.ofPackage(running.theme ?: applied?.amoled?.theme)
            Now.Applying(running, themes?.find(id)?.name ?: if (id == ThemeId.RebornAmoled) builtIn else engineName)
        }
        applied == null -> Now.Reading
        applied.amoled.enabled -> {
            val id = ThemeId.ofPackage(applied.amoled.theme)
            val classic = classicInUse(applied)
            Now.Engine(
                id = id,
                name = themes?.find(id)?.name ?: engineName,
                amoled = applied.amoled,
                classic = classic,
                classicName = classic?.let { c -> themes?.find(ThemeId.Apk(c.pkg))?.name ?: c.label ?: c.pkg },
            )
        }
        else -> classicInUse(applied)?.let { c ->
            val id = ThemeId.Apk(c.pkg)
            Now.Classic(id, themes?.find(id)?.name ?: c.label ?: c.pkg, c.overlays)
        } ?: Now.Stock
    }
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = now,
        contentKey = { it::class },
        transitionSpec = {
            fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) using
                SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() }
        },
        modifier = modifier.fillMaxWidth(),
        label = "now",
    ) { state ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (state) {
                Now.Reading -> AccentBlock(
                    overline = stringResource(R.string.reborn_now_overline),
                    title = stringResource(R.string.reborn_reading),
                    live = false,
                )
                Now.Stock -> {
                    AccentBlock(
                        overline = stringResource(R.string.reborn_now_overline),
                        title = stringResource(R.string.reborn_now_stock_title),
                        supporting = stringResource(R.string.reborn_now_stock_text),
                        live = false,
                        shape = BlockShape.Single,
                    )
                    Actions(
                        primary = stringResource(R.string.reborn_now_explore) to R.drawable.ic_reborn_themes,
                        onPrimary = onChangeTheme,
                    )
                }
                is Now.Applying -> {
                    AccentBlock(
                        overline = stringResource(R.string.reborn_now_overline),
                        title = stringResource(
                            when (state.job.kind) {
                                AmoledJobs.Kind.REMOVE -> R.string.reborn_now_removing
                                AmoledJobs.Kind.RESTORE -> R.string.reborn_now_restoring
                                else -> R.string.reborn_now_applying
                            },
                            state.name,
                        ),
                        live = true,
                        shape = BlockShape.Top,
                    )
                    Surface(
                        shape = BlockShape.Bottom.shape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) { JobProgress(state.job) }
                    }
                    Actions(
                        primary = stringResource(R.string.reborn_now_open_studio) to R.drawable.ic_reborn_tune,
                        onPrimary = onOpenStudio,
                    )
                }
                is Now.Engine -> {
                    AccentBlock(
                        overline = stringResource(R.string.reborn_now_overline),
                        title = state.name,
                        supporting = lookSummary(looks?.takeIf { it.id == state.id }, state.amoled.themed),
                        looks = looks?.takeIf { it.id == state.id },
                        live = true,
                        shape = BlockShape.Top,
                    )
                    CountRow(
                        count = state.amoled.themed,
                        countLabel = stringResource(R.string.reborn_now_apps_themed),
                        detailTitle = when {
                            state.interrupted -> stringResource(R.string.reborn_now_applied_unknown)
                            else -> stringResource(R.string.reborn_now_applied_at, relativeTime(state.amoled.appliedAt))
                        },
                    ) {
                        if (state.classic != null && state.classicName != null) {
                            Text(
                                pluralStringResource(R.plurals.reborn_now_plus_overlays, state.classic.overlays,
                                    state.classic.overlays, state.classicName),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        EngineChips(state)
                    }
                    if (state.needsReapply) {
                        Actions(
                            primary = stringResource(R.string.reborn_now_reapply) to R.drawable.ic_reborn_refresh,
                            onPrimary = onReapply,
                            secondary = stringResource(R.string.reborn_now_customize),
                            onSecondary = onCustomize,
                        )
                    } else {
                        Actions(
                            primary = stringResource(R.string.reborn_now_customize) to R.drawable.ic_reborn_tune,
                            onPrimary = onCustomize,
                            secondary = stringResource(R.string.reborn_now_change),
                            onSecondary = onChangeTheme,
                        )
                    }
                }
                is Now.Classic -> {
                    val own = looks?.takeIf { it.id == state.id && it.coverage > 0 }
                    AccentBlock(
                        overline = stringResource(R.string.reborn_now_overline),
                        title = state.name,
                        supporting = pluralStringResource(R.plurals.reborn_now_overlays, state.overlays, state.overlays),
                        looks = own,
                        live = true,
                        shape = BlockShape.Top,
                    )
                    CountRow(
                        count = state.overlays,
                        countLabel = stringResource(R.string.reborn_now_overlays_on),
                        detailTitle = stringResource(R.string.reborn_now_classic),
                    ) {
                        Text(
                            stringResource(R.string.reborn_now_classic_text),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Actions(
                        primary = stringResource(R.string.reborn_now_options) to R.drawable.ic_reborn_layers,
                        onPrimary = { onOpenTheme(state.id) },
                        secondary = stringResource(R.string.reborn_now_change),
                        onSecondary = onChangeTheme,
                    )
                }
            }
        }
    }
}

/** The blocks' corners: 32 dp outside, 8 dp where they meet. */
private enum class BlockShape(val shape: RoundedCornerShape) {
    Single(RoundedCornerShape(32.dp)),
    Top(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp, bottomStart = 8.dp, bottomEnd = 8.dp)),
    Bottom(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 32.dp, bottomEnd = 32.dp)),
    BottomStart(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 32.dp, bottomEnd = 8.dp)),
    BottomEnd(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 32.dp)),
}

/**
 * The accent block: the theme's colour (primaryFixedDim), a live dot and "Now", the name in a
 * display face, a line about it and its palette; a shape breathes in its corner.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun AccentBlock(
    overline: String,
    title: String,
    live: Boolean,
    supporting: String? = null,
    looks: ThemeLooks? = null,
    shape: BlockShape = BlockShape.Top,
) {
    val scheme = MaterialTheme.colorScheme
    val container = scheme.primaryFixedDim
    val content = scheme.onPrimaryFixed
    Surface(
        shape = shape.shape,
        color = container,
        contentColor = content,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.clipToBounds()) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 34.dp, y = (-18).dp)
                    .size(150.dp)
                    .graphicsLayer { alpha = 0.9f },
            ) {
                MorphingShape(HeroShape.Calm, Color.Black.copy(alpha = 0.18f), Modifier.size(150.dp))
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 168.dp)
                    .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 22.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveDot(live, content)
                    Spacer(Modifier.width(8.dp))
                    Text(overline, style = MaterialTheme.typography.overline, color = content)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.displaySmallEmphasized,
                    color = content,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(end = 56.dp)
                        .semantics { heading() },
                )
                if (supporting != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        supporting,
                        style = MaterialTheme.typography.bodyLargeEmphasized,
                        color = content.copy(alpha = 0.82f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val shown = looks?.shown
                if (shown != null) {
                    Spacer(Modifier.height(14.dp))
                    PaletteStrip(shown.roles, ring = container)
                }
            }
        }
    }
}

/** An 8 dp dot whose ring pulses out while something is live. */
@Composable
private fun LiveDot(live: Boolean, color: Color) {
    val transition = rememberInfiniteTransition(label = "live")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "livePulse",
    )
    Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
        if (live) {
            Box(
                Modifier
                    .size(8.dp + 14.dp * pulse)
                    .graphicsLayer { alpha = 0.35f * (1f - pulse) }
                    .background(color, CircleShape),
            )
        }
        Box(
            Modifier
                .size(8.dp)
                .background(if (live) color else color.copy(alpha = 0.5f), CircleShape),
        )
    }
}

/** The count block (tertiary) and the detail block side by side. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CountRow(
    count: Int,
    countLabel: String,
    detailTitle: String,
    detail: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalRebornColors.current.dark
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Surface(
            shape = BlockShape.BottomStart.shape,
            color = if (dark) scheme.tertiaryContainer else scheme.tertiaryFixedDim,
            contentColor = if (dark) scheme.onTertiaryContainer else scheme.onTertiaryFixed,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(
                    count.toString(),
                    style = MaterialTheme.typography.displayMediumEmphasized.copy(fontFeatureSettings = "tnum"),
                    color = if (dark) scheme.tertiary else scheme.onTertiaryFixed,
                    maxLines = 1,
                )
                Text(countLabel, style = MaterialTheme.typography.labelLargeEmphasized, maxLines = 2)
            }
        }
        Surface(
            shape = BlockShape.BottomEnd.shape,
            color = scheme.surfaceContainerHigh,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            Column(
                Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    detailTitle,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                detail()
            }
        }
    }
}

/** Survived a reboot, apps that need a restart, an interrupted apply. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EngineChips(state: Now.Engine) {
    val amoled = state.amoled
    if (!state.interrupted && !amoled.survivedReboot && amoled.restartNeeded <= 0) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (state.interrupted) StatusChip(stringResource(R.string.reborn_amoled_interrupted), ChipState.Warn)
        if (amoled.survivedReboot) StatusChip(stringResource(R.string.reborn_survived_reboot), ChipState.OK)
        if (amoled.restartNeeded > 0) {
            StatusChip(stringResource(R.string.reborn_restart_needed, amoled.restartNeeded), ChipState.Warn)
        }
    }
}

/** The card's buttons: the main action filled, the other tonal, both 56 dp. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Actions(
    primary: Pair<String, Int>,
    onPrimary: () -> Unit,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
) {
    val haptics = LocalHapticFeedback.current
    val height = ButtonDefaults.MediumContainerHeight
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onPrimary()
            },
            shapes = ButtonDefaults.shapesFor(height),
            contentPadding = ButtonDefaults.contentPaddingFor(height),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = height),
        ) {
            ButtonContent(primary.first, primary.second)
        }
        if (secondary != null) {
            FilledTonalButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onSecondary()
                },
                shapes = ButtonDefaults.shapesFor(height),
                contentPadding = ButtonDefaults.contentPaddingFor(height),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = height),
            ) {
                Text(secondary, style = ButtonDefaults.textStyleFor(height), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ButtonContent(label: String, @DrawableRes icon: Int) {
    val height = ButtonDefaults.MediumContainerHeight
    Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)))
    Spacer(Modifier.width(8.dp))
    Text(label, style = ButtonDefaults.textStyleFor(height), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** "Elevated · Monet accent · 229 apps": the level, the accent (when one is chosen) and the app count. */
@Composable
private fun lookSummary(looks: ThemeLooks?, apps: Int): String {
    val parts = ArrayList<String>()
    looks?.level?.let { parts += stringResource(levelName(it)) }
    accentName(looks?.accent)?.let { parts += stringResource(it) }
    parts += pluralStringResource(R.plurals.reborn_now_apps, apps, apps)
    return parts.joinToString(" · ")
}

private fun levelName(level: AmoledLevel): Int = when (level) {
    AmoledLevel.PURE -> R.string.amoled_level_pure
    AmoledLevel.DIM -> R.string.amoled_level_dim
    AmoledLevel.ELEVATED -> R.string.amoled_level_elevated
    AmoledLevel.TINTED -> R.string.amoled_level_tinted
}

private fun accentName(accent: String?): Int? = when {
    accent == null || accent == AmoledPresets.ACCENT_NONE -> null
    accent == AmoledPresets.ACCENT_MONET -> R.string.reborn_now_accent_monet
    accent == AmoledPresets.ACCENT_ONEUI -> R.string.reborn_now_accent_oneui
    accent == AmoledPresets.ACCENT_SPRITE -> R.string.amoled_accent_sprite
    accent == AmoledPresets.ACCENT_WHITE -> R.string.reborn_now_accent_white
    AmoledPresets.isCustom(accent) -> R.string.reborn_now_accent_custom
    else -> null
}

@Composable
private fun relativeTime(time: Long): String =
    if (time <= 0L) stringResource(R.string.reborn_never)
    else DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS).toString()
