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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.itexpert120.yomu.core.designsystem.YomuButton
import com.itexpert120.yomu.core.designsystem.YomuMotion
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.yomuAnimationsEnabled
import com.itexpert120.yomu.core.designsystem.yomuChromeEnter
import com.itexpert120.yomu.core.designsystem.yomuChromeExit
import com.itexpert120.yomu.core.model.CustomReaderTheme
import com.itexpert120.yomu.core.model.ReaderLayout
import com.itexpert120.yomu.core.model.ReaderSettings
import com.itexpert120.yomu.core.reader.ReaderNavigator
import com.itexpert120.yomu.core.reader.ReaderRenderState

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
) {
    if (state.experience.settingsError != null) {
        SaveFailureNotice(state.experience.settingsError, onRetrySettings)
    } else {
        SaveFailureNotice(state.experience.annotationError, onRetryAnnotations)
    }
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
            val controller = WindowCompat.getInsetsController(window, view)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val lp = window.attributes
                lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                window.attributes = lp
            }
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_IMMERSIVE or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN
            controller.hide(WindowInsetsCompat.Type.systemBars())
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
            if (insets.isVisible(WindowInsetsCompat.Type.systemBars())) hideSystemBars()
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
    // In non-immersive mode Readium receives system-bar insets and reserves the status strip inside
    // the WebView. Yomu only reserves the visible controls row, avoiding a double top gap.
    val fullTop = with(density) { topBarPx.toDp() }
    val statusTop = WindowInsets.statusBarsIgnoringVisibility
        .asPaddingValues()
        .calculateTopPadding()
    val pagedSafeTop = WindowInsets.displayCutout
        .union(WindowInsets.statusBarsIgnoringVisibility)
        .asPaddingValues()
        .calculateTopPadding()
    val pagedSafeBottom = WindowInsets.navigationBarsIgnoringVisibility
        .asPaddingValues()
        .calculateBottomPadding()
    val baseTopInset = (fullTop - statusTop).coerceAtLeast(0.dp)
    val footerHeight = if (reading.settings.showFooter) with(density) { footerPx.toDp() } else 0.dp
    val scrollEndPadding =
        if (reading.settings.layout == ReaderLayout.Scroll && reading.settings.showFooter) {
            // Just enough breathing room so the last line doesn't sit tight under the footer; the footer
            // height itself is already reserved below.
            4.dp
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
    val pageReady = reading.renderState is ReaderRenderState.Ready
    val readerSurfacesAvailable = reading.renderState !is ReaderRenderState.Opening
    val chromeShown = !pageReady || !immersive || state.chapterControlsVisible
    val topInset by animateDpAsState(
        targetValue = when {
            immersive && reading.settings.layout == ReaderLayout.Paged -> pagedSafeTop
            immersive -> 0.dp
            else -> baseTopInset
        },
        label = "readerTopInset",
    )
    val bottomInset by animateDpAsState(
        targetValue = when {
            immersive && reading.settings.layout == ReaderLayout.Paged -> pagedSafeBottom
            immersive -> 0.dp
            else -> baseBottomInset
        },
        label = "readerBottomInset",
    )

    val background = Color(reading.settings.backgroundArgb)
    val onBackground = Color(reading.settings.textArgb)

    val reveal = remember { Animatable(1f) }
    LaunchedEffect(reading.renderState) {
        when {
            reading.renderState !is ReaderRenderState.Ready -> reveal.snapTo(0f)
            !yomuAnimationsEnabled() -> reveal.snapTo(1f)
            else -> reveal.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 120, easing = YomuMotion.EmphasizedDecel),
            )
        }
    }
    // Session creation changes which layer owns the opening scrim. Keep the indicator as movable
    // content so Compose transfers its animation state instead of disposing and restarting it.
    val openingContent = remember {
        movableContentOf<Modifier> { modifier -> ReaderOpening(modifier) }
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
                        .padding(top = topInset, bottom = bottomInset)
                        .graphicsLayer {
                            alpha = reveal.value
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
                        openingContent(Modifier.align(Alignment.Center))
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
                        // page can reserve it in non-immersive mode. Animate alpha + a slide off its own edge.
                        val footerAlpha by animateFloatAsState(
                            targetValue = if (chromeShown) 1f else 0f,
                            label = "readerFooterAlpha",
                        )
                        val footerSlidePx by animateFloatAsState(
                            targetValue = if (chromeShown) 0f else footerPx / 3f,
                            animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
                            label = "readerFooterSlide",
                        )
                        ReaderFooter(
                            progressPercent = reading.progressPercent,
                            chapterPagesLeft = reading.chapterPagesLeft,
                            chapterProgression = reading.chapterProgression,
                            settings = reading.settings,
                            onContentHeight = { footerPx = it },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .graphicsLayer {
                                    alpha = footerAlpha
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

            else -> openingContent(Modifier.align(Alignment.Center))
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun ReaderOpening(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ReaderOpeningSpinner()
        Text(text = "Opening…", color = YomuTheme.colors.textMuted, style = YomuTheme.type.body)
    }
}

@Composable
private fun ReaderOpeningSpinner() {
    val rotation by rememberInfiniteTransition(label = "readerOpeningTransition").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
        ),
        label = "readerOpeningRotation",
    )
    val color = YomuTheme.colors.accent
    Canvas(
        modifier = Modifier
            .size(24.dp)
            .graphicsLayer { rotationZ = rotation },
    ) {
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 105f,
            useCenter = false,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.ReaderFailure(
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "This book couldn't be opened.",
            color = YomuTheme.colors.textPrimary,
            style = YomuTheme.type.body,
        )
        YomuButton(text = "Try again", onClick = onRetry)
        YomuButton(text = "Back to library", onClick = onBack)
    }
}
