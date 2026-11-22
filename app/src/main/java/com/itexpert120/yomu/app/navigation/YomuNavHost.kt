package com.itexpert120.yomu.app.navigation

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventTransitionState
import com.itexpert120.yomu.app.AppViewModel
import com.itexpert120.yomu.app.ExternalOpenViewModel
import com.itexpert120.yomu.core.designsystem.YomuMotion
import com.itexpert120.yomu.core.designsystem.yomuAnimationsEnabled
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

    // Like Mihon, Home owns both the top-level content and its navigation chrome. The root
    // transition therefore moves the whole Home frame—including the bar or rail—to a child screen.
    val backMotion = remember { BackMotion() }
    // A system back gesture in progress (never a tapped back button): the pop it scrubs tracks the
    // finger linearly. Public navigation-event state, unlike Transition.isSeeking.
    val activity = LocalActivity.current
    LaunchedEffect(activity) {
        val dispatcher = (activity as? NavigationEventDispatcherOwner)?.navigationEventDispatcher ?: return@LaunchedEffect
        dispatcher.transitionState.collect { state ->
            if (state is NavigationEventTransitionState.InProgress) {
                backMotion.released = false
                backMotion.gestureInFlight = true
                backMotion.recordEdge(state.latestEvent.swipeEdge)
            } else {
                backMotion.released = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Home,
        modifier = modifier,
        enterTransition = transitions.pushEnter,
        exitTransition = {
            // A forward move starts fresh: nothing from an earlier back gesture may carry over.
            backMotion.predictive = false
            backMotion.tapPop = false
            backMotion.gestureInFlight = false
            if (initialState.id != targetState.id) backMotion.opening = true
            transitions.pushExit(this)
        },
        // A back tap plays the same Material back as a released gesture, from rest (see BackPreviewFrame).
        popEnterTransition = { EnterTransition.None },
        popExitTransition = {
            val sameEntry = initialState.id == targetState.id
            if (sameEntry || !motionEnabled) {
                backMotion.predictive = false
                ExitTransition.None
            } else {
                backMotion.predictive = true
                // NavHost re-runs this when a released gesture commits; that is not a tap.
                if (!backMotion.gestureInFlight) {
                    backMotion.tapPop = true
                    backMotion.swipeEdge = if (layoutDirection == LayoutDirection.Rtl) NavigationEvent.EDGE_RIGHT else NavigationEvent.EDGE_LEFT
                }
                ExitTransition.KeepUntilTransitionsFinished
            }
        },
        // A back gesture: both pages hold still and BackPreviewFrame draws the Material predictive
        // back from the gesture-seeked transition (the leaving page shrinks, rounds and drifts with
        // the finger over the page underneath, then fades away on commit).
        predictivePopEnterTransition = { EnterTransition.None },
        predictivePopExitTransition = { swipeEdge ->
            backMotion.predictive = motionEnabled
            backMotion.tapPop = false
            backMotion.gestureInFlight = true
            backMotion.recordEdge(swipeEdge)
            ExitTransition.KeepUntilTransitionsFinished
        },
    ) {
        composable<Home> {
            BackPreviewFrame(backMotion) {
                TopLevelNavigationShell(
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

    val visibleEntries by navController.visibleEntries.collectAsStateWithLifecycle()
    LaunchedEffect(visibleEntries.size) {
        if (visibleEntries.size <= 1) backMotion.opening = false
    }
    backMotion.popSettled = { if (navController.previousBackStackEntry != null) navController.popBackStack() }
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
    val transitions = rememberYomuScreenTransitions()

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
            enterTransition = transitions.enter,
            exitTransition = transitions.exit,
            popEnterTransition = transitions.popEnter,
            popExitTransition = transitions.popExit,
            // A back gesture between tabs scrubs the same slide a back tap plays; Navigation's
            // default would shrink the tab unclipped inside the shell instead.
            predictivePopEnterTransition = { transitions.popEnter(this) },
            predictivePopExitTransition = { transitions.popExit(this) },
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
    // The running pop is a gesture-driven predictive back (Material style, drawn by BackPreviewFrame).
    var predictive by mutableStateOf(false)

    // NavigationEvent.EDGE_LEFT / EDGE_RIGHT: the leaving page drifts away from the edge it was pulled from.
    var swipeEdge = NavigationEvent.EDGE_LEFT

    // NavHost resets its edge to EDGE_NONE as a gesture ends, and some events carry no edge; only a
    // real left/right edge replaces the one this gesture started with.
    fun recordEdge(edge: Int) {
        if (edge == NavigationEvent.EDGE_LEFT || edge == NavigationEvent.EDGE_RIGHT) swipeEdge = edge
    }

    // False while a finger is on a back gesture. Each page frame tracks its seeked fraction until
    // then and keeps the value at release, which splits the transition into the drag and the
    // commit (or cancel) that follows.
    var released = true

    // A back tap / back key (no gesture): the same Material back, committing from rest.
    var tapPop = false

    // A forward move is still animating; a back gesture can't be previewed until it settles.
    var opening by mutableStateOf(false)

    // Pops the top page; called once a back gesture taken during [opening] has been released.
    var popSettled: () -> Unit = {}

    // From a gesture's first event until its pop finishes (the leaving page goes) or its cancel
    // settles back to rest.
    var gestureInFlight = false
}

/**
 * The parts of the back motion a transition spec can't express: the page underneath starts dimmed
 * and brightens as it slides in, and the page being pulled off casts a soft shadow from its leading
 * edge onto it, so the seam reads as one page lying on another. Forward handoffs are untouched.
 */
@Composable
private fun AnimatedContentScope.BackPreviewFrame(
    motion: BackMotion,
    content: @Composable () -> Unit,
) {
    val leaving = transition.targetState == EnterExitState.PostExit
    // Runs on every transition: whether it is a Material back is only known once NavHost has picked
    // the transition (BackMotion.predictive, set from its spec lambdas during this same composition),
    // so that flag is read when drawing, not here.
    val motionEnabled = yomuAnimationsEnabled()
    // 0 on the resting page, 1 at the far end of a predictive back (PostExit for the leaving page,
    // PreEnter for the page underneath). The NavHost seeks this with the gesture, then plays the rest
    // on commit, or back to 0 on cancel.
    val predictiveFraction = transition.animateFloat(
        transitionSpec = { tween(YomuMotion.PredictiveBackMillis, easing = LinearEasing) },
        label = "predictiveBack",
    ) { state -> if (motionEnabled && state != EnterExitState.Visible) 1f else 0f }
    // This page's seeked fraction at the moment the finger lifted (see BackMotion.released).
    val releaseAt = remember { floatArrayOf(0f) }
    // The leaving page is removed once its pop has played out.
    DisposableEffect(Unit) {
        onDispose { if (motion.released) motion.gestureInFlight = false }
    }
    // A back gesture begun while a page is still opening can't be previewed: NavHost would seek a
    // transition that is already running toward that page, which leaves the page frozen under the
    // finger and then cuts. The page takes those gestures itself (its handler is registered after
    // NavHost's, so it wins) and pops once it has settled, so the back plays as it does for a tap.
    // It stays enabled until its gesture ends: disabling a handler mid-gesture completes the gesture.
    var earlyBack by remember { mutableStateOf(false) }
    PredictiveBackHandler(enabled = motion.opening || earlyBack) { progress ->
        earlyBack = true
        try {
            progress.collect { }
            snapshotFlow { motion.opening }.first { !it }
            motion.popSettled()
        } finally {
            earlyBack = false
        }
    }
    val density = LocalDensity.current
    val cornerPx = with(density) { YomuMotion.PredictiveBackCorner.toPx() }
    val edgeMarginPx = with(density) { YomuMotion.PredictiveBackEdgeMargin.toPx() }
    Box(
        modifier = Modifier
            .graphicsLayer {
                val t = predictiveFraction.value
                if (t <= 0f && motion.released) motion.gestureInFlight = false
                if (t <= 0f || !motion.predictive) {
                    clip = false
                    return@graphicsLayer
                }
                // Both pages move, as in Google's apps. The pose follows the finger while dragged;
                // after release the commit animates from wherever the pages are (a fast flick
                // commits straight from its small drag), and a cancel retraces the drag.
                val fraction = if (leaving) t else 1f - t
                if (!motion.released) releaseAt[0] = fraction
                val pose = PredictivePose.of(fraction, release = if (motion.tapPop) 0f else releaseAt[0])
                val fromRight = motion.swipeEdge == NavigationEvent.EDGE_RIGHT
                if (leaving) {
                    // The top page shrinks toward 90% and drifts toward the far side while
                    // dragged, then fades out quickly as it keeps shrinking.
                    val dragScale = lerp(1f, 0.9f, pose.drag)
                    val scale = lerp(dragScale, 0.84f, pose.commit)
                    scaleX = scale
                    scaleY = scale
                    val drift = (size.width / 20f - edgeMarginPx).coerceAtLeast(0f) * pose.drag
                    translationX = if (fromRight) -drift else drift
                    alpha = 1f - (pose.commitTime / PredictiveFadeSpan).coerceAtMost(1f)
                    shape = RoundedCornerShape(cornerPx * (maxOf(pose.drag, pose.commit) / 0.1f).coerceAtMost(1f))
                } else {
                    // The page underneath waits shrunk, shifted toward the edge the gesture came
                    // from and darkened; on commit it glides to centre, grows and brightens.
                    val waitScale = lerp(0.9f, 0.92f, pose.drag)
                    val scale = lerp(waitScale, 1f, pose.commit)
                    scaleX = scale
                    scaleY = scale
                    val offset = size.width * PredictiveEnterOffset * (1f - 0.2f * pose.drag) * (1f - pose.commit)
                    translationX = if (fromRight) offset else -offset
                    shape = RoundedCornerShape(cornerPx * (1f - pose.commit))
                }
                clip = true
            }
            .drawWithContent {
                drawContent()
                // Predictive back: the page underneath is darkened, lifting a little with the drag and
                // fully once the back commits.
                val t = predictiveFraction.value
                if (!leaving && t > 0f && motion.predictive) {
                    val pose = PredictivePose.of(1f - t, release = if (motion.tapPop) 0f else releaseAt[0])
                    drawRect(Color.Black, alpha = YomuMotion.PredictiveBackScrim * (1f - 0.25f * pose.drag) * (1f - pose.commit))
                }
            },
    ) {
        content()
    }
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

// The top page fades out over the first PredictiveFadeSpan of the commit while it keeps shrinking,
// so it clears quickly instead of lingering over the page underneath as a double exposure. The page
// underneath starts PredictiveEnterOffset of the width toward the swipe edge.
private const val PredictiveFadeSpan = 0.3f
private const val PredictiveEnterOffset = 0.1f

/**
 * Where a predictive back stands, from the NavHost-seeked transition [fraction] (which equals the
 * gesture progress while dragging) and the gesture progress at [release]: [drag] is the decelerated
 * drag (frozen at release while committing; retracing on cancel), [commit] the eased 0..1 commit for
 * geometry and [commitTime] its linear time, for the fade (the eased curve front-loads too hard to fade on).
 */
private class PredictivePose(val drag: Float, val commit: Float, val commitTime: Float) {
    companion object {
        fun of(fraction: Float, release: Float): PredictivePose {
            val dragged = minOf(fraction, release).coerceIn(0f, 1f)
            // Decelerating, so the page shows most of its shrink early in the drag, as the platform does.
            val drag = 1f - (1f - dragged) * (1f - dragged)
            val commitTime = if (fraction > release && release < 1f) ((fraction - release) / (1f - release)).coerceIn(0f, 1f) else 0f
            return PredictivePose(drag, YomuMotion.EmphasizedDecel.transform(commitTime), commitTime)
        }
    }
}
