package com.itexpert120.yomu.app.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.itexpert120.yomu.app.AppViewModel
import com.itexpert120.yomu.app.ExternalOpenViewModel
import com.itexpert120.yomu.core.designsystem.YomuMotion
import com.itexpert120.yomu.core.designsystem.yomuAnimationsEnabled
import com.itexpert120.yomu.core.designsystem.yomuScreenEnter
import com.itexpert120.yomu.core.designsystem.yomuScreenExit
import com.itexpert120.yomu.core.reader.ReaderOpenTrace
import com.itexpert120.yomu.feature.about.AboutRoute
import com.itexpert120.yomu.feature.bookdetails.BookDetailsRoute
import com.itexpert120.yomu.feature.bookedit.EditBookRoute
import com.itexpert120.yomu.feature.library.LibraryRoute
import com.itexpert120.yomu.feature.reader.FontLibraryRoute
import com.itexpert120.yomu.feature.reader.ReaderDefaultsRoute
import com.itexpert120.yomu.feature.reader.ReaderRoute
import com.itexpert120.yomu.feature.settings.SettingsRoute
import com.itexpert120.yomu.feature.stats.StatsRoute
import kotlinx.coroutines.flow.first

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
    val pendingBooks by externalOpenViewModel.pendingBooks.collectAsState()
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

    val motionEnabled = yomuAnimationsEnabled()
    val layoutDirection = LocalLayoutDirection.current
    val screenTravelDistancePx = with(LocalDensity.current) {
        YomuMotion.ScreenTransitionDistance.roundToPx()
    }
    fun physicalDirection(forward: Boolean): Boolean = when (layoutDirection) {
        LayoutDirection.Ltr -> forward
        LayoutDirection.Rtl -> !forward
    }

    // Like Mihon, Home owns both the top-level content and its navigation chrome. The root
    // transition therefore moves the whole Home frame—including the bar or rail—to a child screen.
    NavHost(
        navController = navController,
        startDestination = Home,
        modifier = modifier,
        enterTransition = {
            val sameEntry = initialState.id == targetState.id
            val forward = initialState.destination.isForwardTransitionTo(targetState.destination)
            when {
                sameEntry || !motionEnabled -> EnterTransition.None
                else -> yomuScreenEnter(
                    travelDistancePx = screenTravelDistancePx,
                    forward = physicalDirection(forward),
                )
            }
        },
        exitTransition = {
            val sameEntry = initialState.id == targetState.id
            val forward = initialState.destination.isForwardTransitionTo(targetState.destination)
            when {
                sameEntry || !motionEnabled -> ExitTransition.None
                else -> yomuScreenExit(
                    travelDistancePx = screenTravelDistancePx,
                    forward = physicalDirection(forward),
                )
            }
        },
        popEnterTransition = {
            val sameEntry = initialState.id == targetState.id
            when {
                sameEntry || !motionEnabled -> EnterTransition.None
                else -> yomuScreenEnter(
                    travelDistancePx = screenTravelDistancePx,
                    forward = physicalDirection(false),
                )
            }
        },
        popExitTransition = {
            val sameEntry = initialState.id == targetState.id
            when {
                sameEntry || !motionEnabled -> ExitTransition.None
                else -> yomuScreenExit(
                    travelDistancePx = screenTravelDistancePx,
                    forward = physicalDirection(false),
                )
            }
        },
    ) {
        composable<Home> {
            TopLevelNavigationShell(
                appViewModel = appViewModel,
                onOpenReader = ::navigateToReader,
                onOpenDetails = { bookId -> navController.navigate(BookDetails(bookId)) },
                onOpenFontLibrary = { navController.navigate(FontLibrary) },
                onOpenAbout = { navController.navigate(About) },
            )
        }
        composable<BookDetails> { entry ->
            val args = entry.toRoute<BookDetails>()
            BookDetailsRoute(
                onBack = navController::popBackStackIfResumed,
                onRead = { navigateToReader(Reader(args.bookId), "details-resume") },
                onEdit = { navController.navigate(EditBook(args.bookId)) },
                onOpenChapter = { locator ->
                    navigateToReader(Reader(args.bookId, locator), "chapter")
                },
            )
        }
        composable<EditBook> {
            EditBookRoute(onBack = navController::popBackStackIfResumed)
        }
        composable<ReaderDefaults> {
            ReaderDefaultsRoute(
                onBack = navController::popBackStackIfResumed,
                onOpenFontLibrary = { navController.navigate(FontLibrary) },
            )
        }
        composable<FontLibrary> {
            FontLibraryRoute(onBack = navController::popBackStackIfResumed)
        }
        composable<About> {
            AboutRoute(onBack = navController::popBackStackIfResumed)
        }
        composable<Reader> {
            ReaderRoute(onBack = navController::popBackStackIfResumed)
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
) {
    val navController = rememberNavController()
    val currentDestination = navController.currentBackStackEntryAsState().value?.destination
    val selected = when {
        currentDestination?.hasRoute<Stats>() == true -> YomuTopLevelDestination.Statistics
        currentDestination?.hasRoute<Settings>() == true -> YomuTopLevelDestination.Settings
        else -> YomuTopLevelDestination.Library
    }
    val motionEnabled = yomuAnimationsEnabled()
    val layoutDirection = LocalLayoutDirection.current
    val screenTravelDistancePx = with(LocalDensity.current) {
        YomuMotion.ScreenTransitionDistance.roundToPx()
    }
    fun physicalDirection(forward: Boolean): Boolean = when (layoutDirection) {
        LayoutDirection.Ltr -> forward
        LayoutDirection.Rtl -> !forward
    }

    var librarySelecting by remember { mutableStateOf(false) }
    YomuTopLevelNavigation(
        selected = selected,
        onSelected = navController::navigateTopLevel,
        barVisible = !librarySelecting,
    ) { hostModifier ->
        NavHost(
            navController = navController,
            startDestination = Library,
            modifier = hostModifier,
            enterTransition = {
                val sameEntry = initialState.id == targetState.id
                val forward = initialState.destination.isForwardTransitionTo(targetState.destination)
                when {
                    sameEntry || !motionEnabled -> EnterTransition.None
                    else -> yomuScreenEnter(
                        travelDistancePx = screenTravelDistancePx,
                        forward = physicalDirection(forward),
                    )
                }
            },
            exitTransition = {
                val sameEntry = initialState.id == targetState.id
                val forward = initialState.destination.isForwardTransitionTo(targetState.destination)
                when {
                    sameEntry || !motionEnabled -> ExitTransition.None
                    else -> yomuScreenExit(
                        travelDistancePx = screenTravelDistancePx,
                        forward = physicalDirection(forward),
                    )
                }
            },
            popEnterTransition = {
                val sameEntry = initialState.id == targetState.id
                when {
                    sameEntry || !motionEnabled -> EnterTransition.None
                    else -> yomuScreenEnter(
                        travelDistancePx = screenTravelDistancePx,
                        forward = physicalDirection(false),
                    )
                }
            },
            popExitTransition = {
                val sameEntry = initialState.id == targetState.id
                when {
                    sameEntry || !motionEnabled -> ExitTransition.None
                    else -> yomuScreenExit(
                        travelDistancePx = screenTravelDistancePx,
                        forward = physicalDirection(false),
                    )
                }
            },
        ) {
            composable<Library> {
                LibraryRoute(
                    onOpenReader = { bookId -> onOpenReader(Reader(bookId), "resume") },
                    onOpenDetails = onOpenDetails,
                    onSelectionModeChange = { librarySelecting = it },
                )
            }
            composable<Stats> {
                StatsRoute(onBack = null)
            }
            composable<Settings> {
                SettingsRoute(
                    appViewModel = appViewModel,
                    onBack = null,
                    onOpenFontLibrary = onOpenFontLibrary,
                    onOpenAbout = onOpenAbout,
                )
            }
        }
    }
}

/** Top-level destinations have a stable left-to-right order for directional screen changes. */
private fun NavDestination.isForwardTransitionTo(target: NavDestination): Boolean {
    val fromIndex = topLevelIndex()
    val targetIndex = target.topLevelIndex()
    return if (fromIndex != null && targetIndex != null) {
        targetIndex > fromIndex
    } else {
        true
    }
}

private fun NavDestination.topLevelIndex(): Int? = when {
    hasRoute<Library>() -> 0
    hasRoute<Stats>() -> 1
    hasRoute<Settings>() -> 2
    else -> null
}

private fun NavHostController.navigateTopLevel(destination: YomuTopLevelDestination) {
    if (currentBackStackEntry?.lifecycle?.currentState != Lifecycle.State.RESUMED) return

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
}

/** Ignore repeat taps while the current destination is already leaving the screen. */
private fun NavHostController.popBackStackIfResumed() {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        popBackStack()
    }
}
