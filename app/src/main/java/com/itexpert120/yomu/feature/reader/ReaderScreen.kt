package com.itexpert120.yomu.feature.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.itexpert120.yomu.core.designsystem.YomuBadgeShape
import com.itexpert120.yomu.core.designsystem.YomuBadgeTone
import com.itexpert120.yomu.core.designsystem.YomuButton
import com.itexpert120.yomu.core.designsystem.YomuButtonEmphasis
import com.itexpert120.yomu.core.designsystem.YomuEmptyState
import com.itexpert120.yomu.core.designsystem.YomuLoadingIndicator
import com.itexpert120.yomu.core.designsystem.YomuShapeTokens
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.yomuAnimationsEnabled
import com.itexpert120.yomu.core.designsystem.yomuChromeEnter
import com.itexpert120.yomu.core.designsystem.yomuChromeExit
import com.itexpert120.yomu.core.model.CustomReaderTheme
import com.itexpert120.yomu.core.model.ReaderLayout
import com.itexpert120.yomu.core.model.ReaderSettings
import com.itexpert120.yomu.core.reader.ReaderNavigator
import com.itexpert120.yomu.core.reader.ReaderRenderState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

// Intentionally colours the system bars to the reading theme via the (now-deprecated) window
// setters — the only way to keep the bars seamless with the page without a system scrim.
@Suppress("DEPRECATION")
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReaderScreen(
    state: ReaderUiState,
    onBack: () -> Unit,
    onRetryOpen: () -> Unit,
    onOpenSheet: () -> Unit,
    onCloseSheet: () -> Unit,
    onSelectChapter: (String) -> Unit,
    onNextChapter: () -> Unit,
    onPreviousChapter: () -> Unit,
    onUpdateSettings: (ReaderSettings) -> Unit,
    onResetSettings: () -> Unit,
    onOpenCustomTheme: () -> Unit,
    onCloseCustomTheme: () -> Unit,
    onApplyCustomTheme: (CustomReaderTheme) -> Unit,
    onSaveCustomTheme: (String) -> Unit,
    onDeleteCustomTheme: (String) -> Unit,
    // Browse sheet (Contents / Bookmarks / Highlights).
    onOpenBrowse: () -> Unit,
    onSelectBrowseTab: (BrowseTab) -> Unit,
    onCloseBrowse: () -> Unit,
    onJumpToLocator: (String) -> Unit,
    onJumpToBookmark: (String) -> Unit,
    onJumpToHighlight: (String) -> Unit,
    onJumpToSearchResult: (String) -> Unit,
    onDeleteBookmarkById: (String) -> Unit,
    onDeleteHighlightById: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    // Word lookup + footnote popups.
    onCloseLookup: () -> Unit,
    onLookUpWord: (String) -> Unit,
    onLookupBack: () -> Unit,
    onRetryLookup: () -> Unit,
    onPronounce: (String) -> Unit,
    onCloseFootnote: () -> Unit,
    // Highlight edit popup + bookmark toggle.
    onDeleteHighlight: () -> Unit,
    onSetHighlightColor: (Int) -> Unit,
    onCloseEditHighlight: () -> Unit,
    onToggleBookmark: () -> Unit,
    onReadingResumed: () -> Unit,
    onReadingPaused: () -> Unit,
    onRetrySettings: () -> Unit = {},
    onRetryAnnotations: () -> Unit = {},
    onAcceptSyncOffer: () -> Unit = {},
    onDismissSyncOffer: () -> Unit = {},
) {
    // One notice at a time, in priority order. Each stays composed (with a null value) so it can
    // play its exit instead of being cut when another branch takes over.
    val settingsError = state.experience.settingsError
    val annotationError = state.experience.annotationError.takeIf { settingsError == null }
    SaveFailureNotice(settingsError, onRetrySettings)
    SaveFailureNotice(annotationError, onRetryAnnotations)
    SyncOfferNotice(
        offer = state.experience.syncOffer.takeIf { settingsError == null && annotationError == null },
        onJump = onAcceptSyncOffer,
        onDismiss = onDismissSyncOffer,
    )
    val view = LocalView.current
    val reading = state.experience
    val readyMarker = (reading.renderState as? ReaderRenderState.Ready)?.takeUnless { reading.loading }
    val navigator: ReaderNavigator? = reading.navigator
    var brightnessPreview by remember { mutableStateOf<Float?>(null) }
    var dimPreview by remember { mutableStateOf<Float?>(null) }

    // Keep the display awake while reading, per the user's setting; released when leaving the reader.
    DisposableEffect(reading.settings.keepScreenOn) {
        val previous = view.keepScreenOn
        view.keepScreenOn = reading.settings.keepScreenOn
        onDispose { view.keepScreenOn = previous }
    }

    // Count foreground reading time toward statistics: accumulate between resume and pause.
    DisposableEffect(Unit) {
        val lifecycle = (view.context.findActivity() as? LifecycleOwner)?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> onReadingResumed()
                Lifecycle.Event.ON_PAUSE -> onReadingPaused()
                else -> Unit
            }
        }
        lifecycle?.addObserver(observer)
        // Start immediately: the screen is on-screen now (don't rely on a future resume event).
        onReadingResumed()
        onDispose {
            lifecycle?.removeObserver(observer)
            onReadingPaused()
        }
    }
    // The system bars show while the reader menu is up, so one back gesture (or the status bar) leaves
    // the reader instead of the first swipe only revealing hidden bars. Otherwise reading is full-screen.
    val systemBarsShown = state.chapterControlsVisible
    val currentSystemBarsShown by rememberUpdatedState(systemBarsShown)
    // Hide both system bars for full-screen reading. Android re-shows the bars whenever the window
    // loses and regains focus (returning from background, multi-window, etc.), so this must be
    // re-applied on every ON_RESUME — not just once. A one-shot effect here is why content used to
    // reappear below the status bar after the app came back from the background.
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val restoreWindow = window?.let { captureReaderWindow(it, view) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window?.isStatusBarContrastEnforced = false
            window?.isNavigationBarContrastEnforced = false
        }
        fun hideSystemBars() {
            window ?: return
            if (currentSystemBarsShown) showReaderSystemBars(window, view) else hideReaderSystemBars(window, view)
        }
        val lifecycle = (view.context.findActivity() as? LifecycleOwner)?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hideSystemBars()
        }
        lifecycle?.addObserver(observer)
        // ON_RESUME/focus-regain fire too late: Android shows the bars as a "courtesy" the moment the
        // window regains focus (unlock, return from launcher/recents), and by the time either callback
        // runs the system has already animated them in — re-hiding from there just produces a brief
        // visible flash before hideSystemBars() catches up. Listen for the inset-visibility change
        // itself instead: it fires the instant the system shows the bars, so we can re-hide immediately.
        // Returning the incoming value lets the normal ViewGroup dispatch continue. Calling
        // ViewCompat.onApplyWindowInsets on this same decorated view from inside its listener would
        // recursively invoke the listener.
        val decorView = window?.decorView
        val insetsListener = OnApplyWindowInsetsListener { _, insets ->
            if (insets.isVisible(WindowInsetsCompat.Type.systemBars()) && !currentSystemBarsShown) hideSystemBars()
            insets
        }
        decorView?.let { ViewCompat.setOnApplyWindowInsetsListener(it, insetsListener) }
        val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) hideSystemBars()
        }
        view.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)
        hideSystemBars()
        onDispose {
            lifecycle?.removeObserver(observer)
            view.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
            decorView?.let { ViewCompat.setOnApplyWindowInsetsListener(it, null) }
            restoreWindow?.invoke()
        }
    }
    LaunchedEffect(systemBarsShown) {
        val window = view.context.findActivity()?.window ?: return@LaunchedEffect
        if (systemBarsShown) showReaderSystemBars(window, view) else hideReaderSystemBars(window, view)
    }
    // Colour the system bars to the reading background so the status area matches the page on every
    // Android version (on API 35+ the bar is transparent and the chrome backdrop shows through).
    LaunchedEffect(reading.settings.backgroundArgb, reading.settings.isLightBackground) {
        val window = view.context.findActivity()?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        val barColor = Color(reading.settings.backgroundArgb).toArgb()
        window.statusBarColor = barColor
        window.navigationBarColor = barColor
        controller.isAppearanceLightStatusBars = reading.settings.isLightBackground
        controller.isAppearanceLightNavigationBars = reading.settings.isLightBackground
    }
    LaunchedEffect(
        reading.settings.useSystemBrightness,
        reading.settings.brightness,
        brightnessPreview,
    ) {
        val preview = brightnessPreview
        if (reading.settings.useSystemBrightness) {
            brightnessPreview = null
        } else if (preview != null && kotlin.math.abs(preview - reading.settings.brightness) < 0.001f) {
            brightnessPreview = null
        }
    }

    LaunchedEffect(reading.settings.dimLevel, dimPreview) {
        val preview = dimPreview
        if (preview != null && kotlin.math.abs(preview - reading.settings.dimLevel) < 0.001f) {
            dimPreview = null
        }
    }

    val effectiveBrightness = brightnessPreview ?: reading.settings.brightness
    val effectiveDim = (dimPreview ?: reading.settings.dimLevel).coerceIn(0f, 1f)
    val sheetState = if (brightnessPreview != null || dimPreview != null) {
        state.copy(
            experience = reading.copy(
                settings = reading.settings.copy(
                    brightness = effectiveBrightness,
                    dimLevel = effectiveDim,
                ),
            ),
        )
    } else {
        state
    }

    // Drive the window screen brightness: defer to the system level, or pin it to the reader setting.
    LaunchedEffect(reading.settings.useSystemBrightness, effectiveBrightness) {
        val window = view.context.findActivity()?.window ?: return@LaunchedEffect
        val lp = window.attributes
        lp.screenBrightness = if (reading.settings.useSystemBrightness) {
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        } else {
            effectiveBrightness.coerceIn(0f, 1f)
        }
        window.attributes = lp
    }

    val density = LocalDensity.current
    var topBarPx by remember { mutableIntStateOf(0) }
    var footerPx by remember { mutableIntStateOf(0) }
    // Reserve the complete measured bar. AndroidView adjusts dispatched window insets for its
    // position, so subtracting the status inset here would leave content underneath the title bar.
    val fullTop = with(density) { topBarPx.toDp() }
    val pagedSafeTop = WindowInsets.displayCutout
        .union(WindowInsets.statusBarsIgnoringVisibility)
        .asPaddingValues()
        .calculateTopPadding()
    val pagedSafeBottom = WindowInsets.navigationBarsIgnoringVisibility
        .asPaddingValues()
        .calculateBottomPadding()
    val baseTopInset = fullTop
    val footerHeight = if (reading.settings.showFooter) with(density) { footerPx.toDp() } else 0.dp
    val scrollEndPadding =
        if (reading.settings.layout == ReaderLayout.Scroll && reading.settings.showFooter) {
            // Just enough breathing room so the last line doesn't sit tight under the footer; the footer
            // height itself is already reserved below.
            YomuTheme.space.xxs
        } else {
            0.dp
        }
    // Footer off: content flows under a fully transparent gesture bar. In scroll mode, reserve a
    // little extra end space so the last lines of a chapter don't sit tight against the footer.
    val baseBottomInset = footerHeight + scrollEndPadding
    // Immersive mode: the page is always full-bleed (edge to edge, under the status-bar area) and the
    // top bar + footer OVERLAY it — appearing/disappearing on a centre tap without reflowing the text.
    // Non-immersive keeps the page inset below the bars (their height reserved). The chrome always
    // stays shown while loading or when immersive is off (title/Back/footer visible).
    val immersive = reading.settings.immersiveChrome
    // A chapter transition keeps the chrome as it was; only the initial open (or a failure) forces
    // it visible. Treating transitions as "not ready" flashed the immersive top bar every chapter.
    val chromeForcedByLoad = reading.renderState is ReaderRenderState.Opening ||
        reading.renderState is ReaderRenderState.Failed
    val readerSurfacesAvailable = reading.renderState !is ReaderRenderState.Opening
    val chromeShown = chromeForcedByLoad || !immersive || state.chapterControlsVisible
    val topInset = when {
        immersive && reading.settings.layout == ReaderLayout.Paged -> pagedSafeTop
        immersive -> 0.dp
        else -> baseTopInset
    }
    val bottomInset by animateDpAsState(
        targetValue = when {
            immersive && reading.settings.layout == ReaderLayout.Paged -> pagedSafeBottom
            immersive -> 0.dp
            else -> baseBottomInset
        },
        animationSpec = if (yomuAnimationsEnabled()) MaterialTheme.motionScheme.defaultSpatialSpec() else snap(),
        label = "readerBottomInset",
    )

    val background = Color(reading.settings.backgroundArgb)
    val onBackground = Color(reading.settings.textArgb)
    val mutedOnBackground = Color(reading.settings.colorPalette.secondaryTextArgb)

    // Page reveal: the new chapter fades in on an effects spring while travelling a short distance
    // in its reading direction on a spatial spring — rising into place going forward, settling down
    // going back — so a chapter change reads as continuous motion rather than a pop.
    val reveal = remember { Animatable(1f) }
    val revealShift = remember { Animatable(0f) }
    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val fastEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    LaunchedEffect(reading.renderState) {
        when (val render = reading.renderState) {
            is ReaderRenderState.Transitioning -> {
                reveal.snapTo(0f)
                revealShift.snapTo(if (render.forward) 1f else -1f)
            }

            !is ReaderRenderState.Ready -> {
                reveal.snapTo(0f)
                revealShift.snapTo(0f)
            }

            else -> if (!yomuAnimationsEnabled()) {
                reveal.snapTo(1f)
                revealShift.snapTo(0f)
            } else {
                coroutineScope {
                    launch { reveal.animateTo(1f, effectsSpec) }
                    launch { revealShift.animateTo(0f, spatialSpec) }
                }
            }
        }
    }
    val revealTravelPx = with(LocalDensity.current) { ChapterRevealTravel.toPx() }
    // Session creation changes which layer owns the opening scrim. Keep the indicator as movable
    // content so Compose transfers its animation state instead of disposing and restarting it.
    val openingContent = remember {
        movableContentOf<Modifier, Color> { modifier, color -> ReaderOpening(color, modifier) }
    }

    Box(
        modifier = Modifier
            .semantics { testTagsAsResourceId = true }
            .then(if (readyMarker != null) Modifier.testTag("reader-ready:${readyMarker.href}") else Modifier)
            .fillMaxSize()
            .background(background),
    ) {
        when {
            reading.failed -> ReaderFailure(onBack = onBack, onRetry = onRetryOpen)
            navigator != null -> {
                // Host the navigator even while loading so it can paint and fire its ready signal;
                // an opaque scrim below covers the half-rendered page until that first paint.
                ReaderNavigatorHost(
                    navigator = navigator,
                    backgroundArgb = reading.settings.backgroundArgb,
                    immersive = immersive,
                    modifier = Modifier
                        .fillMaxSize()
                        // The spatial spring may overshoot below zero; padding must not.
                        .padding(top = topInset, bottom = bottomInset.coerceAtLeast(0.dp))
                        .graphicsLayer {
                            alpha = reveal.value
                            translationY = revealShift.value * revealTravelPx
                        },
                )

                // Until the first page paints, cover the WebView with an opaque "Opening…" scrim.
                // The top bar is drawn after it, so Back stays usable during a slow open.
                if (reading.loading) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(background),
                    ) {
                        openingContent(Modifier.align(Alignment.Center), mutedOnBackground)
                    }
                }

                // During any chapter transition, hold an opaque cover (no message) until the new
                // chapter's layout CSS — chiefly the chapter-start top padding — has applied, so the
                // page is revealed already-padded instead of the padding popping in a few frames later.
                val coverChapterTransition = reading.renderState is ReaderRenderState.Transitioning
                if (coverChapterTransition) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(background),
                    )
                }

                // Top bar: chevron · chapter title · bookmark. Hidden with
                // the rest of the chrome in immersive mode; always shown otherwise.
                AnimatedVisibility(
                    visible = chromeShown,
                    enter = yomuChromeEnter(fromBottom = false),
                    exit = yomuChromeExit(toBottom = false),
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    ReaderTopBar(
                        // Keep the last known chapter title visible while the next resource is
                        // covered and styled; blanking it here exposes the generic "Reading" label.
                        chapter = reading.chapterTitle ?: "Reading",
                        background = background,
                        content = onBackground,
                        isBookmarked = reading.currentPageBookmarked,
                        onBack = onBack,
                        onToggleBookmark = onToggleBookmark,
                        onContentHeight = { topBarPx = it },
                    )
                }

                // Do not create reader surfaces during the initial open. Once created, keep them
                // composed through navigator reflows so an open sheet is not dismissed and rebuilt
                // every time an in-reader display preference changes.
                if (readerSurfacesAvailable) {
                    if (reading.settings.showFooter) {
                        // Keep the footer composed even while hidden so its measured height is always known —
                        // the controls bar can anchor above it on the first immersive reveal, and the EPUB
                        // page can reserve it in non-immersive mode. It moves with the same motion as the
                        // top bar and toolbar (yomuChromeEnter/Exit): an effects fade plus a spatial slide of
                        // a third of its height off its own edge, both read in the layer block only.
                        val animateFooter = yomuAnimationsEnabled()
                        val footerFadeSpec: AnimationSpec<Float> = when {
                            !animateFooter -> snap()
                            chromeShown -> effectsSpec
                            else -> fastEffectsSpec
                        }
                        val footerSlideSpec: AnimationSpec<Float> = if (animateFooter) spatialSpec else snap()
                        val footerAlpha by animateFloatAsState(
                            targetValue = if (chromeShown) 1f else 0f,
                            animationSpec = footerFadeSpec,
                            label = "readerFooterAlpha",
                        )
                        val footerSlidePx by animateFloatAsState(
                            targetValue = if (chromeShown) 0f else footerPx / 3f,
                            animationSpec = footerSlideSpec,
                            label = "readerFooterSlide",
                        )
                        ReaderFooter(
                            progressPercent = reading.progressPercent,
                            chapterPagesLeft = reading.chapterPagesLeft,
                            chapterProgression = reading.chapterProgression,
                            settings = reading.settings,
                            onContentHeight = { footerPx = it },
                            showDeviceStatus = !systemBarsShown,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .graphicsLayer {
                                    alpha = footerAlpha.coerceIn(0f, 1f)
                                    translationY = footerSlidePx
                                },
                        )
                    }

                    // Extra-dim scrim over the whole reading surface (content + chrome) for going darker
                    // than the device minimum. Decorative only — no pointerInput, so taps pass through.
                    if (effectiveDim > 0f) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Color.Black.copy(alpha = effectiveDim * ReaderSettings.MAX_DIM_ALPHA),
                                ),
                        )
                    }

                    // Bottom navigation bar, toggled by a centre tap. Keep it above the passive footer
                    // so both chrome surfaces remain visible when immersive controls are revealed.
                    ReaderChapterControlsBar(
                        visible = state.chapterControlsVisible,
                        bottomPadding = footerHeight,
                        footerVisible = reading.settings.showFooter,
                        background = background,
                        content = onBackground,
                        onBrowse = onOpenBrowse,
                        onSearch = onOpenSearch,
                        onDisplay = onOpenSheet,
                    )

                    ReaderControlsSheet(
                        visible = state.sheetVisible,
                        state = sheetState,
                        onDismiss = onCloseSheet,
                        onSelectChapter = onSelectChapter,
                        onNextChapter = onNextChapter,
                        onPreviousChapter = onPreviousChapter,
                        onUpdateSettings = onUpdateSettings,
                        onResetSettings = onResetSettings,
                        onOpenCustomTheme = onOpenCustomTheme,
                        onApplyCustomTheme = onApplyCustomTheme,
                        onPreviewBrightness = { brightness ->
                            brightnessPreview = brightness.coerceIn(0f, 1f)
                        },
                        onCommitBrightness = { brightness ->
                            val value = brightness.coerceIn(0f, 1f)
                            brightnessPreview = value
                            onUpdateSettings(reading.settings.copy(brightness = value))
                        },
                        onPreviewDim = { dim -> dimPreview = dim.coerceIn(0f, 1f) },
                        onCommitDim = { dim ->
                            val value = dim.coerceIn(0f, 1f)
                            dimPreview = value
                            onUpdateSettings(reading.settings.copy(dimLevel = value))
                        },
                    )

                    CustomThemeSheet(
                        visible = state.customSheetVisible,
                        settings = sheetState.experience.settings,
                        customThemes = state.customThemes,
                        onDismiss = onCloseCustomTheme,
                        onUpdateSettings = onUpdateSettings,
                        onSave = onSaveCustomTheme,
                        onApply = onApplyCustomTheme,
                        onDelete = onDeleteCustomTheme,
                    )

                    ReaderBrowseSheet(
                        tab = state.browseTab,
                        bookTitle = reading.title,
                        toc = reading.tableOfContents,
                        tocLoading = reading.tocLoading,
                        currentHref = reading.currentHref,
                        onJumpToLocator = onJumpToLocator,
                        bookmarks = reading.bookmarks,
                        onJumpToBookmark = onJumpToBookmark,
                        onDeleteBookmark = onDeleteBookmarkById,
                        highlights = reading.highlights,
                        onJumpToHighlight = onJumpToHighlight,
                        onDeleteHighlight = onDeleteHighlightById,
                        onSelectTab = onSelectBrowseTab,
                        onDismiss = onCloseBrowse,
                    )

                    ReaderSearchSheet(
                        visible = state.searchSheetVisible,
                        searchQuery = reading.searchQuery,
                        searchResults = reading.searchResults,
                        searchInProgress = reading.searchInProgress,
                        searchError = reading.searchError,
                        searchPerformed = reading.searchPerformed,
                        onSearchQueryChange = onSearchQueryChange,
                        onSubmitSearch = onSubmitSearch,
                        onJumpToSearchResult = onJumpToSearchResult,
                        onDismiss = onCloseSearch,
                    )

                    WordLookupSheet(
                        state = reading.lookup,
                        onDismiss = onCloseLookup,
                        onPronounce = onPronounce,
                        onLookUpWord = onLookUpWord,
                        onBack = onLookupBack,
                        onRetry = onRetryLookup,
                    )

                    FootnoteSheet(html = reading.footnoteHtml, onDismiss = onCloseFootnote)

                    HighlightEditSheet(
                        highlight = reading.editingHighlight,
                        onSelectColor = onSetHighlightColor,
                        onDelete = onDeleteHighlight,
                        onDismiss = onCloseEditHighlight,
                    )
                }
            }

            else -> openingContent(Modifier.align(Alignment.Center), mutedOnBackground)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** The opening state: the shared morphing indicator, captioned in the reading theme's muted ink. */
@Composable
private fun ReaderOpening(captionColor: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(YomuTheme.space.sm),
    ) {
        YomuLoadingIndicator()
        Text(text = "Opening…", color = captionColor, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * The book could not be opened. The page behind is in the reading theme, so the message sits on
 * its own app-themed surface to keep the shared empty-state colours legible on any page colour.
 */
@Composable
private fun BoxScope.ReaderFailure(
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .align(Alignment.Center)
            .padding(YomuTheme.space.lg)
            .widthIn(max = ReaderFailureMaxWidth),
        shape = YomuShapeTokens.hero,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        YomuEmptyState(
            icon = Icons.Rounded.ErrorOutline,
            title = "This book couldn't be opened",
            body = "The file may have moved or be damaged. Try again, or head back to your library.",
            shape = YomuBadgeShape.Burst,
            tone = YomuBadgeTone.Error,
            actions = {
                YomuButton(text = "Try again", onClick = onRetry)
                YomuButton(
                    text = "Back to library",
                    onClick = onBack,
                    emphasis = YomuButtonEmphasis.Secondary,
                )
            },
        )
    }
}

private val ReaderFailureMaxWidth = 420.dp

/** How far a newly revealed chapter travels into place. */
private val ChapterRevealTravel = 40.dp

private fun applyReaderCutoutMode(window: android.view.Window) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val lp = window.attributes
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        window.attributes = lp
    }
}

/** Full-screen reading: both system bars hidden, revealed transiently by a swipe. */
// The legacy layout flags are deliberate: they keep the WebView laid out under the bars on every API
// level, which the insets controller alone does not guarantee.
@Suppress("DEPRECATION")
private fun hideReaderSystemBars(window: android.view.Window, view: View) {
    val controller = WindowCompat.getInsetsController(window, view)
    applyReaderCutoutMode(window)
    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    window.decorView.systemUiVisibility =
        View.SYSTEM_UI_FLAG_IMMERSIVE or
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
        View.SYSTEM_UI_FLAG_FULLSCREEN
    controller.hide(WindowInsetsCompat.Type.systemBars())
}

/**
 * Menu open: bars visible so one back gesture exits. The layout flags stay, so the page is still laid
 * out edge-to-edge and the bars draw over it rather than reflowing the text.
 */
@Suppress("DEPRECATION")
private fun showReaderSystemBars(window: android.view.Window, view: View) {
    val controller = WindowCompat.getInsetsController(window, view)
    applyReaderCutoutMode(window)
    window.decorView.systemUiVisibility =
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    controller.show(WindowInsetsCompat.Type.systemBars())
}
