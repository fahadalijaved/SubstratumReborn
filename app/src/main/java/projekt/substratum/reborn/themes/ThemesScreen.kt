/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.themes

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import projekt.substratum.R
import projekt.substratum.common.Broadcasts
import projekt.substratum.common.References
import projekt.substratum.common.Theming
import projekt.substratum.reborn.RebornActions
import projekt.substratum.reborn.RebornCache
import projekt.substratum.reborn.amoled.rememberActionSnackbar
import projekt.substratum.reborn.components.AppIcon
import projekt.substratum.reborn.components.Entrance
import projekt.substratum.reborn.components.HeroShape
import projekt.substratum.reborn.components.MorphingShape
import projekt.substratum.reborn.components.SectionCard
import projekt.substratum.reborn.components.SectionHeader
import projekt.substratum.reborn.components.ShapedIcon
import projekt.substratum.reborn.components.TagChip
import projekt.substratum.reborn.components.design.ActionIcon
import projekt.substratum.reborn.components.design.BusyLabel
import projekt.substratum.reborn.components.design.FabMenuItem
import projekt.substratum.reborn.components.design.HeroCard
import projekt.substratum.reborn.components.design.MenuAction
import projekt.substratum.reborn.components.design.MenuGroup
import projekt.substratum.reborn.components.design.PageLoading
import projekt.substratum.reborn.components.design.RebornFabMenu
import projekt.substratum.reborn.components.design.RebornOverflow
import projekt.substratum.reborn.components.design.RebornPullToRefresh
import projekt.substratum.reborn.components.design.SegmentedGroup
import projekt.substratum.reborn.components.design.ThemeCard
import projekt.substratum.reborn.components.design.rebornSegmentColors
import projekt.substratum.reborn.components.design.rebornTopBarColors
import projekt.substratum.reborn.components.design.rememberRefreshableRoot
import projekt.substratum.reborn.components.onIo
import projekt.substratum.reborn.components.rememberResumeCount
import projekt.substratum.util.helpers.Root
import projekt.substratum.util.helpers.StarterTheme

private const val APK_MIME = "application/vnd.android.package-archive"

private enum class Installing { Starter, File }

/**
 * Installed themes as full-width hero cards (search in the top bar, a long-press sheet with app
 * info, shortcut and uninstall), and Discover: the bundled Reborn AMOLED and installing a theme
 * APK from a file. A tap opens the theme the way Classic does: overlay themes in
 * InformationActivity, rules themes in AMOLED Studio ([onOpenStudio]).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ThemesScreen(onOpenStudio: (String) -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val resume = rememberResumeCount()
    var refresh by remember { mutableIntStateOf(0) }
    val themes = rememberRefreshableRoot(resume, refresh, initial = RebornCache.themes) {
        loadThemes(context).also { RebornCache.themes = it }
    }
    val snapshot = themes.value
    val snackbar = rememberActionSnackbar()
    val scope = rememberCoroutineScope()
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var sheetFor by rememberSaveable { mutableStateOf<String?>(null) }
    var installing by remember { mutableStateOf<Installing?>(null) }
    val list = rememberLazyListState()
    // The Add FAB shows while the list is at its top, like Google's apps
    val fabVisible by remember { derivedStateOf { list.firstVisibleItemIndex == 0 } }
    BackHandler(enabled = searching) {
        searching = false
        query = ""
    }

    // The entrance plays once; coming back to the tab shows the cards at once
    var entered by rememberSaveable { mutableStateOf(false) }
    val animate = remember { !entered }
    LaunchedEffect(Unit) { entered = true }

    fun open(theme: ThemeItem) {
        try {
            // Which kind it is was read with the list: no package lookup here
            if (theme.rules) onOpenStudio(theme.pkg)
            else Theming.launchOverlayTheme(activity ?: context, theme.pkg)
        } catch (e: RuntimeException) {
            snackbar(context.getString(R.string.reborn_theme_failed), null, null)
        }
    }

    fun openInstalled(pkg: String, rules: Boolean) {
        try {
            if (rules) onOpenStudio(pkg) else Theming.launchOverlayTheme(activity ?: context, pkg)
        } catch (e: RuntimeException) {
            snackbar(context.getString(R.string.reborn_theme_failed), null, null)
        }
    }

    fun installStarter() {
        if (installing != null) return
        scope.launch {
            installing = Installing.Starter
            val result = try {
                onIo { if (!Root.checkRootAccess()) null else StarterTheme.installWithRoot(context) }
            } finally {
                installing = null
            }
            when (result) {
                // Without root the system installer takes the bundled APK
                null -> StarterTheme.install(context)
                true -> {
                    Broadcasts.sendRefreshMessage(context.applicationContext)
                    refresh++
                    snackbar(
                        context.getString(R.string.reborn_install_done, context.getString(R.string.reborn_discover_starter_title)),
                        context.getString(R.string.reborn_open),
                    ) { onOpenStudio(RebornActions.AMOLED_THEME) }
                }
                false -> snackbar(context.getString(R.string.install_starter_theme_failed), null, null)
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null || installing != null) return@rememberLauncherForActivityResult
        scope.launch {
            installing = Installing.File
            val result = try {
                onIo { installThemeFile(context, uri) }
            } finally {
                installing = null
            }
            when (result) {
                is InstallResult.Installed -> {
                    Broadcasts.sendRefreshMessage(context.applicationContext)
                    refresh++
                    snackbar(context.getString(R.string.reborn_install_done, result.name), context.getString(R.string.reborn_open)) {
                        openInstalled(result.pkg, result.rules)
                    }
                }
                InstallResult.NotATheme -> snackbar(context.getString(R.string.reborn_install_not_theme), null, null)
                InstallResult.NoRoot -> snackbar(context.getString(R.string.reborn_needs_root), null, null)
                is InstallResult.Failed -> snackbar(
                    result.reason?.let { context.getString(R.string.reborn_install_failed, it) }
                        ?: context.getString(R.string.reborn_install_failed_plain),
                    null, null,
                )
            }
        }
    }
    fun pickFile() {
        if (installing != null) return
        try {
            picker.launch(arrayOf(APK_MIME))
        } catch (e: Exception) {
            Log.w("RebornThemes", "no document picker", e)
            snackbar(context.getString(R.string.reborn_install_failed_plain), null, null)
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val background = MaterialTheme.colorScheme.background
    val barColors = rebornTopBarColors()
    val motion = MaterialTheme.motionScheme
    // The bar's nested scroll is the pull to refresh's (below), so a pull expands the bar first
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = background,
        floatingActionButton = {
            val starterMissing = snapshot?.starterInstalled == false
            RebornFabMenu(
                items = buildList {
                    add(FabMenuItem(stringResource(R.string.reborn_install_file), R.drawable.ic_reborn_file, ::pickFile))
                    if (starterMissing) {
                        add(FabMenuItem(stringResource(R.string.reborn_discover_starter_title), R.drawable.ic_reborn_download, ::installStarter))
                    }
                },
                icon = R.drawable.ic_reborn_create,
                contentDescription = stringResource(R.string.reborn_add_theme),
                visible = fabVisible && !searching && installing == null,
            )
        },
        topBar = {
            AnimatedContent(
                targetState = searching,
                transitionSpec = { fadeIn(motion.fastEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
                label = "themesBar",
            ) { search ->
                if (search) {
                    SearchBar(
                        query = query,
                        onQuery = { query = it },
                        onClose = {
                            searching = false
                            query = ""
                        },
                        colors = barColors,
                    )
                } else {
                    LargeFlexibleTopAppBar(
                        title = { Text(stringResource(R.string.reborn_tab_themes)) },
                        subtitle = {
                            snapshot?.let { Text(pluralThemes(it.themes.size)) }
                        },
                        actions = {
                            ActionIcon(R.drawable.ic_reborn_search, stringResource(R.string.reborn_themes_search), { searching = true })
                            RebornOverflow {
                                listOf(
                                    MenuGroup(
                                        listOf(
                                            MenuAction(
                                                stringResource(R.string.reborn_install_file),
                                                R.drawable.ic_reborn_file,
                                                enabled = installing == null,
                                                onClick = ::pickFile,
                                            ),
                                        ),
                                    ),
                                    MenuGroup(
                                        listOf(
                                            MenuAction(
                                                stringResource(R.string.reborn_classic),
                                                R.drawable.ic_reborn_open,
                                                supporting = stringResource(R.string.reborn_classic_supporting),
                                                onClick = { RebornActions.openClassic(context) },
                                            ),
                                        ),
                                    ),
                                )
                            }
                        },
                        colors = barColors,
                        scrollBehavior = scrollBehavior,
                    )
                }
            }
        },
    ) { padding ->
        val data = snapshot
        val q = query.trim().lowercase()
        val visible = remember(data, q) {
            data?.themes.orEmpty().filter { q.isEmpty() || it.search.contains(q) }
        }
        RebornPullToRefresh(
            refreshing = themes.refreshing,
            onRefresh = themes.refresh,
            modifier = Modifier.padding(top = padding.calculateTopPadding()),
            scrollConnection = scrollBehavior.nestedScrollConnection,
        ) {
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    // Room for the Add FAB over the last card
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when {
                    data == null -> item(key = "loading") { PageLoading(height = 320.dp) }
                    data.themes.isEmpty() -> item(key = "empty") {
                        Entrance(0, animate) {
                            EmptyThemes(
                                starterInstalled = data.starterInstalled,
                                installing = installing,
                                onStarter = ::installStarter,
                                onFile = ::pickFile,
                                onClassic = { RebornActions.openClassic(context) },
                            )
                        }
                    }
                    visible.isEmpty() -> item(key = "no_match") {
                        NoMatch(query.trim())
                    }
                    else -> items(visible, key = { it.pkg }, contentType = { "theme" }) { theme ->
                        val index = data.themes.indexOf(theme)
                        Entrance(index.coerceAtMost(4), animate) {
                            InstalledThemeCard(
                                theme = theme,
                                onOpen = { open(theme) },
                                onLongPress = { sheetFor = theme.pkg },
                            )
                        }
                    }
                }
                if (data != null && !searching && data.themes.isNotEmpty()) {
                    item(key = "discover_header") {
                        SectionHeader(stringResource(R.string.reborn_discover))
                    }
                    if (!data.starterInstalled) {
                        item(key = "discover_starter") {
                            DiscoverCard(
                                icon = R.drawable.ic_reborn_amoled,
                                shape = MaterialShapes.Clover4Leaf.toShape(),
                                container = MaterialTheme.colorScheme.primaryContainer,
                                content = MaterialTheme.colorScheme.onPrimaryContainer,
                                title = stringResource(R.string.reborn_discover_starter_title),
                                text = stringResource(R.string.reborn_discover_starter_text),
                                action = stringResource(R.string.reborn_discover_install),
                                actionIcon = R.drawable.ic_reborn_download,
                                busy = installing == Installing.Starter,
                                enabled = installing == null,
                                onClick = ::installStarter,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                    item(key = "discover_file") {
                        DiscoverCard(
                            icon = R.drawable.ic_reborn_file,
                            shape = MaterialShapes.Cookie6Sided.toShape(),
                            container = MaterialTheme.colorScheme.tertiaryContainer,
                            content = MaterialTheme.colorScheme.onTertiaryContainer,
                            title = stringResource(R.string.reborn_discover_file_title),
                            text = stringResource(R.string.reborn_discover_file_text),
                            action = stringResource(R.string.reborn_discover_file_action),
                            actionIcon = R.drawable.ic_reborn_file,
                            busy = installing == Installing.File,
                            enabled = installing == null,
                            onClick = ::pickFile,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }

    val sheetTheme = sheetFor?.let { pkg -> snapshot?.themes?.firstOrNull { it.pkg == pkg } }
    if (sheetTheme != null) {
        ThemeSheet(
            theme = sheetTheme,
            onDismiss = { sheetFor = null },
            onOpen = {
                sheetFor = null
                open(sheetTheme)
            },
            onAppInfo = {
                sheetFor = null
                start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", sheetTheme.pkg, null)))
            },
            onShortcut = {
                sheetFor = null
                // The label is the shortcut's id too: namesakes get their version, or their
                // package when the versions match, so one does not replace the other's shortcut
                val label = if (sheetTheme.namesake) {
                    val others = snapshot?.themes.orEmpty().filter {
                        it.pkg != sheetTheme.pkg && it.name.equals(sheetTheme.name, ignoreCase = true)
                    }
                    val suffix = sheetTheme.version?.takeIf { v -> others.none { it.version == v } } ?: sheetTheme.pkg
                    context.getString(R.string.reborn_theme_shortcut_label, sheetTheme.name, suffix)
                } else sheetTheme.name
                scope.launch {
                    val done = runCatching { onIo { References.createLauncherIcon(context, sheetTheme.pkg, label) } }.isSuccess
                    snackbar(
                        context.getString(if (done) R.string.reborn_theme_shortcut_done else R.string.reborn_failed),
                        null, null,
                    )
                }
            },
            onUninstall = {
                sheetFor = null
                // The list is read again when the system's dialog returns here
                start(context, Intent(Intent.ACTION_DELETE, Uri.parse("package:" + sheetTheme.pkg)))
            },
        )
    }
}

private fun start(context: android.content.Context, intent: Intent) {
    try {
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.w("RebornThemes", "cannot start $intent", e)
    }
}

@Composable
private fun pluralThemes(count: Int): String {
    val context = LocalContext.current
    return context.resources.getQuantityString(R.plurals.reborn_themes_count, count, count)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    onQuery: (String) -> Unit,
    onClose: () -> Unit,
    colors: androidx.compose.material3.TopAppBarColors,
) {
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    TopAppBar(
        title = {
            TextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 12.dp)
                    .focusRequester(focus),
                placeholder = { Text(stringResource(R.string.reborn_themes_search)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_reborn_search), contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { onQuery("") }) {
                            Icon(painterResource(R.drawable.ic_reborn_close), stringResource(R.string.reborn_studio_clear_search))
                        }
                    }
                } else null,
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.ic_reborn_back), stringResource(R.string.reborn_back))
            }
        },
        colors = colors,
    )
}

/**
 * A full-width 16:9 card: the hero with a scrim, name, author and version, then the theme's
 * chips. The expressive [ThemeCard] (press morph, scale) with this theme's art and icon.
 */
@Composable
private fun InstalledThemeCard(theme: ThemeItem, onOpen: () -> Unit, onLongPress: () -> Unit, modifier: Modifier = Modifier) {
    ThemeCard(
        name = theme.name,
        byline = byline(theme),
        onClick = onOpen,
        onLongClick = onLongPress,
        modifier = modifier,
        // Same name as another installed theme: told apart by package and version
        supporting = if (theme.namesake) {
            stringResource(R.string.reborn_theme_package_version, theme.pkg, theme.version ?: "–")
        } else null,
        avatar = { AppIcon(theme.pkg, size = 48.dp, shape = RoundedCornerShape(14.dp)) },
        chips = { ThemeChips(theme) },
    ) {
        HeroArt(theme, Modifier.fillMaxSize())
    }
}

@Composable
private fun byline(theme: ThemeItem): String =
    if (theme.version.isNullOrBlank()) stringResource(R.string.reborn_theme_by, theme.author)
    else stringResource(R.string.reborn_theme_by_version, theme.author, theme.version)

@Composable
private fun ThemeChips(theme: ThemeItem) {
    val scheme = MaterialTheme.colorScheme
    if (theme.rules) {
        TagChip(stringResource(R.string.reborn_theme_chip_rules), R.drawable.ic_reborn_amoled, scheme.primaryContainer, scheme.onPrimaryContainer)
    }
    if (theme.overlays) {
        TagChip(stringResource(R.string.reborn_theme_chip_overlays), R.drawable.ic_reborn_layers, scheme.secondaryContainer, scheme.onSecondaryContainer)
    }
    if (theme.wallpapers) {
        TagChip(stringResource(R.string.reborn_theme_chip_wallpapers), R.drawable.ic_reborn_wallpaper, scheme.tertiaryContainer, scheme.onTertiaryContainer)
    }
}

/** The theme's hero image, or a drawn one in its icon's colours, faded in once read. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeroArt(theme: ThemeItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth.coerceIn(1, 1440)
        val hero by produceState(cachedHero(theme, width), theme.pkg, theme.updated, width) {
            // Also after an update of the theme or another width: the old hero stays meanwhile
            value = cachedHero(theme, width) ?: withContext(heroLoader) { loadHero(context, theme, width) }
        }
        Crossfade(targetState = hero, animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(), label = "hero") { h ->
            when {
                h == null -> Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
                h.image != null -> Image(
                    bitmap = h.image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> DrawnHero(Color(h.light), Color(h.deep))
            }
        }
    }
}

/** For themes without a hero image: their icon's colours over expressive shapes. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DrawnHero(light: Color, deep: Color) {
    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Brush.linearGradient(listOf(deep, Color.Black))),
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 56.dp, y = (-48).dp)
                .size(240.dp)
                .rotate(18f)
                .clip(MaterialShapes.Cookie9Sided.toShape())
                .background(light.copy(alpha = 0.55f)),
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-36).dp, y = (-20).dp)
                .size(150.dp)
                .rotate(-12f)
                .clip(MaterialShapes.Clover4Leaf.toShape())
                .background(light.copy(alpha = 0.22f)),
        )
        Box(
            Modifier
                .align(Alignment.Center)
                .offset(x = 40.dp, y = 6.dp)
                .size(72.dp)
                .clip(MaterialShapes.Sunny.toShape())
                .background(light.copy(alpha = 0.35f)),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DiscoverCard(
    @DrawableRes icon: Int,
    shape: androidx.compose.ui.graphics.Shape,
    container: Color,
    content: Color,
    title: String,
    text: String,
    action: String,
    @DrawableRes actionIcon: Int,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val installingLabel = stringResource(R.string.reborn_installing)
    SectionCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShapedIcon(icon, container, content, size = 56.dp, shape = shape, busy = busy)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
                Spacer(Modifier.height(2.dp))
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        val scheme = MaterialTheme.colorScheme
        FilledTonalButton(
            onClick = onClick,
            enabled = enabled,
            shapes = ButtonDefaults.shapes(),
            // Busy keeps the tonal colours: the in-button indicator says it is installing
            colors = if (busy) ButtonDefaults.filledTonalButtonColors(
                disabledContainerColor = scheme.secondaryContainer,
                disabledContentColor = scheme.onSecondaryContainer,
            ) else ButtonDefaults.filledTonalButtonColors(),
            modifier = Modifier
                .align(Alignment.End)
                .semantics { if (busy) stateDescription = installingLabel },
        ) {
            BusyLabel(busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(actionIcon), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(action)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmptyThemes(
    starterInstalled: Boolean,
    installing: Installing?,
    onStarter: () -> Unit,
    onFile: () -> Unit,
    onClassic: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    HeroCard {
        Box(
            Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center,
        ) {
            MorphingShape(HeroShape.Calm, scheme.primary, Modifier.size(184.dp))
            if (installing != null) {
                LoadingIndicator(Modifier.size(72.dp), color = scheme.onPrimary)
            } else {
                Icon(
                    painterResource(R.drawable.ic_reborn_themes),
                    contentDescription = null,
                    tint = scheme.onPrimary,
                    modifier = Modifier.size(64.dp),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.reborn_themes_empty),
            style = MaterialTheme.typography.headlineMediumEmphasized,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.reborn_themes_empty_text),
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!starterInstalled) {
                val busy = installing == Installing.Starter
                Button(
                    onClick = onStarter,
                    enabled = installing == null,
                    shapes = ButtonDefaults.shapes(),
                    colors = if (busy) ButtonDefaults.buttonColors(
                        disabledContainerColor = scheme.primary,
                        disabledContentColor = scheme.onPrimary,
                    ) else ButtonDefaults.buttonColors(),
                ) {
                    BusyLabel(busy) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_reborn_download), null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.reborn_discover_starter_title))
                        }
                    }
                }
            } else {
                Button(onClick = onClassic, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.reborn_classic)) }
            }
            OutlinedButton(onClick = onFile, enabled = installing == null, shapes = ButtonDefaults.shapes()) {
                Icon(painterResource(R.drawable.ic_reborn_file), null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.reborn_discover_file_action))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NoMatch(query: String) {
    SectionCard {
        ShapedIcon(
            icon = R.drawable.ic_reborn_search,
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
            size = 52.dp,
            shape = MaterialShapes.Cookie7Sided.toShape(),
        )
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.reborn_themes_no_match), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.reborn_themes_no_match_text, query),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The long-press sheet: what the theme is, and open, app info, shortcut and uninstall. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ThemeSheet(
    theme: ThemeItem,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onAppInfo: () -> Unit,
    onShortcut: () -> Unit,
    onUninstall: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val sheet = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = scheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 24.dp),
            ) {
                AppIcon(theme.pkg, size = 56.dp, shape = MaterialTheme.shapes.large)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        theme.name,
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        byline(theme),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        theme.pkg,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (theme.rules || theme.overlays || theme.wallpapers) {
                Spacer(Modifier.height(14.dp))
                FlowRow(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) { ThemeChips(theme) }
            }
            Spacer(Modifier.height(16.dp))
            val actions = listOf(
                SheetActionRow(
                    R.drawable.ic_reborn_open,
                    stringResource(R.string.reborn_theme_open),
                    stringResource(if (theme.rules) R.string.reborn_theme_open_rules else R.string.reborn_theme_open_overlays),
                    onOpen,
                ),
                SheetActionRow(
                    R.drawable.ic_reborn_info,
                    stringResource(R.string.reborn_theme_app_info),
                    stringResource(R.string.reborn_theme_app_info_text),
                    onAppInfo,
                ),
                SheetActionRow(
                    R.drawable.ic_reborn_shortcut,
                    stringResource(R.string.reborn_theme_shortcut),
                    stringResource(R.string.reborn_theme_shortcut_text),
                    onShortcut,
                ),
            )
            SegmentedGroup(actions.size, Modifier.padding(horizontal = 16.dp)) { i, shapes ->
                SheetAction(actions[i], shapes, rebornSegmentColors())
            }
            if (theme.removable) {
                // Destructive, so in its own group, in the error colours
                Spacer(Modifier.height(12.dp))
                SegmentedGroup(1, Modifier.padding(horizontal = 16.dp)) { _, shapes ->
                    SheetAction(
                        SheetActionRow(
                            R.drawable.ic_reborn_delete,
                            stringResource(R.string.reborn_theme_uninstall),
                            stringResource(R.string.reborn_theme_uninstall_text),
                            onUninstall,
                        ),
                        shapes,
                        ListItemDefaults.segmentedColors(
                            containerColor = scheme.errorContainer,
                            contentColor = scheme.onErrorContainer,
                            leadingContentColor = scheme.onErrorContainer,
                            supportingContentColor = scheme.onErrorContainer.copy(alpha = 0.8f),
                        ),
                    )
                }
            }
        }
    }
}

private class SheetActionRow(@DrawableRes val icon: Int, val title: String, val text: String, val onClick: () -> Unit)

/** One segmented action row of the theme sheet. */
@Composable
private fun SheetAction(row: SheetActionRow, shapes: ListItemShapes, colors: ListItemColors) {
    val haptics = LocalHapticFeedback.current
    SegmentedListItem(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            row.onClick()
        },
        shapes = shapes,
        colors = colors,
        leadingContent = { Icon(painterResource(row.icon), contentDescription = null) },
        supportingContent = { Text(row.text) },
    ) {
        Text(row.title, style = MaterialTheme.typography.bodyLargeEmphasized)
    }
}
