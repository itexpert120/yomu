package com.itexpert120.yomu.app.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.itexpert120.yomu.feature.about.AboutRoute
import com.itexpert120.yomu.feature.bookdetails.BookDetailsRoute
import com.itexpert120.yomu.feature.bookedit.EditBookRoute
import com.itexpert120.yomu.feature.library.LibraryRoute
import com.itexpert120.yomu.feature.reader.FontLibraryRoute
import com.itexpert120.yomu.feature.reader.ReaderDefaultsRoute
import com.itexpert120.yomu.feature.reader.ReaderRoute
import com.itexpert120.yomu.feature.settings.SettingsRoute
import com.itexpert120.yomu.feature.stats.StatsRoute

@Composable
fun YomuNavHost(
    appViewModel: AppViewModel,
    externalOpenViewModel: ExternalOpenViewModel,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val currentDestination = navController.currentBackStackEntryAsState().value?.destination
    val topLevelDestination = when {
        currentDestination?.hasRoute<Library>() == true -> YomuTopLevelDestination.Library
        currentDestination?.hasRoute<Stats>() == true -> YomuTopLevelDestination.Statistics
        currentDestination?.hasRoute<Settings>() == true -> YomuTopLevelDestination.Settings
        else -> null
    }

    // An EPUB opened from outside the app (file manager / share) is imported off-screen, then we
    // jump straight into the reader for the resolved book (an existing entry on a duplicate).
    LaunchedEffect(Unit) {
        externalOpenViewModel.openBook.collect { bookId ->
            navController.navigate(Reader(bookId))
        }
    }
    // Screen changes use one layered horizontal handoff. The direction follows the route pair,
    // while back reverses the same motion from the opposite side.
    val motionEnabled = yomuAnimationsEnabled()
    val layoutDirection = LocalLayoutDirection.current
    val screenTravelDistancePx = with(LocalDensity.current) {
        YomuMotion.ScreenTransitionDistance.roundToPx()
    }
    fun physicalDirection(forward: Boolean): Boolean = when (layoutDirection) {
        LayoutDirection.Ltr -> forward
        LayoutDirection.Rtl -> !forward
    }
    val navContent: @Composable (Modifier) -> Unit = { hostModifier ->
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
                    onOpenReader = { bookId -> navController.navigate(Reader(bookId)) },
                    onOpenDetails = { bookId -> navController.navigate(BookDetails(bookId)) },
                )
            }
            composable<BookDetails> { entry ->
                val args = entry.toRoute<BookDetails>()
                BookDetailsRoute(
                    onBack = navController::popBackStackIfResumed,
                    onRead = { navController.navigate(Reader(args.bookId)) },
                    onEdit = { navController.navigate(EditBook(args.bookId)) },
                    onOpenChapter = { locator -> navController.navigate(Reader(args.bookId, locator)) },
                )
            }
            composable<EditBook> {
                EditBookRoute(onBack = navController::popBackStackIfResumed)
            }
            composable<Settings> {
                SettingsRoute(
                    appViewModel = appViewModel,
                    onBack = null,
                    onOpenFontLibrary = { navController.navigate(FontLibrary) },
                    onOpenAbout = { navController.navigate(About) },
                )
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
            composable<Stats> {
                StatsRoute(onBack = null)
            }
            composable<About> {
                AboutRoute(onBack = navController::popBackStackIfResumed)
            }
            composable<Reader> {
                ReaderRoute(onBack = navController::popBackStackIfResumed)
            }
        }
    }

    // Keep the NavHost in one stable composition slot. The scaffold hides its chrome for child
    // routes instead of replacing the NavHost, so child screens retain route transitions.
    YomuTopLevelNavigation(
        selected = topLevelDestination,
        onSelected = navController::navigateTopLevel,
        content = navContent,
        modifier = modifier,
    )
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
    // Keep one coordinated shared-axis handoff in flight. Stacking destinations from rapid rail
    // or bar taps interrupts the easing curve and produces a visible position jump.
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
