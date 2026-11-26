/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import projekt.substratum.common.platform.RebornPlatform

/** One UI (Samsung) rather than stock Android: cached, safe to read while composing. */
fun isOneUi(): Boolean = RebornPlatform.isOneUi()

/** "stock One UI" or "stock Android": the look the stock side of a preview shows. */
@Composable
fun stockLookName(): String = RebornPlatform.stockLook(LocalContext.current)

/** "One UI blue" or "the system accent": the accent apps keep when the look sets none. */
@Composable
fun stockAccentName(): String = RebornPlatform.stockAccent(LocalContext.current)
