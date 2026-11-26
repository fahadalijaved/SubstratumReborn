/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.components.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import projekt.substratum.reborn.theme.LocalRebornColors

/** The label above a segmented group: titleSmallEmphasized in primary, sentence case. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.sectionLabel,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .padding(start = 4.dp, top = 8.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

/**
 * Segment colours that read on every surface: the card colour (#121212 on true black, where the
 * default `surface` would vanish), secondaryContainer when selected.
 */
@Composable
fun rebornSegmentColors(): ListItemColors {
    val scheme = MaterialTheme.colorScheme
    return ListItemDefaults.segmentedColors(
        containerColor = LocalRebornColors.current.card,
        selectedContainerColor = scheme.secondaryContainer,
        selectedContentColor = scheme.onSecondaryContainer,
        draggedContainerColor = scheme.surfaceContainerHighest,
    )
}

/**
 * A short segmented group (settings rows, links, sheet actions) as ONE lazy item or plain
 * content: rows 2 dp apart, the first and last with the large outer corners.
 * Use SegmentedListItem(onClick | selected | checked, shapes = shapes, colors = rebornSegmentColors(), ...).
 */
@Composable
fun SegmentedGroup(
    count: Int,
    modifier: Modifier = Modifier,
    row: @Composable (index: Int, shapes: ListItemShapes) -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        for (i in 0 until count) row(i, ListItemDefaults.segmentedShapes(i, count))
    }
}

/**
 * A long segmented list, one lazy item per row. The LazyColumn must not use
 * Arrangement.spacedBy (it would separate the segments); the 2 dp gap comes in [row]'s modifier,
 * which also carries Modifier.animateItem(). Put it on the row's SegmentedListItem.
 */
fun <T> LazyListScope.segmentedItems(
    items: List<T>,
    key: ((T) -> Any)? = null,
    row: @Composable (item: T, shapes: ListItemShapes, modifier: Modifier) -> Unit,
) {
    itemsIndexed(items, key = key?.let { k -> { _: Int, item: T -> k(item) } }) { i, item ->
        val gap = if (i == 0) 0.dp else ListItemDefaults.SegmentedGap
        row(item, ListItemDefaults.segmentedShapes(i, items.size), Modifier.animateItem().padding(top = gap))
    }
}
