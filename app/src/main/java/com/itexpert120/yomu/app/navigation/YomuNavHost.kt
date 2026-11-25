package com.itexpert120.yomu.app.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import com.itexpert120.yomu.app.AppViewModel
import com.itexpert120.yomu.app.ExternalOpenViewModel
import com.itexpert120.yomu.core.designsystem.YomuMotion
import com.itexpert120.yomu.core.reader.ReaderOpenTrace
import com.itexpert120.yomu.core.reader.ReaderRenderState
import com.itexpert120.yomu.feature.about.AboutRoute
import com.itexpert120.yomu.feature.bookdetails.BookDetailsRoute
import com.itexpert120.yomu.feature.bookdetails.bookPaneBesideLibrary
import com.itexpert120.yomu.feature.bookdetails.bookPaneWidth
import com.itexpert120.yomu.feature.bookedit.EditBookRoute
import com.itexpert120.yomu.feature.library.LibraryRoute
import com.itexpert120.yomu.feature.reader.FontLibraryRoute
import com.itexpert120.yomu.feature.reader.ReaderDefaultsRoute
import com.itexpert120.yomu.feature.reader.ReaderRoute
import com.itexpert120.yomu.feature.reader.ReaderViewModel
import com.itexpert120.yomu.feature.settings.SettingsRoute
import com.itexpert120.yomu.feature.stats.StatsRoute
import com.itexpert120.yomu.feature.sync.SyncRoute
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun YomuNavHost(
    appViewModel: AppViewModel,
    externalOpenViewModel: ExternalOpenViewModel,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()

    // A Resume/chapter tap can arrive twice while Navigation is moving the current entry out. The
    // lifecycle check makes the first navigation win without queuing duplicate reader destinations.
    fun navigateToReader(destination: Reader, reason: String) {
        val entry = navController.currentBackStackEntry
        if (entry?.lifecycle?.currentState != Lifecycle.State.RESUMED) return
        ReaderOpenTrace.mark("reader.tap.$reason")
        navController.navigate(destination)
    }

    // An EPUB opened from outside the app (file manager / share) is imported off-screen, then we
    // jump straight into the reader for the resolved book (an existing entry on a duplicate).
    val pendingBooks by externalOpenViewModel.pendingBooks.collectAsStateWithLifecycle()
    val navigationEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(pendingBooks, navigationEntry) {
        val bookId = pendingBooks.firstOrNull() ?: return@LaunchedEffect
        val entry = navigationEntry ?: return@LaunchedEffect
        entry.lifecycle.currentStateFlow.first { it == Lifecycle.State.RESUMED }
        if (navController.currentBackStackEntry === entry) {
            navigateToReader(Reader(bookId), "external")
            externalOpenViewModel.acknowledgeOpen(bookId)
        }
    }

    val transitions = rememberYomuScreenTransitions()
    val motionEnabled = transitions.motionEnabled
    val layoutDirection = transitions.layoutDirection

    val backMotion = remember { BackMotion() }
    TrackBackMotion(backMotion, navController)

    // How far a book pane beside the library is open; the library narrows with it.
    val bookPane = remember { BookPaneSpace() }
    val captureScope = rememberCoroutineScope()

    // A book opens over (phone) or beside (pane) the library. With a book pane already open,
    // another book swaps into it in place instead of stacking a second one.
    fun openDetails(bookId: String) {
        val top = navController.currentBackStackEntry ?: return
        if (top.destination.hasRoute<BookDetails>()) {
            if (top.toRoute<BookDetails>().bookId == bookId) return
            navController.navigate(BookDetails(bookId)) { popUpTo<BookDetails> { inclusive = true } }
        } else {
            navController.navigate(BookDetails(bookId))
        }
    }

    // Every in-app back (a back button, the book sheet's close or drag, the library beside a book
    // pane): it pops now, or — while the page is still opening — as soon as it has settled, so a
    // back is never dropped and a dragged-away sheet never comes back. Repeats are ignored.
    val pendingPops = remember { mutableSetOf<String>() }
    fun popWhenSettled(entry: NavBackStackEntry) {
        if (navController.currentBackStackEntry !== entry) return
        if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) {
            navController.popBackStack()
            return
        }
        if (!pendingPops.add(entry.id)) return
        captureScope.launch {
            try {
                val state = entry.lifecycle.currentStateFlow.first { it == Lifecycle.State.RESUMED || it == Lifecycle.State.DESTROYED }
                if (state == Lifecycle.State.RESUMED && navController.currentBackStackEntry === entry) navController.popBackStack()
            } finally {
                pendingPops.remove(entry.id)
            }
        }
    }

    // Resume: opening the reader from a book's phone sheet, or from the library pile, plays Stack's
    // Resume motion (the sheet rises into the page: BookSheetFrame + ReaderEntranceFrame) instead
    // of the push. A book shown as a pane beside the library keeps the push.
    val windowWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val bookIsPhoneSheet = !bookPaneBesideLibrary(windowWidth)
    fun readerEntrance(from: NavDestination?, to: NavDestination): ReaderEntrance? = readerEntrance(
        toReader = to.hasRoute<Reader>(),
        fromLibrary = from?.hasRoute<Home>() == true,
        fromBook = from?.hasRoute<BookDetails>() == true,
        bookIsPhoneSheet = bookIsPhoneSheet,
    )

    // The library (Home: the top-level shell with its tabs, bar or rail) is composed once, under the
    // NavHost, for the life of the app: Home is an empty page that only lets it show through. So the
    // library is never torn down while a book is open, and comes back exactly as it was — no
    // recreation work, no unsettled first frame — and a book pane can sit beside it, live.
    Box(modifier) {
        NavHost(
            navController = navController,
            startDestination = Home,
            modifier = Modifier.fillMaxSize().zIndex(1f),
            enterTransition = {
                // A book opens as a sheet over the library (BookSheetFrame moves it); everything else pushes.
                when {
                    // Another book swapped into an open pane: the content crossfades in place.
                    targetState.destination.hasRoute<BookDetails>() && initialState.destination.hasRoute<BookDetails>() -> {
                        backMotion.sheetEnter = false
                        if (motionEnabled && initialState.id != targetState.id) fadeIn(YomuMotion.scheme.defaultEffectsSpec()) else EnterTransition.None
                    }
                    targetState.destination.hasRoute<BookDetails>() -> {
                        backMotion.sheetEnter = initialState.id != targetState.id
                        EnterTransition.None
                    }
                    // ReaderEntranceFrame draws the Resume motion; with animations off it is a crossfade.
                    initialState.id != targetState.id && readerEntrance(initialState.destination, targetState.destination) != null ->
                        if (motionEnabled) EnterTransition.None else fadeIn(tween(ResumeCrossfadeMillis))
                    else -> transitions.pushEnter(this)
                }
            },
            exitTransition = {
                backMotion.forward(changesEntry = initialState.id != targetState.id)
                when {
                    targetState.destination.hasRoute<BookDetails>() && initialState.destination.hasRoute<BookDetails>() ->
                        if (motionEnabled && initialState.id != targetState.id) fadeOut(YomuMotion.scheme.fastEffectsSpec()) else ExitTransition.None
                    // The library holds still under an opening book; only the sheet moves.
                    targetState.destination.hasRoute<BookDetails>() && initialState.id != targetState.id && motionEnabled ->
                        ExitTransition.KeepUntilTransitionsFinished
                    // Resume: the page underneath stays until the reader has covered it.
                    initialState.id != targetState.id && readerEntrance(initialState.destination, targetState.destination) != null ->
                        ExitTransition.KeepUntilTransitionsFinished
                    else -> transitions.pushExit(this)
                }
            },
            // Every back is Stack's one slide (BackPreviewFrame; the book sheet's in BookSheetFrame):
            // the page underneath holds still, the leaving page is kept until its slide has played.
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { backPopExit(backMotion, motionEnabled, layoutDirection) },
            predictivePopEnterTransition = { EnterTransition.None },
            predictivePopExitTransition = { swipeEdge -> backPredictivePopExit(backMotion, swipeEdge, motionEnabled) },
        ) {
            // The library shows through (LibraryLayer, below).
            composable<Home> { Box(Modifier.fillMaxSize()) }
            composable<BookDetails> { entry ->
                BackPreviewFrame(backMotion, slides = false) {
                    BookSheetFrame(
                        motion = backMotion,
                        pane = bookPane,
                        // Read while the page leaves: the reader it is leaving for is already on top.
                        resuming = {
                            val top = navController.currentBackStackEntry?.destination
                            top != null && readerEntrance(entry.destination, top) == ReaderEntrance.BookSheet
                        },
                    ) { libraryBeside, resume ->
                        val args = entry.toRoute<BookDetails>()
                        BookDetailsRoute(
                            onBack = { popWhenSettled(entry) },
                            onRead = { navigateToReader(Reader(args.bookId), "details-resume") },
                            onEdit = { navController.navigate(EditBook(args.bookId)) },
                            onOpenChapter = { locator ->
                                navigateToReader(Reader(args.bookId, locator), "chapter")
                            },
                            libraryBeside = libraryBeside,
                            resume = resume,
                        )
                    }
                }
            }
            composable<EditBook> { entry ->
                BackPreviewFrame(backMotion) {
                    EditBookRoute(onBack = { popWhenSettled(entry) })
                }
            }
            composable<ReaderDefaults> { entry ->
                BackPreviewFrame(backMotion) {
                    ReaderDefaultsRoute(
                        onBack = { popWhenSettled(entry) },
                        onOpenFontLibrary = { navController.navigate(FontLibrary) },
                    )
                }
            }
            composable<FontLibrary> { entry ->
                BackPreviewFrame(backMotion) {
                    FontLibraryRoute(onBack = { popWhenSettled(entry) })
                }
            }
            composable<About> { entry ->
                BackPreviewFrame(backMotion) {
                    AboutRoute(onBack = { popWhenSettled(entry) })
                }
            }
            composable<Sync> { entry ->
                BackPreviewFrame(backMotion) {
                    SyncRoute(onBack = { popWhenSettled(entry) })
                }
            }
            composable<Reader> { entry ->
                // Where this reader was opened from, taken at its first frame (the page under it).
                val entrance = remember { readerEntrance(navController.previousBackStackEntry?.destination, entry.destination) }
                // The same ViewModel ReaderRoute uses (this entry owns it): its first page is laid out.
                val reader: ReaderViewModel = hiltViewModel()
                val pageReady = remember(reader) {
                    reader.state.map { it.experience.failed || (it.experience.renderState is ReaderRenderState.Ready && !it.experience.loading) }
                }
                BackPreviewFrame(backMotion) {
                    ReaderEntranceFrame(entrance = entrance, pageReady = pageReady) {
                        ReaderRoute(
                            onBack = { popWhenSettled(entry) },
                            onOpenFontLibrary = { navController.navigate(FontLibrary) },
                        )
                    }
                }
            }
        }
        // Composed after the NavHost (its graph is set by then, and the library's back handlers keep
        // their place after the NavHost's), drawn beneath it.
        LibraryLayer(navController, bookPane) {
            TopLevelNavigationShell(
                appViewModel = appViewModel,
                onOpenReader = ::navigateToReader,
                onOpenDetails = ::openDetails,
                onOpenFontLibrary = { navController.navigate(FontLibrary) },
                onOpenAbout = { navController.navigate(About) },
                onOpenReaderDefaults = { navController.navigate(ReaderDefaults) },
                onOpenSync = { navController.navigate(Sync) },
            )
        }
    }
}

/**
 * The library, kept composed under the root NavHost for the life of the app, in the Home entry's
 * owners (its ViewModels, lifecycle and saved state are Home's, as when it was Home's page).
 *
 * - It is drawn while Home or a book is on screen (and during moves to and from them); under any
 *   other page it is only kept, not drawn, and is hidden from accessibility.
 * - Its back handling (search, selection, the tab host) is live only while Home is on top.
 * - Beside a book pane its column narrows by the pane's open share, laid out each frame of the
 *   pane's move, so the grid reflows with it (its items animate to their new places).
 */
@Composable
private fun LibraryLayer(
    navController: NavHostController,
    pane: BookPaneSpace,
    content: @Composable () -> Unit,
) {
    val current by navController.currentBackStackEntryAsState()
    val visible by navController.visibleEntries.collectAsStateWithLifecycle()
    val home = remember(current) { runCatching { navController.getBackStackEntry<Home>() }.getOrNull() } ?: return
    val top = current?.destination
    val homeOnTop = top?.hasRoute<Home>() == true
    val shown = visible.any { it.destination.hasRoute<Home>() || it.destination.hasRoute<BookDetails>() }
    val besidePane = top?.hasRoute<BookDetails>() == true && pane.openness != null
    val back = rememberNavigationEventDispatcherOwner(enabled = homeOnTop)
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalViewModelStoreOwner provides home,
        LocalLifecycleOwner provides home,
        LocalSavedStateRegistryOwner provides home,
        LocalNavigationEventDispatcherOwner provides back,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val paneWidthPx = with(density) { bookPaneWidth(maxWidth).roundToPx() }
            Box(
                Modifier
                    .fillMaxSize()
                    .layout { measurable, constraints ->
                        val full = constraints.maxWidth
                        val open = pane.openness?.invoke() ?: 0f
                        val width = (full - (paneWidthPx * open).roundToInt()).coerceIn(0, full)
                        val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                        layout(full, placeable.height) { placeable.placeRelative(0, 0) }
                    }
                    .drawWithContent { if (shown) drawContent() }
                    .then(if (shown && (homeOnTop || besidePane)) Modifier else Modifier.clearAndSetSemantics { }),
            ) { content() }
        }
    }
}

@Composable
private fun TopLevelNavigationShell(
    appViewModel: AppViewModel,
    onOpenReader: (Reader, String) -> Unit,
    onOpenDetails: (String) -> Unit,
    onOpenFontLibrary: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenReaderDefaults: () -> Unit,
    onOpenSync: () -> Unit,
) {
    val navController = rememberNavController()
    val currentDestination = navController.currentBackStackEntryAsState().value?.destination
    val selected = when {
        currentDestination?.hasRoute<Stats>() == true -> YomuTopLevelDestination.Statistics
        currentDestination?.hasRoute<Settings>() == true -> YomuTopLevelDestination.Settings
        else -> YomuTopLevelDestination.Library
    }
    val transitions = rememberYomuScreenTransitions()
    // A back between tabs (gesture, back key) is Stack's one slide, like any page; a tap on a tab in
    // the bar keeps the tab switch's fade-through slide, even when navigation runs it as a pop.
    val backMotion = remember { BackMotion() }
    TrackBackMotion(backMotion, navController)
    val tabTap = remember { booleanArrayOf(false) }

    var librarySelecting by remember { mutableStateOf(false) }
    YomuTopLevelNavigation(
        selected = selected,
        onSelected = { destination -> if (destination != selected && navController.navigateTopLevel(destination)) tabTap[0] = true },
        barVisible = !librarySelecting,
    ) { hostModifier ->
        NavHost(
            navController = navController,
            startDestination = Library,
            modifier = hostModifier,
            enterTransition = transitions.enter,
            exitTransition = {
                tabTap[0] = false
                backMotion.forward(changesEntry = initialState.id != targetState.id)
                transitions.exit(this)
            },
            popEnterTransition = { if (tabTap[0]) transitions.popEnter(this) else EnterTransition.None },
            popExitTransition = {
                if (tabTap[0]) {
                    tabTap[0] = false
                    backMotion.forward(changesEntry = false)
                    transitions.popExit(this)
                } else {
                    backPopExit(backMotion, transitions.motionEnabled, transitions.layoutDirection)
                }
            },
            predictivePopEnterTransition = { EnterTransition.None },
            predictivePopExitTransition = { swipeEdge -> backPredictivePopExit(backMotion, swipeEdge, transitions.motionEnabled) },
        ) {
            composable<Library> {
                BackPreviewFrame(backMotion) {
                    LibraryRoute(
                        onOpenReader = { bookId -> onOpenReader(Reader(bookId), "resume") },
                        onOpenDetails = onOpenDetails,
                        onSelectionModeChange = { librarySelecting = it },
                        onHaveSyncWords = onOpenSync,
                    )
                }
            }
            composable<Stats> {
                BackPreviewFrame(backMotion) {
                    StatsRoute(
                        onBack = null,
                        onOpenLibrary = { if (navController.navigateTopLevel(YomuTopLevelDestination.Library)) tabTap[0] = true },
                    )
                }
            }
            composable<Settings> {
                BackPreviewFrame(backMotion) {
                    SettingsRoute(
                        appViewModel = appViewModel,
                        onBack = null,
                        onOpenFontLibrary = onOpenFontLibrary,
                        onOpenAbout = onOpenAbout,
                        onOpenReaderDefaults = onOpenReaderDefaults,
                        onOpenSync = onOpenSync,
                    )
                }
            }
        }
    }
}

/** Switches tab; false when nothing happened (the current tab is still settling). */
private fun NavHostController.navigateTopLevel(destination: YomuTopLevelDestination): Boolean {
    if (currentBackStackEntry?.lifecycle?.currentState != Lifecycle.State.RESUMED) return false

    when (destination) {
        YomuTopLevelDestination.Library -> navigate(Library) {
            popUpTo<Library> { saveState = true }
            launchSingleTop = true
            restoreState = true
        }

        YomuTopLevelDestination.Statistics -> navigate(Stats) {
            popUpTo<Library> { saveState = true }
            launchSingleTop = true
            restoreState = true
        }

        YomuTopLevelDestination.Settings -> navigate(Settings) {
            popUpTo<Library> { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    return true
}

/** With animations off the Resume motion is a short crossfade into the reader. */
private const val ResumeCrossfadeMillis = 150
