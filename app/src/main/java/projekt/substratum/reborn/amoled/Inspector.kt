/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.amoled

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import projekt.substratum.R
import projekt.substratum.common.platform.AmoledChange
import projekt.substratum.common.platform.AmoledLevel
import projekt.substratum.common.platform.AmoledPalette
import projekt.substratum.reborn.components.AppIcon
import projekt.substratum.reborn.components.ShapedIcon
import projekt.substratum.reborn.components.design.MenuAction
import projekt.substratum.reborn.components.design.MenuGroup
import projekt.substratum.reborn.components.design.PageLoading
import projekt.substratum.reborn.components.design.RebornMenu
import projekt.substratum.reborn.components.rememberRoot
import projekt.substratum.reborn.theme.LocalRebornColors
import projekt.substratum.reborn.theme.StatusColors
import java.util.Locale

private fun hex(color: Int): String =
    if (color ushr 24 == 0xff) String.format(Locale.ROOT, "#%06X", color and 0xffffff)
    else String.format(Locale.ROOT, "#%08X", color)

/**
 * One line of the inspector: a change the engine made, or a colour the user keeps stock.
 *
 * @param shared How many colours of the app carry this name: more than one when the app's
 *               resource names are obfuscated (Play Store and other Google apps), and then a keep
 *               or pin, which goes by name, would change every one of them
 */
private class Entry(
    val name: String,
    val change: AmoledChange?,
    val kept: Boolean,
    val pinned: Int?,
    val shared: Int = 1,
) {
    val userRule: Boolean get() = kept || pinned != null

    /** Unique in the list: obfuscated apps repeat one name for many colours, ids never repeat */
    val key: String = change?.let { "id:" + it.id } ?: "kept:$name"
}

/**
 * Every colour AMOLED changed in one app (its change record), stock to AMOLED with the rule that
 * decided, searchable. A long-press keeps a colour stock, pins one or resets it, and reads the app
 * again.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Inspector(row: StudioState.AppRow, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalRebornColors.current
    val scheme = MaterialTheme.colorScheme
    val records by StudioState.records.collectAsStateWithLifecycle()
    val data by rememberRoot(row.pkg, records) { StudioState.readInspector(context, row.pkg) }
    val sheet = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val host = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var pinning by remember { mutableStateOf<Entry?>(null) }
    val say: (String) -> Unit = { message ->
        scope.launch {
            host.currentSnackbarData?.dismiss()
            host.showSnackbar(message)
        }
    }

    val entries = remember(data) {
        val d = data ?: return@remember emptyList<Entry>()
        val changes = d.record?.changes.orEmpty()
        val shared = changes.groupingBy { it.name }.eachCount()
        // Kept colours are not in the record (nothing is written for them): listed first
        (d.kept.filter { it !in shared }.map { Entry(it, null, true, null) } +
            changes.map { Entry(it.name, it, it.name in d.kept, d.pinned[it.name], shared[it.name] ?: 1) })
            .distinctBy { it.key }
    }
    val visible = remember(entries, query) {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) entries
        else entries.filter {
            it.name.lowercase(Locale.ROOT).contains(q) || it.change?.rule?.lowercase(Locale.ROOT)?.contains(q) == true ||
                it.change?.let { c -> hex(c.amoled).lowercase(Locale.ROOT).contains(q) } == true
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = if (colors.dark) scheme.surfaceContainerLow else scheme.surfaceContainerLow,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f),
        ) {
            Column(Modifier.fillMaxWidth()) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 24.dp),
                ) {
                    AppIcon(row.pkg, size = 52.dp, shape = RoundedCornerShape(14.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            row.label,
                            style = MaterialTheme.typography.titleLargeEmphasized,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            row.pkg,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 24.dp),
                ) {
                    RouteChip(row.route)
                    val level = AmoledLevel.parse(data?.record?.level ?: row.level, null)
                    if (level != null) {
                        MiniChip(
                            stringResource(R.string.reborn_studio_level, stringResource(levelLabel(level))),
                            R.drawable.ic_reborn_amoled,
                            colors.unknown,
                        )
                    }
                    val count = data?.record?.changes?.size ?: row.colors
                    if (count > 0) {
                        MiniChip(
                            pluralStringResource(R.plurals.reborn_studio_colours, count, count),
                            R.drawable.ic_reborn_palette,
                            colors.unknown,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    placeholder = { Text(stringResource(R.string.reborn_studio_inspector_search)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_reborn_search), contentDescription = null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = { query = "" }) {
                                Icon(painterResource(R.drawable.ic_reborn_close), stringResource(R.string.reborn_studio_clear_search))
                            }
                        }
                    } else null,
                    singleLine = true,
                    shape = CircleShape,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = scheme.surfaceContainerHigh,
                        unfocusedContainerColor = scheme.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                val d = data
                when {
                    d == null -> PageLoading(height = 220.dp)
                    d.record == null && entries.isEmpty() -> EmptyRecord()
                    else -> {
                        Text(
                            stringResource(R.string.reborn_studio_inspector_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                        )
                        if (visible.isEmpty()) {
                            Text(
                                stringResource(R.string.reborn_studio_changes_none),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                        LazyColumn(
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            items(visible, key = { it.key }, contentType = { "change" }) { entry ->
                                ChangeRow(
                                    entry = entry,
                                    onKeep = {
                                        StudioState.setUserRule(row, entry.name, keep = true, pin = null, onDone = say)
                                    },
                                    onPin = { pinning = entry },
                                    onReset = {
                                        StudioState.setUserRule(row, entry.name, keep = false, pin = null, onDone = say)
                                    },
                                )
                            }
                        }
                    }
                }
            }
            SnackbarHost(
                host,
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            ) { Snackbar(it, shape = MaterialTheme.shapes.large) }
        }
    }

    pinning?.let { entry ->
        PinDialog(
            name = entry.name,
            initial = entry.pinned ?: entry.change?.amoled ?: 0xff000000.toInt(),
            onDismiss = { pinning = null },
            onPin = { color -> StudioState.setUserRule(row, entry.name, keep = false, pin = color, onDone = say) },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmptyRecord() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
    ) {
        ShapedIcon(
            icon = R.drawable.ic_reborn_palette,
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
            size = 72.dp,
            shape = MaterialShapes.Flower.toShape(),
        )
        Spacer(Modifier.height(18.dp))
        Text(
            stringResource(R.string.reborn_studio_inspector_empty),
            style = MaterialTheme.typography.titleLargeEmphasized,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.reborn_studio_inspector_empty_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChangeRow(entry: Entry, onKeep: () -> Unit, onPin: () -> Unit, onReset: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    val change = entry.change
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .combinedClickable(
                    onClick = { menu = true },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menu = true
                    },
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                RuleChip(
                    when {
                        entry.kept -> AmoledPalette.USER_KEEP
                        entry.pinned != null -> AmoledPalette.USER_PIN
                        else -> change?.rule.orEmpty()
                    }
                )
            }
            Spacer(Modifier.width(10.dp))
            if (change != null) {
                Swatch(change.stockDark)
                Icon(
                    painterResource(R.drawable.ic_reborn_arrow),
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .size(16.dp),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp)) {
                    Swatch(change.amoled)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        hex(change.amoled),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            } else {
                Text(
                    stringResource(R.string.reborn_studio_stock),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
        RebornMenu(expanded = menu, onDismiss = { menu = false }) {
            // A keep or pin goes by name: with an obfuscated name it would change every colour
            // that shares it, so only a rule set earlier can be reset. The group label says so.
            val ownName = entry.shared <= 1
            listOf(
                MenuGroup(
                    label = if (ownName) null
                    else pluralStringResource(R.plurals.reborn_studio_shared_name, entry.shared, entry.shared),
                    entries = listOf(
                        MenuAction(
                            stringResource(R.string.reborn_studio_keep_stock),
                            R.drawable.ic_reborn_undo,
                            enabled = !entry.kept && ownName,
                            onClick = onKeep,
                        ),
                        MenuAction(
                            stringResource(R.string.reborn_studio_pin),
                            R.drawable.ic_reborn_pin,
                            enabled = ownName,
                            onClick = onPin,
                        ),
                        MenuAction(
                            stringResource(R.string.reborn_studio_reset),
                            R.drawable.ic_reborn_refresh,
                            enabled = entry.userRule,
                            onClick = onReset,
                        ),
                    ),
                ),
            )
        }
    }
}

@Composable
private fun RuleChip(rule: String) {
    val scheme = MaterialTheme.colorScheme
    val colors = LocalRebornColors.current
    val (label, status) = when {
        rule == AmoledPalette.USER_KEEP -> stringResource(R.string.reborn_studio_rule_kept) to
            StatusColors(scheme.tertiary, scheme.onTertiary, scheme.tertiaryContainer, scheme.onTertiaryContainer)
        rule == AmoledPalette.USER_PIN -> stringResource(R.string.reborn_studio_rule_pinned) to
            StatusColors(scheme.tertiary, scheme.onTertiary, scheme.tertiaryContainer, scheme.onTertiaryContainer)
        rule == AmoledPalette.ACCENT -> rule to
            StatusColors(scheme.primary, scheme.onPrimary, scheme.primaryContainer, scheme.onPrimaryContainer)
        rule == AmoledPalette.EXPLICIT -> rule to
            StatusColors(scheme.secondary, scheme.onSecondary, scheme.secondaryContainer, scheme.onSecondaryContainer)
        rule.startsWith(AmoledPalette.PATTERN_PREFIX) -> stringResource(R.string.reborn_studio_rule_pattern) to
            StatusColors(scheme.secondary, scheme.onSecondary, scheme.secondaryContainer, scheme.onSecondaryContainer)
        else -> rule.ifEmpty { "?" } to colors.unknown
    }
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = status.onContainer,
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(status.container)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** A colour on a small checkerboard, so translucent and black colours read; null is a state list. */
@Composable
private fun Swatch(color: Int?, size: Dp = 30.dp) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    val shape = RoundedCornerShape(9.dp)
    Canvas(
        Modifier
            .size(size)
            .clip(shape)
            .border(1.dp, outline, shape),
    ) {
        val translucent = color == null || color ushr 24 != 0xff
        if (translucent) {
            val cell = 5.dp.toPx()
            val columns = (this.size.width / cell).toInt() + 1
            val rows = (this.size.height / cell).toInt() + 1
            for (x in 0 until columns) {
                for (y in 0 until rows) {
                    drawRect(
                        if ((x + y) % 2 == 0) Color(0xFF3A3A3A) else Color(0xFF242424),
                        topLeft = Offset(x * cell, y * cell),
                        size = Size(cell, cell),
                    )
                }
            }
        }
        if (color != null) {
            drawRect(Color(color))
        } else {
            // A state list: its colours depend on the state, AMOLED names one for it
            val stripe = 4.dp.toPx()
            var x = -this.size.height
            while (x < this.size.width) {
                drawLine(Color(0x99FFFFFF), Offset(x, this.size.height), Offset(x + this.size.height, 0f), strokeWidth = stripe / 2)
                x += stripe * 2
            }
        }
    }
}

@Composable
private fun PinDialog(name: String, initial: Int, onDismiss: () -> Unit, onPin: (Int) -> Unit) {
    var text by rememberSaveable { mutableStateOf(hex(initial)) }
    val parsed = remember(text) {
        val t = text.trim().let { if (it.startsWith("#")) it else "#$it" }
        if (t.length != 7 && t.length != 9) null
        else try {
            AmoledPalette.parseColor(t)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_reborn_pin), contentDescription = null) },
        title = { Text(stringResource(R.string.reborn_studio_pin_title)) },
        text = {
            Column {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Swatch(parsed ?: initial, 48.dp)
                    Spacer(Modifier.width(14.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.trim().take(9) },
                        label = { Text(stringResource(R.string.reborn_studio_pin_hint)) },
                        isError = parsed == null,
                        supportingText = if (parsed == null) {
                            { Text(stringResource(R.string.reborn_studio_invalid_colour)) }
                        } else null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = parsed != null,
                onClick = {
                    parsed?.let(onPin)
                    onDismiss()
                },
                shapes = ButtonDefaults.shapes(),
            ) { Text(stringResource(R.string.reborn_studio_pin_title)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.reborn_cancel)) }
        },
    )
}
