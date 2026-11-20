package com.itexpert120.yomu.app.navigation

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventTransitionState
import com.itexpert120.yomu.app.AppViewModel
import com.itexpert120.yomu.app.ExternalOpenViewModel
import com.itexpert120.yomu.core.designsystem.YomuMotion
import com.itexpert120.yomu.core.designsystem.yomuAnimationsEnabled
import com.itexpert120.yomu.core.designsystem.yomuBackEasing
import com.itexpert120.yomu.core.designsystem.yomuScreenEnter
import com.itexpert120.yomu.core.designsystem.yomuScreenExit
import com.itexpert120.yomu.core.designsystem.yomuScreenPopEnter
import com.itexpert120.yomu.core.designsystem.yomuScreenPopExit
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
    val backMotion = remember { BackMotion() }
    // A system back gesture in progress (never a tapped back button): the pop it scrubs tracks the
    // finger linearly. Public navigation-event state, unlike Transition.isSeeking.
    val activity = LocalActivity.current
    LaunchedEffect(activity) {
        val dispatcher = (activity as? NavigationEventDispatcherOwner)?.navigationEventDispatcher ?: return@LaunchedEffect
        dispatcher.transitionState.collect { state ->
            if (state is NavigationEventTransitionState.InProgress) backMotion.gestureDriven = true
        }
    }

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
            backMotion.popping = false
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
                // Home moves its content itself so its navigation bar/rail can stay put.
                targetState.destination.hasRoute<Home>() -> EnterTransition.None
                else -> yomuScreenPopEnter(fromStart = physicalDirection(true), gestureDriven = { backMotion.gestureDriven })
            }
        },
        popExitTransition = {
            backMotion.popping = true
            val sameEntry = initialState.id == targetState.id
            when {
                sameEntry || !motionEnabled -> ExitTransition.None
                else -> yomuScreenPopExit(towardEnd = physicalDirection(true), gestureDriven = { backMotion.gestureDriven })
            }
        },
    ) {
        composable<Home> {
            // Home only ever enters by a pop: its content slides in from the parallax offset on the
            // shared back curve, while the bar/rail (part of Home) stays fixed.
            val backOffset = transition.animateFloat(
                transitionSpec = {
                    tween(YomuMotion.ScreenPopDurationMillis, easing = yomuBackEasing { backMotion.gestureDriven })
                },
                label = "homeBackOffset",
            ) { state -> if (state == EnterExitState.PreEnter && motionEnabled) 1f else 0f }
            BackPreviewFrame(backMotion, dim = false) {
                TopLevelNavigationShell(
                    contentBackOffset = { backOffset.value },
                    appViewModel = appViewModel,
                    onOpenReader = ::navigateToReader,
                    onOpenDetails = { bookId -> navController.navigate(BookDetails(bookId)) },
                    onOpenFontLibrary = { navController.navigate(FontLibrary) },
                    onOpenAbout = { navController.navigate(About) },
                )
            }
        }
        composable<BookDetails> { entry ->
            BackPreviewFrame(backMotion) {
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
        }
        composable<EditBook> {
            BackPreviewFrame(backMotion) {
                EditBookRoute(onBack = navController::popBackStackIfResumed)
            }
        }
        composable<ReaderDefaults> {
            BackPreviewFrame(backMotion) {
                ReaderDefaultsRoute(
                    onBack = navController::popBackStackIfResumed,
                    onOpenFontLibrary = { navController.navigate(FontLibrary) },
                )
            }
        }
        composable<FontLibrary> {
            BackPreviewFrame(backMotion) {
                FontLibraryRoute(onBack = navController::popBackStackIfResumed)
            }
        }
        composable<About> {
            BackPreviewFrame(backMotion) {
                AboutRoute(onBack = navController::popBackStackIfResumed)
            }
        }
        composable<Reader> {
            BackPreviewFrame(backMotion) {
                ReaderRoute(onBack = navController::popBackStackIfResumed)
            }
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
    contentBackOffset: () -> Float = { 0f },
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
        contentBackOffset = contentBackOffset,
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

/** Whether the root host's running transition is a back (pop) rather than a forward handoff. */
private class BackMotion {
    var popping = false

    // True once a predictive-back gesture has scrubbed the running pop; picks the linear easing.
    var gestureDriven = false
}

/**
 * The parts of the back motion a transition spec can't express: the page underneath starts dimmed
 * and brightens as it slides in, and the page being pulled off casts a soft shadow from its leading
 * edge onto it, so the seam reads as one page lying on another. Forward handoffs are untouched.
 */
@Composable
private fun AnimatedContentScope.BackPreviewFrame(
    motion: BackMotion,
    // Home dims only its content (see contentBackOffset), never its navigation chrome.
    dim: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dimAlpha = transition.animateFloat(
        transitionSpec = { tween(YomuMotion.ScreenPopDurationMillis, easing = LinearEasing) },
        label = "backPreviewDim",
    ) { state -> if (state == EnterExitState.PreEnter) YomuMotion.BackPreviewDim else 0f }
    val leaving = transition.targetState == EnterExitState.PostExit
    // A gesture-driven pop keeps its linear easing until the transition settles, then the next back
    // starts out as a (decelerating) button press again.
    LaunchedEffect(transition) {
        snapshotFlow { transition.isRunning }.collect { running -> if (!running) motion.gestureDriven = false }
    }
    val shadowColor = MaterialTheme.colorScheme.scrim
    val endToStart = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(
        modifier = Modifier
            .drawBehind {
                if (!motion.popping || !leaving) return@drawBehind
                // Drawn just outside the page's leading edge, over the page underneath.
                val width = YomuMotion.BackEdgeShadowWidth.toPx()
                val left = if (endToStart) size.width else -width
                val stops = YomuMotion.BackEdgeShadowStops.map { (at, strength) ->
                    val position = if (endToStart) 1f - at else at
                    position to shadowColor.copy(alpha = YomuMotion.BackEdgeShadowAlpha * strength)
                }.sortedBy { it.first }.toTypedArray()
                drawRect(
                    brush = Brush.horizontalGradient(*stops, startX = left, endX = left + width),
                    topLeft = Offset(left, 0f),
                    size = Size(width, size.height),
                )
            }
            .drawWithContent {
                drawContent()
                val alpha = dimAlpha.value
                if (dim && motion.popping && alpha > 0f) drawRect(Color.Black, alpha = alpha)
            },
    ) {
        content()
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
