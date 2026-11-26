/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.home

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonGroupScope
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import projekt.substratum.R
import projekt.substratum.common.platform.AmoledJobs
import projekt.substratum.common.platform.AmoledPresets
import projekt.substratum.reborn.RebornActions
import projekt.substratum.reborn.amoled.AmoledSummary
import projekt.substratum.reborn.amoled.JobProgress
import projekt.substratum.reborn.amoled.JobUi
import projekt.substratum.reborn.applied.AppliedSnapshot
import projekt.substratum.reborn.applied.AppliedSource
import projekt.substratum.reborn.components.AmbientMorph
import projekt.substratum.reborn.components.AppIcon
import projekt.substratum.reborn.components.design.RebornRounded
import projekt.substratum.reborn.components.design.softTitle
import projekt.substratum.reborn.nav.ThemeId
import projekt.substratum.reborn.theme.LocalRebornColors
import projekt.substratum.reborn.themes.ThemeLooks
import projekt.substratum.reborn.themes.ThemesSnapshot
import projekt.substratum.reborn.themes.levelName

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

/** What the Now card (and the quick actions under it) show. */
internal sealed interface Now {
    data object Reading : Now
    data object Stock : Now
    data class Applying(val job: JobUi, val name: String) : Now
    data class Engine(
        val id: ThemeId,
        val name: String,
        val amoled: AmoledSummary,
    ) : Now {
        /** On, but no apply ever finished recording its time */
        val interrupted: Boolean get() = amoled.appliedAt <= 0L
        val needsReapply: Boolean get() = interrupted || amoled.restartNeeded > 0
    }
    data class Classic(val id: ThemeId, val pkg: String, val name: String, val overlays: Int) : Now
}

/** What is applied now, from the Applied read, the themes and the engine's running job. */
@Composable
internal fun nowState(applied: AppliedSnapshot?, themes: ThemesSnapshot?, job: JobUi?): Now {
    val builtIn = stringResource(R.string.reborn_builtin_amoled_name)
    val engineName = amoledName(applied?.amoled)
    val running = job?.takeIf { it.kind != AmoledJobs.Kind.SET_ENABLED }
    return when {
        running != null -> {
            val id = ThemeId.ofPackage(running.theme ?: applied?.amoled?.theme)
            Now.Applying(running, themes?.find(id)?.name ?: if (id == ThemeId.RebornAmoled) builtIn else engineName)
        }
        applied == null -> Now.Reading
        applied.amoled.enabled -> {
            val id = ThemeId.ofPackage(applied.amoled.theme)
            Now.Engine(id = id, name = themes?.find(id)?.name ?: engineName, amoled = applied.amoled)
        }
        else -> classicInUse(applied)?.let { c ->
            val id = ThemeId.Apk(c.pkg)
            Now.Classic(id, c.pkg, themes?.find(id)?.name ?: c.label ?: c.pkg, c.overlays)
        } ?: Now.Stock
    }
}

/** The blocks' corners: 32 dp outside, 8 dp where they meet. */
private enum class BlockShape(val shape: RoundedCornerShape) {
    Top(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp, bottomStart = 8.dp, bottomEnd = 8.dp)),
    Bottom(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 32.dp, bottomEnd = 32.dp)),
    BottomStart(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 32.dp, bottomEnd = 8.dp)),
    BottomEnd(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 32.dp)),
}

/** The theme's name on the accent block: 44/46 sp, weight 650, ROND 100. */
private val NowTitle: TextStyle
    @Composable get() = MaterialTheme.typography.displayMedium.copy(
        fontFamily = RebornRounded,
        fontWeight = FontWeight(650),
        fontSize = 44.sp,
        lineHeight = 46.sp,
        letterSpacing = (-1).sp,
    )

/** The count on the tertiary block: 52/56 sp, weight 650, ROND 100, tabular figures. */
private val NowCount: TextStyle
    @Composable get() = MaterialTheme.typography.displayLarge.copy(
        fontFamily = RebornRounded,
        fontWeight = FontWeight(650),
        fontSize = 52.sp,
        lineHeight = 56.sp,
        letterSpacing = (-1.5).sp,
        fontFeatureSettings = "tnum",
    )

/** The chips' 13 sp bold label */
private val ChipText: TextStyle
    @Composable get() = MaterialTheme.typography.labelLargeEmphasized.copy(fontSize = 13.sp, lineHeight = 20.sp)

/**
 * Home's lead, the theme in use as one colour-blocked card (blocks 4 dp apart, 32 dp outside, 8 dp
 * where they meet). Block A, in the theme's accent: a live dot and "Now", the theme's name, its
 * look and accent as chips, and a black Cookie9Sided ↔ Sunny shape breathing off its top right
 * edge with the theme's glyph. Under it, block B (tertiary): how many apps it themes (opening
 * Applied); block C: what changed since the last apply (new apps, with their icons; apps to
 * restart; an interrupted apply). With nothing applied, A says so with "Pick a theme", and B and C
 * count the installed themes and point to Create. While the engine works, its progress replaces
 * B and C.
 *
 * @param installedThemes The themes Themes lists (for the stock card)
 * @param newApps Apps installed since the last apply the engine has not read ([readNewApps])
 * @param running The ambient shape moves (false while About or a sheet covers Home)
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun NowCard(
    now: Now,
    looks: ThemeLooks?,
    installedThemes: Int?,
    newApps: List<String>?,
    onPickTheme: () -> Unit,
    onOpenCreate: () -> Unit,
    onOpenApplied: () -> Unit,
    modifier: Modifier = Modifier,
    running: Boolean = true,
) {
    val motion = MaterialTheme.motionScheme
    val overline = stringResource(R.string.reborn_now_overline)
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
                Now.Reading -> {
                    AccentBlock(overline, stringResource(R.string.reborn_reading), live = false, R.drawable.ic_reborn_themes_filled, running)
                    BlockRow(
                        count = { CountBlock("–", stringResource(R.string.reborn_now_apps_themed), null, it) },
                        detail = { DetailBlock(stringResource(R.string.reborn_now_since), "–", emptyList(), null, it) },
                    )
                }
                Now.Stock -> {
                    AccentBlock(overline, stringResource(R.string.reborn_now_none_title), live = false, R.drawable.ic_reborn_themes_filled, running) {
                        PickButton(onPickTheme)
                    }
                    BlockRow(
                        count = {
                            CountBlock(
                                installedThemes?.toString() ?: "–",
                                pluralStringResource(R.plurals.reborn_now_themes_installed, installedThemes ?: 2),
                                onPickTheme,
                                it,
                            )
                        },
                        detail = {
                            DetailBlock(stringResource(R.string.reborn_now_start_fresh), stringResource(R.string.reborn_now_make_own), emptyList(), onOpenCreate, it)
                        },
                    )
                }
                is Now.Applying -> {
                    AccentBlock(
                        overline,
                        stringResource(
                            when (state.job.kind) {
                                AmoledJobs.Kind.REMOVE -> R.string.reborn_now_removing
                                AmoledJobs.Kind.RESTORE -> R.string.reborn_now_restoring
                                else -> R.string.reborn_now_applying
                            },
                            state.name,
                        ),
                        live = true,
                        R.drawable.ic_reborn_amoled_filled,
                        running,
                    )
                    Surface(
                        shape = BlockShape.Bottom.shape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) { JobProgress(state.job) }
                    }
                }
                is Now.Engine -> {
                    val own = looks?.takeIf { it.id == state.id }
                    AccentBlock(overline, state.name, live = true, R.drawable.ic_reborn_amoled_filled, running) {
                        own?.level?.let { DarkChip(stringResource(levelName(it)), R.drawable.ic_reborn_amoled_filled) }
                        accentName(own?.accent)?.let { LineChip(stringResource(it), R.drawable.ic_reborn_themes) }
                    }
                    val restart = state.amoled.restartNeeded
                    val apps = newApps
                    val none = emptyList<String>()
                    val (value, icons) = when {
                        state.interrupted -> stringResource(R.string.reborn_now_interrupted) to none
                        restart > 0 -> pluralStringResource(R.plurals.reborn_now_to_restart, restart, restart) to none
                        apps == null -> "–" to none
                        apps.isNotEmpty() -> pluralStringResource(R.plurals.reborn_now_new_apps, apps.size, apps.size) to apps
                        else -> stringResource(R.string.reborn_now_up_to_date) to none
                    }
                    BlockRow(
                        count = { CountBlock(state.amoled.themed.toString(), stringResource(R.string.reborn_now_apps_themed), onOpenApplied, it) },
                        detail = { DetailBlock(stringResource(R.string.reborn_now_since), value, icons, null, it) },
                    )
                }
                is Now.Classic -> {
                    AccentBlock(overline, state.name, live = true, R.drawable.ic_reborn_applied_filled, running) {
                        DarkChip(stringResource(R.string.reborn_now_chip_classic), R.drawable.ic_reborn_applied_filled)
                        LineChip(pluralStringResource(R.plurals.reborn_now_overlays, state.overlays, state.overlays), R.drawable.ic_reborn_layers)
                    }
                    BlockRow(
                        count = { CountBlock(state.overlays.toString(), stringResource(R.string.reborn_now_overlays_on), onOpenApplied, it) },
                        detail = {
                            DetailBlock(stringResource(R.string.reborn_now_classic), stringResource(R.string.reborn_now_classic_native), emptyList(), null, it)
                        },
                    )
                }
            }
        }
    }
}

/**
 * Block A: 196 dp in the theme's accent (primaryFixedDim; primaryFixed in light mode, as the
 * mockup's light frame has it) with onPrimaryFixed content. The overline, the name on up to two
 * lines, then [bottom] (chips, or the stock card's button) 20 dp from the bottom; a 164 dp black
 * Cookie9Sided ↔ Sunny shape (7 s, one turn a minute) sits partly off the top right edge with
 * [icon] in primaryFixedDim.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AccentBlock(
    overline: String,
    title: String,
    live: Boolean,
    @DrawableRes icon: Int,
    running: Boolean,
    bottom: @Composable RowScope.() -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalRebornColors.current.dark
    val container = if (dark) scheme.primaryFixedDim else scheme.primaryFixed
    val content = scheme.onPrimaryFixed
    Surface(
        shape = BlockShape.Top.shape,
        color = container,
        contentColor = content,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.heightIn(min = 196.dp)) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 34.dp, y = 16.dp)
                    .size(164.dp),
                contentAlignment = Alignment.Center,
            ) {
                AmbientMorph(
                    from = MaterialShapes.Cookie9Sided,
                    to = MaterialShapes.Sunny,
                    color = Color.Black,
                    modifier = Modifier.fillMaxSize(),
                    periodMillis = 7000,
                    turnMillis = 60000,
                    running = running,
                )
                Icon(painterResource(icon), contentDescription = null, tint = scheme.primaryFixedDim, modifier = Modifier.size(52.dp))
            }
            Column(
                Modifier
                    .heightIn(min = 196.dp)
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveDot(live, running, content)
                    Spacer(Modifier.width(8.dp))
                    Text(overline, style = MaterialTheme.typography.labelLargeEmphasized, color = content)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    title,
                    style = NowTitle,
                    color = content,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        // Wraps before the shape, as "Reborn⏎AMOLED"
                        .padding(end = 72.dp)
                        .semantics { heading() },
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = bottom)
            }
        }
    }
}

/**
 * An 8 dp dot whose ring pulses out (0 → 7 dp, 35 % → clear, every 2.4 s) while something is live.
 * The pulse rests while Home is covered ([running] false), like the ambient shape.
 */
@Composable
private fun LiveDot(live: Boolean, running: Boolean, color: Color) {
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(live && running) {
        if (!live || !running) {
            pulse.snapTo(1f)
            return@LaunchedEffect
        }
        while (true) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, tween(2400, easing = LinearEasing))
        }
    }
    Canvas(Modifier.size(8.dp)) {
        val r = size.minDimension / 2f
        if (live) {
            val p = (pulse.value / 0.7f).coerceAtMost(1f)
            if (p < 1f) drawCircle(color.copy(alpha = 0.35f * (1f - p)), radius = r + 7.dp.toPx() * p)
        }
        drawCircle(if (live) color else color.copy(alpha = 0.5f), radius = r)
    }
}

/** The look chip: black, with primaryFixed text and a filled glyph. */
@Composable
private fun DarkChip(text: String, @DrawableRes icon: Int) {
    val tint = MaterialTheme.colorScheme.primaryFixed
    Row(
        Modifier
            .height(32.dp)
            .background(Color.Black, CircleShape)
            .padding(start = 10.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = ChipText, color = tint, maxLines = 1)
    }
}

/** The accent chip: a 1.5 dp outline in the block's content colour at 28 %. */
@Composable
private fun LineChip(text: String, @DrawableRes icon: Int) {
    val content = MaterialTheme.colorScheme.onPrimaryFixed
    Row(
        Modifier
            .height(32.dp)
            .border(1.5.dp, content.copy(alpha = 0.28f), CircleShape)
            .padding(start = 10.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = ChipText, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The stock card's way out: a black "Pick a theme" button (to Themes). */
@Composable
private fun PickButton(onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Button(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            onClick()
        },
        shapes = ButtonDefaults.shapes(),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = MaterialTheme.colorScheme.primaryFixed),
        contentPadding = PaddingValues(start = 12.dp, end = 16.dp),
        modifier = Modifier.height(40.dp),
    ) {
        Icon(painterResource(R.drawable.ic_reborn_themes), contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.reborn_now_pick), style = MaterialTheme.typography.labelLargeEmphasized)
    }
}

/** Blocks B and C side by side, 4 dp apart, as tall as the taller one (120 dp at least). */
@Composable
private fun BlockRow(count: @Composable (Modifier) -> Unit, detail: @Composable (Modifier) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        count(
            Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        detail(
            Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
    }
}

/** A block's surface, a button when it leads somewhere. */
@Composable
private fun BlockSurface(
    shape: Shape,
    container: Color,
    content: Color,
    onClick: (() -> Unit)?,
    modifier: Modifier,
    body: @Composable () -> Unit,
) {
    if (onClick != null) {
        Surface(onClick = onClick, shape = shape, color = container, contentColor = content, modifier = modifier, content = body)
    } else {
        Surface(shape = shape, color = container, contentColor = content, modifier = modifier, content = body)
    }
}

/**
 * Block B: tertiaryFixedDim (tertiaryFixed in light mode), the count in the rounded face with
 * tabular figures, its label, and a Clover4Leaf plate with a check at the top right.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CountBlock(count: String, label: String, onClick: (() -> Unit)?, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalRebornColors.current.dark
    val content = scheme.onTertiaryFixed
    val plate = MaterialShapes.Clover4Leaf.toShape()
    BlockSurface(
        shape = BlockShape.BottomStart.shape,
        container = if (dark) scheme.tertiaryFixedDim else scheme.tertiaryFixed,
        content = content,
        onClick = onClick,
        modifier = modifier.heightIn(min = 120.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(count, style = NowCount, color = content, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = content.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 14.dp, end = 14.dp)
                    .size(36.dp)
                    .clip(plate)
                    .background(content),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_reborn_check),
                    contentDescription = null,
                    tint = scheme.tertiaryFixedDim,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * Block C (surfaceContainerHigh): a 12 sp label, the value at 22 sp, and up to three overlapping
 * app icons (2.5 dp ring in the block's colour) with "+N" for the rest.
 */
@Composable
private fun DetailBlock(key: String, value: String, apps: List<String>, onClick: (() -> Unit)?, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val container = scheme.surfaceContainerHigh
    BlockSurface(
        shape = BlockShape.BottomEnd.shape,
        container = container,
        content = scheme.onSurface,
        onClick = onClick,
        modifier = modifier.heightIn(min = 120.dp),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                key,
                style = MaterialTheme.typography.labelMediumEmphasized,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.softTitle,
                maxLines = if (apps.isEmpty()) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (apps.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                AppStack(apps, ring = container)
            }
        }
    }
}

/**
 * Up to three app icons, 28 dp with 9 dp corners, overlapping by 6 dp; "+N" for the rest. Their
 * 2.5 dp ring is drawn outside them, so it takes no room (the block stays 120 dp).
 */
@Composable
private fun AppStack(apps: List<String>, ring: Color) {
    val corner = RoundedCornerShape(9.dp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
            apps.take(3).forEach { pkg ->
                Box(
                    Modifier
                        .size(28.dp)
                        .drawBehind {
                            val out = 2.5.dp.toPx()
                            drawRoundRect(
                                ring,
                                topLeft = Offset(-out, -out),
                                size = Size(size.width + 2 * out, size.height + 2 * out),
                                cornerRadius = CornerRadius(11.5.dp.toPx()),
                            )
                        },
                ) {
                    AppIcon(pkg, Modifier.clip(corner), size = 28.dp, shape = corner)
                }
            }
        }
        if (apps.size > 3) {
            Spacer(Modifier.width(8.dp))
            Text(
                "+" + (apps.size - 3),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The quick actions under the Now card: Re-apply, Customize and Undo as one ButtonGroup of 56 dp
 * tonal buttons (Undo a little narrower); a pressed one widens and its neighbours make room.
 * Re-apply turns filled when the theme needs it (an apply was interrupted, apps wait for a
 * restart).
 */
@Composable
internal fun NowActions(
    reapplyEnabled: Boolean,
    reapplyFilled: Boolean,
    undoEnabled: Boolean,
    onReapply: () -> Unit,
    onCustomize: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reapply = stringResource(R.string.reborn_now_reapply)
    val customize = stringResource(R.string.reborn_now_customize)
    val undo = stringResource(R.string.reborn_now_undo)
    val sources = remember { List(3) { MutableInteractionSource() } }
    ButtonGroup(
        overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        actionItem(sources[0], 1f, reapply, R.drawable.ic_reborn_refresh, reapplyEnabled, reapplyFilled, HapticFeedbackType.Confirm, onReapply)
        actionItem(sources[1], 1.1f, customize, R.drawable.ic_reborn_tune, true, false, HapticFeedbackType.ContextClick, onCustomize)
        actionItem(sources[2], 0.8f, undo, R.drawable.ic_reborn_undo, undoEnabled, false, HapticFeedbackType.ContextClick, onUndo)
    }
}

private fun ButtonGroupScope.actionItem(
    interaction: MutableInteractionSource,
    weight: Float,
    label: String,
    @DrawableRes icon: Int,
    enabled: Boolean,
    filled: Boolean,
    haptic: HapticFeedbackType,
    onClick: () -> Unit,
) {
    customItem(
        buttonGroupContent = {
            val haptics = LocalHapticFeedback.current
            val height = ButtonDefaults.MediumContainerHeight
            Button(
                onClick = {
                    haptics.performHapticFeedback(haptic)
                    onClick()
                },
                enabled = enabled,
                shapes = ButtonDefaults.shapesFor(height),
                colors = if (filled) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
                contentPadding = PaddingValues(horizontal = 12.dp),
                interactionSource = interaction,
                modifier = Modifier
                    .weight(weight)
                    .heightIn(min = height)
                    .animateWidth(interaction),
            ) {
                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)))
                Spacer(Modifier.width(8.dp))
                Text(
                    label,
                    style = ButtonDefaults.textStyleFor(height).copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        menuContent = { state ->
            DropdownMenuItem(
                text = { Text(label) },
                onClick = {
                    state.dismiss()
                    onClick()
                },
                enabled = enabled,
                leadingIcon = { Icon(painterResource(icon), contentDescription = null) },
            )
        },
    )
}

/** The accent's short name for the chip ("Monet", "One UI blue", "Sprite teal", "White", "Custom"). */
private fun accentName(accent: String?): Int? = when {
    accent == null || accent == AmoledPresets.ACCENT_NONE -> null
    accent == AmoledPresets.ACCENT_MONET -> R.string.reborn_now_chip_monet
    accent == AmoledPresets.ACCENT_ONEUI -> R.string.amoled_accent_oneui
    accent == AmoledPresets.ACCENT_SPRITE -> R.string.amoled_accent_sprite
    accent == AmoledPresets.ACCENT_WHITE -> R.string.reborn_now_chip_white
    AmoledPresets.isCustom(accent) -> R.string.reborn_now_chip_custom
    else -> null
}
