package com.itexpert120.yomu.app.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.itexpert120.yomu.app.AppViewModel
import com.itexpert120.yomu.app.ExternalOpenViewModel
import com.itexpert120.yomu.feature.about.AboutRoute
import com.itexpert120.yomu.feature.bookdetails.BookDetailsRoute
import com.itexpert120.yomu.feature.bookedit.EditBookRoute
import com.itexpert120.yomu.feature.library.LibraryRoute
import com.itexpert120.yomu.feature.reader.ReaderDefaultsRoute
import com.itexpert120.yomu.feature.reader.ReaderRoute
import com.itexpert120.yomu.feature.settings.SettingsRoute
import com.itexpert120.yomu.feature.stats.StatsRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@Composable
fun YomuNavHost(
    appViewModel: AppViewModel,
    externalOpenViewModel: ExternalOpenViewModel,
    modifier: Modifier = Modifier,
    openBookFromWidget: Flow<String> = emptyFlow(),
) {
    val navController = rememberNavController()

    // An EPUB opened from outside the app (file manager / share) is imported off-screen, then we
    // jump straight into the reader for the resolved book (an existing entry on a duplicate).
    LaunchedEffect(Unit) {
        externalOpenViewModel.openBook.collect { bookId ->
            navController.navigate(Reader(bookId))
        }
    }
    // A home-screen widget tap deep-links straight into the reader for the requested book.
    LaunchedEffect(Unit) {
        openBookFromWidget.collect { bookId ->
            navController.navigate(Reader(bookId))
        }
    }
    // Material "shared axis (X)" — the transition Google's own apps use for hierarchical
    // navigation. Per MDC, it is exactly SlideDistance(30dp) + FadeThrough, NOT a plain
    // slide+crossfade and NOT the 0.92 scale (that belongs to the separate "fade through"
    // pattern). FadeThrough: the outgoing screen fades out over the first 35% of the duration
    // and the incoming fades in over the remaining 65%, so they never overlap at full opacity.
    // Values match MDC: 300ms, standard easing cubic-bezier(0.4,0,0.2,1), 0.35 threshold.
    // Built from official androidx.compose.animation primitives (MaterialSharedAxis is a
    // View-system class with no Compose drop-in). The pop transitions also drive predictive
    // back (android:enableOnBackInvokedCallback in manifest).
    val duration = 300
    val slide = with(LocalDensity.current) { 30.dp.roundToPx() }
    val fadeOutMs = (duration * 0.35f).toInt()
    val fadeInMs = duration - fadeOutMs
    val easing = FastOutSlowInEasing
    val incoming = { fadeIn(tween(fadeInMs, delayMillis = fadeOutMs, easing = easing)) }
    val outgoing = { fadeOut(tween(fadeOutMs, easing = easing)) }
    NavHost(
        navController = navController,
        startDestination = Library,
        modifier = modifier,
        enterTransition = {
            slideInHorizontally(tween(duration, easing = easing)) { slide } + incoming()
        },
        exitTransition = {
            slideOutHorizontally(tween(duration, easing = easing)) { -slide } + outgoing()
        },
        popEnterTransition = {
            slideInHorizontally(tween(duration, easing = easing)) { -slide } + incoming()
        },
        popExitTransition = {
            slideOutHorizontally(tween(duration, easing = easing)) { slide } + outgoing()
        },
    ) {
        composable<Library> {
            val themePreference by appViewModel.themePreference.collectAsState()
            LibraryRoute(
                themePreference = themePreference,
                onOpenReader = { bookId -> navController.navigate(Reader(bookId)) },
                onOpenDetails = { bookId -> navController.navigate(BookDetails(bookId)) },
                onThemeToggle = appViewModel::onCycleTheme,
                onOpenStats = { navController.navigate(Stats) },
                onOpenSettings = { navController.navigate(Settings) },
            )
        }
        composable<BookDetails> { entry ->
            val args = entry.toRoute<BookDetails>()
            BookDetailsRoute(
                onBack = navController::popBackStack,
                onRead = { navController.navigate(Reader(args.bookId)) },
                onEdit = { navController.navigate(EditBook(args.bookId)) },
                onOpenChapter = { locator -> navController.navigate(Reader(args.bookId, locator)) },
            )
        }
        composable<EditBook> {
            EditBookRoute(onBack = navController::popBackStack)
        }
        composable<Settings> {
            SettingsRoute(
                appViewModel = appViewModel,
                onBack = navController::popBackStack,
                onOpenStats = { navController.navigate(Stats) },
                onOpenReaderDefaults = { navController.navigate(ReaderDefaults) },
                onOpenAbout = { navController.navigate(About) },
            )
        }
        composable<ReaderDefaults> {
            ReaderDefaultsRoute(onBack = navController::popBackStack)
        }
        composable<Stats> {
            StatsRoute(onBack = navController::popBackStack)
        }
        composable<About> {
            AboutRoute(onBack = navController::popBackStack)
        }
        composable<Reader> {
            ReaderRoute(onBack = navController::popBackStack)
        }
    }
}
