package com.itexpert120.yomu.feature.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.computedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.yomuAnimationsEnabled
import com.itexpert120.yomu.core.designsystem.yomuChromeEnter
import com.itexpert120.yomu.core.designsystem.yomuChromeExit
import com.itexpert120.yomu.core.model.ReaderLayout
import com.itexpert120.yomu.core.model.ReaderSettings
import com.itexpert120.yomu.core.reader.ReaderNavigator
import com.itexpert120.yomu.core.reader.ReaderRenderState
import com.itexpert120.yomu.feature.annotations.AnnotationTransferAction
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Intentionally colours the system bars to the reading theme via the (now-deprecated) window
// setters — the only way to keep the bars seamless with the page without a system scrim.
@Suppress("DEPRECATION")
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReaderScreen(
    state: ReaderUiState,
    onAction: (ReaderAction) -> Unit,
) {
    // One notice at a time, in priority order. Each stays composed (with a null value) so it can
    // play its exit instead of being cut when another branch takes over.
    val settingsError = state.experience.settingsError
    val annotationError = state.experience.annotationError.takeIf { settingsError == null }
    SaveFailureNotice(settingsError) { onAction(ReaderAction.RetrySettings) }
    SaveFailureNotice(annotationError) { onAction(ReaderAction.RetryAnnotations) }
    val undo = state.experience.undo.takeIf { settingsError == null && annotationError == null }
    AnnotationUndoNotice(
        undo = undo,
        onUndo = { onAction(ReaderAction.UndoDelete(it)) },
        onTimeout = { onAction(ReaderAction.DismissUndo(it)) },
    )
    val transferNotice = state.annotationTransfer.notice.takeIf { settingsError == null && annotationError == null && undo == null }
    AnnotationTransferNotice(
        notice = transferNotice,
        onTimeout = { onAction(ReaderAction.Annotations(AnnotationTransferAction.DismissNotice(it))) },
    )
    SyncOfferNotice(
        offer = state.experience.syncOffer.takeIf { settingsError == null && annotationError == null && undo == null && transferNotice == null },
        onJump = { onAction(ReaderAction.AcceptSyncOffer) },
        onDismiss = { onAction(ReaderAction.DismissSyncOffer) },
    )
    val view = LocalView.current
    val reading = state.experience
    val readyMarker = (reading.renderState as? ReaderRenderState.Ready)?.takeUnless { reading.loading }
    val navigator: ReaderNavigator? = reading.navigator
    // Brightness / extra-dim slider values. The drag values change every frame, so they are read only
    // in effects and the draw phase — a drag never recomposes the screen. The committed values bridge
    // the gap between release and the settings write-back so the sheet doesn't snap back meanwhile.
    var brightnessDrag by remember { mutableStateOf<Float?>(null) }
    var dimDrag by remember { mutableStateOf<Float?>(null) }
    var brightnessCommitted by remember { mutableStateOf<Float?>(null) }
    var dimCommitted by remember { mutableStateOf<Float?>(null) }
    val currentSettings by rememberUpdatedState(reading.settings)

    // Keep the display awake while reading, per the user's setting; released when leaving the reader.
    DisposableEffect(reading.settings.keepScreenOn) {
        val previous = view.keepScreenOn
        view.keepScreenOn = reading.settings.keepScreenOn
        onDispose { view.keepScreenOn = previous }
    }

    // The system bars show while the reader menu is up, so one back gesture (or the status bar) leaves
    // the reader instead of the first swipe only revealing hidden bars. Otherwise reading is full-screen.
    val systemBarsShown = state.chrome.controlsVisible
    val currentSystemBarsShown by rememberUpdatedState(systemBarsShown)
    // Navigation holds the window takeover back until the reader's entrance has settled, so the
    // page it opened over never relayouts (bars hiding) while it is still on screen.
    val windowReady = LocalReaderWindowReady.current
    // Hide both system bars for full-screen reading. Android re-shows the bars whenever the window
    // loses and regains focus (returning from background, multi-window, etc.), so this must be
    // re-applied on every ON_RESUME — not just once. A one-shot effect here is why content used to
    // reappear below the status bar after the app came back from the background.
    DisposableEffect(view, windowReady) {
        if (!windowReady) return@DisposableEffect onDispose { }
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
    LaunchedEffect(systemBarsShown, windowReady) {
        if (!windowReady) return@LaunchedEffect
        val window = view.context.findActivity()?.window ?: return@LaunchedEffect
        if (systemBarsShown) showReaderSystemBars(window, view) else hideReaderSystemBars(window, view)
    }
    // Colour the system bars to the reading background so the status area matches the page on every
    // Android version (on API 35+ the bar is transparent and the chrome backdrop shows through).
    LaunchedEffect(reading.settings.backgroundArgb, reading.settings.isLightBackground, windowReady) {
        if (!windowReady) return@LaunchedEffect
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
        brightnessCommitted,
    ) {
        val committed = brightnessCommitted
        if (reading.settings.useSystemBrightness) {
            brightnessCommitted = null
        } else if (committed != null && kotlin.math.abs(committed - reading.settings.brightness) < 0.001f) {
            brightnessCommitted = null
        }
    }

    LaunchedEffect(reading.settings.dimLevel, dimCommitted) {
        val committed = dimCommitted
        if (committed != null && kotlin.math.abs(committed - reading.settings.dimLevel) < 0.001f) {
            dimCommitted = null
        }
    }

    val sheetSettings = remember(reading.settings, brightnessCommitted, dimCommitted) {
        if (brightnessCommitted == null && dimCommitted == null) {
            reading.settings
        } else {
            reading.settings.copy(
                brightness = brightnessCommitted ?: reading.settings.brightness,
                dimLevel = (dimCommitted ?: reading.settings.dimLevel).coerceIn(0f, 1f),
            )
        }
    }

    // Drive the window screen brightness: defer to the system level, or pin it to the reader setting.
    // Collected from snapshot state so a slider drag updates the window without recomposing.
    LaunchedEffect(view) {
        val window = view.context.findActivity()?.window ?: return@LaunchedEffect
        snapshotFlow {
            val settings = currentSettings
            if (settings.useSystemBrightness) {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            } else {
                (brightnessDrag ?: brightnessCommitted ?: settings.brightness).coerceIn(0f, 1f)
            }
        }.collect { brightness ->
            val lp = window.attributes
            lp.screenBrightness = brightness
            window.attributes = lp
        }
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
    // A chapter transition keeps the chrome as it was; only the initial open forces it visible.
    // Treating transitions as "not ready" flashed the immersive top bar every chapter.
    val chromeForcedByLoad = reading.renderState is ReaderRenderState.Opening
    val readerSurfacesAvailable = reading.renderState !is ReaderRenderState.Opening
    val chromeShown = chromeForcedByLoad || !immersive || state.chrome.controlsVisible
    // Tabletop (a half-opened horizontal fold): the page and its bars live above the hinge and the
    // controls panel stays up on the half lying flat. 0.dp everywhere else.
    val belowHinge = rememberTabletopBelowHinge()
    val tabletop = belowHinge > 0.dp
    val topInset = when {
        immersive && reading.settings.layout == ReaderLayout.Paged -> pagedSafeTop
        immersive -> 0.dp
        else -> baseTopInset
    }
    val bottomInset by animateDpAsState(
        targetValue = when {
            // Above a hinge there is no navigation bar to keep clear of.
            immersive && reading.settings.layout == ReaderLayout.Paged -> if (tabletop) 0.dp else pagedSafeBottom
            immersive -> 0.dp
            // Paged with no footer: still keep the last line clear of the gesture area.
            reading.settings.layout == ReaderLayout.Paged && !reading.settings.showFooter -> if (tabletop) 0.dp else pagedSafeBottom
            else -> baseBottomInset
        },
        animationSpec = if (yomuAnimationsEnabled()) MaterialTheme.motionScheme.defaultSpatialSpec() else snap(),
        label = "readerBottomInset",
    )

    val chromeColors = remember(reading.settings) { reading.settings.chromeColors() }
    val background = chromeColors.page

    // Page reveal: the new chapter fades in on an effects spring while travelling a short distance
    // in its reading direction on a spatial spring — rising into place going forward, settling down
    // going back — so a chapter change reads as continuous motion rather than a pop.
    val reveal = remember { Animatable(1f) }
    val revealShift = remember { Animatable(0f) }
    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val fastEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val openingRise by rememberUpdatedState(LocalReaderOpeningRise.current)
    LaunchedEffect(reading.renderState) {
        when (val render = reading.renderState) {
            is ReaderRenderState.Transitioning -> {
                reveal.snapTo(0f)
                revealShift.snapTo(if (render.forward) 1f else -1f)
            }

            !is ReaderRenderState.Ready -> {
                reveal.snapTo(0f)
                // The first page rises into place only when opened from a rising book sheet.
                revealShift.snapTo(if (render is ReaderRenderState.Opening) openingRise / ChapterRevealTravel else 0f)
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
        movableContentOf<Modifier, ReaderChromeColors> { modifier, colors -> ReaderOpening(colors, modifier) }
    }

    Box(
        modifier = Modifier
            .semantics { testTagsAsResourceId = true }
            .then(if (readyMarker != null) Modifier.testTag("reader-ready:${readyMarker.href}") else Modifier)
            .fillMaxSize()
            .background(background),
    ) {
        when {
            reading.failed -> ReaderFailure(
                colors = chromeColors,
                onBack = { onAction(ReaderAction.Back) },
                onRetry = { onAction(ReaderAction.RetryOpen) },
            )
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
                        .padding(top = topInset, bottom = bottomInset.coerceAtLeast(0.dp) + belowHinge)
                        .graphicsLayer {
                            alpha = reveal.value
                            translationY = revealShift.value * revealTravelPx
                        },
                )

                // Until the first page paints, cover the WebView with an opaque "Opening…" scrim.
                // The top bar is drawn after it, so Back stays usable during a slow open. It fades
                // away once the page is ready, so a slow book's first page comes in instead of popping.
                AnimatedVisibility(
                    visible = reading.loading,
                    modifier = Modifier.matchParentSize(),
                    enter = EnterTransition.None,
                    exit = if (yomuAnimationsEnabled()) fadeOut(MaterialTheme.motionScheme.slowEffectsSpec()) else ExitTransition.None,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(background),
                    ) {
                        openingContent(Modifier.align(Alignment.Center), chromeColors)
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
                        bookTitle = reading.title,
                        colors = chromeColors,
                        isBookmarked = reading.currentPageBookmarked,
                        onBack = { onAction(ReaderAction.Back) },
                        onToggleBookmark = { onAction(ReaderAction.ToggleBookmark) },
                        onContentHeight = { topBarPx = it },
                    )
                }

                // Do not create reader surfaces during the initial open. Once created, keep them
                // composed through navigator reflows so an open sheet is not dismissed and rebuilt
                // every time an in-reader display preference changes.
                // The footer is composed from the start (invisible until the page is ready) so its
                // height is reserved before the book's first layout. Measured only after the open, it
                // shrank the page right after it appeared and the book re-laid out under the reader.
                run {
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
                            pageLabel = reading.pageLabel,
                            minutesLeftInChapter = reading.minutesLeftInChapter,
                            minutesLeftInBook = reading.minutesLeftInBook,
                            showDeviceStatus = !systemBarsShown,
                            deviceStatusActive = chromeShown,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = belowHinge)
                                .graphicsLayer {
                                    alpha = if (readerSurfacesAvailable) footerAlpha.coerceIn(0f, 1f) else 0f
                                    translationY = footerSlidePx
                                },
                        )
                    }
                }
                if (readerSurfacesAvailable) {
                    // Extra-dim scrim over the whole reading surface (content + chrome) for going darker
                    // than the device minimum. Decorative only — no pointerInput, so taps pass through.
                    // The level is read while drawing, so dragging the slider only redraws this layer.
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .drawBehind {
                                val dim = (dimDrag ?: dimCommitted ?: currentSettings.dimLevel).coerceIn(0f, 1f)
                                if (dim > 0f) {
                                    drawRect(Color.Black.copy(alpha = dim * ReaderSettings.MAX_DIM_ALPHA))
                                }
                            },
                    )

                    // The menu's controls panel, toggled by a centre tap. It carries the chapter
                    // remaining and book progress itself, so it covers the passive footer while up.
                    // In tabletop it stays up on the flat half, below the hinge.
                    val controlsPanel: @Composable BoxScope.(Boolean) -> Unit = { panelVisible ->
                        ReaderControlsPanel(
                            visible = panelVisible,
                            colors = chromeColors,
                            // "6 min left in chapter · pages 112–113", as the boards have it;
                            // pages or percent of the chapter left until there's an estimate.
                            remainingLabel = controlsCaption(
                                timeLeft = ReadingTime.chapterLabel(reading.minutesLeftInChapter),
                                pageLabel = reading.pageLabel,
                                fallback = chapterRemainingLabel(
                                    reading.settings.layout,
                                    reading.chapterPagesLeft,
                                    reading.chapterProgression,
                                ),
                            ),
                            progressPercent = reading.progressPercent,
                            chapterProgression = reading.chapterProgression,
                            onSeekChapter = { onAction(ReaderAction.SeekInChapter(it)) },
                            onContents = { onAction(ReaderAction.OpenBrowse) },
                            onSearch = { onAction(ReaderAction.OpenSearch) },
                            onMarks = {
                                onAction(ReaderAction.OpenBrowse)
                                onAction(ReaderAction.SelectBrowseTab(BrowseTab.Bookmarks))
                            },
                            onDisplay = { onAction(ReaderAction.OpenDisplay) },
                        )
                    }
                    if (tabletop) {
                        ReaderBelowHinge(belowHinge) { controlsPanel(true) }
                    } else {
                        controlsPanel(state.chrome.controlsVisible)
                    }

                    // "Back to p. 112" after a jump: under the top bar while it shows, else just
                    // below the status area.
                    ReaderJumpBackChip(
                        label = reading.jumpBack,
                        colors = chromeColors,
                        onClick = { onAction(ReaderAction.JumpBack) },
                        onDismiss = { onAction(ReaderAction.DismissJumpBack) },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = (if (chromeShown) fullTop else pagedSafeTop) + 12.dp, start = 24.dp, end = 24.dp),
                    )

                    ReaderControlsSheetWithDimPreview(
                        visible = state.chrome.displaySheetVisible,
                        state = state,
                        settings = sheetSettings,
                        dimDrag = { dimDrag },
                        onAction = onAction,
                        onPreviewBrightness = { brightness ->
                            brightnessDrag = brightness.coerceIn(0f, 1f)
                        },
                        onCommitBrightness = { brightness ->
                            val value = brightness.coerceIn(0f, 1f)
                            brightnessCommitted = value
                            brightnessDrag = null
                            onAction(ReaderAction.UpdateSettings(currentSettings.copy(brightness = value)))
                        },
                        onPreviewDim = { dim -> dimDrag = dim.coerceIn(0f, 1f) },
                        onCommitDim = { dim ->
                            val value = dim.coerceIn(0f, 1f)
                            dimCommitted = value
                            dimDrag = null
                            onAction(ReaderAction.UpdateSettings(currentSettings.copy(dimLevel = value)))
                        },
                    )

                    CustomThemeSheet(
                        visible = state.chrome.customThemeSheetVisible,
                        settings = sheetSettings,
                        customThemes = state.customThemes,
                        onDismiss = { onAction(ReaderAction.CloseCustomTheme) },
                        onUpdateSettings = { onAction(ReaderAction.UpdateSettings(it)) },
                        onSave = { onAction(ReaderAction.SaveCustomTheme(it)) },
                        onApply = { onAction(ReaderAction.ApplyCustomTheme(it)) },
                        onDelete = { onAction(ReaderAction.DeleteCustomTheme(it)) },
                    )

                    ReaderBrowseSheet(
                        tab = state.chrome.browseTab,
                        currentPercent = reading.progressPercent,
                        chapterProgress = reading.chapterProgression,
                        chapterReads = state.chapterReads,
                        toc = reading.tableOfContents,
                        tocLoading = reading.tocLoading,
                        currentHref = reading.currentHref,
                        onJumpToLocator = { onAction(ReaderAction.JumpToTocEntry(it)) },
                        bookmarks = reading.bookmarks,
                        onJumpToBookmark = { onAction(ReaderAction.JumpToBookmark(it)) },
                        onDeleteBookmark = { onAction(ReaderAction.DeleteBookmark(it)) },
                        highlights = reading.highlights,
                        onJumpToHighlight = { onAction(ReaderAction.JumpToHighlight(it)) },
                        onDeleteHighlight = { onAction(ReaderAction.DeleteHighlight(it)) },
                        pages = reading.pageList,
                        onGoToPage = { onAction(ReaderAction.GoToPage(it)) },
                        onSelectTab = { onAction(ReaderAction.SelectBrowseTab(it)) },
                        onDismiss = { onAction(ReaderAction.CloseBrowse) },
                        calibreAvailable = state.annotationTransfer.calibreAvailable,
                        onAnnotationAction = { onAction(ReaderAction.Annotations(it)) },
                    )

                    ReaderSearchSheet(
                        visible = state.chrome.searchSheetVisible,
                        searchQuery = reading.searchQuery,
                        searchResults = reading.searchResults,
                        searchInProgress = reading.searchInProgress,
                        searchError = reading.searchError,
                        searchPerformed = reading.searchPerformed,
                        onSearchQueryChange = { onAction(ReaderAction.ChangeSearchQuery(it)) },
                        onSubmitSearch = { onAction(ReaderAction.SubmitSearch) },
                        onJumpToSearchResult = { onAction(ReaderAction.JumpToSearchResult(it)) },
                        onDismiss = { onAction(ReaderAction.CloseSearch) },
                    )

                    WordLookupSheet(
                        state = reading.lookup,
                        onDismiss = { onAction(ReaderAction.CloseLookup) },
                        actions = remember(onAction) { wordLookupActions(onAction) },
                    )

                    FootnoteSheet(html = reading.footnoteHtml, onDismiss = { onAction(ReaderAction.CloseFootnote) })

                    ReaderImageViewer(
                        image = reading.viewedImage,
                        message = reading.imageMessage,
                        onSave = { onAction(ReaderAction.SaveImage) },
                        onShare = { onAction(ReaderAction.ShareImage) },
                        onClose = { onAction(ReaderAction.CloseImageViewer) },
                    )

                    HighlightEditSheet(
                        highlight = reading.editingHighlight,
                        onSelectColor = { onAction(ReaderAction.SetHighlightColor(it)) },
                        onSelectStyle = { onAction(ReaderAction.SetHighlightStyle(it)) },
                        onNoteChange = { id, note -> onAction(ReaderAction.SetHighlightNote(id, note)) },
                        onCopy = { onAction(ReaderAction.CopyEditingHighlight) },
                        onShare = { onAction(ReaderAction.ShareEditingHighlight) },
                        onDelete = { onAction(ReaderAction.DeleteEditingHighlight) },
                        onDismiss = { onAction(ReaderAction.CloseHighlightEditor) },
                    )
                }
            }

            else -> openingContent(Modifier.align(Alignment.Center), chromeColors)
        }
    }
}

/**
 * The controls sheet, fed the live extra-dim drag value at the precision its "N%" chip shows: it
 * recomposes when that whole percent changes rather than on every drag frame, and the screen above
 * it not at all. Brightness needs no live value here — the slider tracks its own drag position.
 */
@Composable
private fun ReaderControlsSheetWithDimPreview(
    visible: Boolean,
    state: ReaderUiState,
    settings: ReaderSettings,
    dimDrag: () -> Float?,
    onAction: (ReaderAction) -> Unit,
    onPreviewBrightness: (Float) -> Unit,
    onCommitBrightness: (Float) -> Unit,
    onPreviewDim: (Float) -> Unit,
    onCommitDim: (Float) -> Unit,
) {
    val dimPercent by remember { computedStateOf { dimDrag()?.let { (it * 100).roundToInt() } } }
    val percent = dimPercent
    val sheetState = remember(state, settings, percent) {
        // The exact value (not the rounded percent) so the slider, which re-seeds from it, doesn't jump.
        val liveDim = if (percent != null) Snapshot.withoutReadObservation { dimDrag() } else null
        val sheetSettings = if (liveDim != null) settings.copy(dimLevel = liveDim) else settings
        if (sheetSettings === state.experience.settings) {
            state
        } else {
            state.copy(experience = state.experience.copy(settings = sheetSettings))
        }
    }
    ReaderControlsSheet(
        visible = visible,
        state = sheetState,
        onDismiss = { onAction(ReaderAction.CloseDisplay) },
        onOpenFontLibrary = { onAction(ReaderAction.OpenFontLibrary) },
        onUpdateSettings = { onAction(ReaderAction.UpdateSettings(it)) },
        onResetSettings = { onAction(ReaderAction.ResetSettings) },
        onOpenCustomTheme = { onAction(ReaderAction.OpenCustomTheme) },
        onApplyCustomTheme = { onAction(ReaderAction.ApplyCustomTheme(it)) },
        onPreviewBrightness = onPreviewBrightness,
        onCommitBrightness = onCommitBrightness,
        onPreviewDim = onPreviewDim,
        onCommitDim = onCommitDim,
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** The opening state: Stack's 2dp line over "Opening…", in the reading theme's own ink. */
@Composable
private fun ReaderOpening(colors: ReaderChromeColors, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ReaderLoadingLine(color = colors.ink, track = colors.track, modifier = Modifier.width(120.dp))
        Text(text = "Opening…", color = colors.muted, style = StackType.Label.copy(fontWeight = FontWeight.Normal))
    }
}

/**
 * The book could not be opened: Stack's empty state drawn straight on the page, in the reading
 * theme's colours so it stays legible on any page.
 */
@Composable
private fun BoxScope.ReaderFailure(
    colors: ReaderChromeColors,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .padding(horizontal = YomuTheme.space.xl)
            .widthIn(max = ReaderFailureMaxWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = StackIcons.MenuBook,
            contentDescription = null,
            tint = colors.muted,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = "This book couldn't be opened",
            color = colors.ink,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 18.dp).semantics { heading() },
        )
        Text(
            text = "The file may be damaged or use a format Open Reader can't read. Your place and notes are kept.",
            color = colors.muted,
            style = StackType.Body,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        ReaderPillButton(
            text = "Try again",
            onClick = onRetry,
            ink = colors.ink,
            surface = colors.page,
            height = 52.dp,
            modifier = Modifier.padding(top = 24.dp).widthIn(min = 220.dp),
        )
        ReaderPillButton(
            text = "Back to library",
            onClick = onBack,
            style = ReaderPillStyle.Secondary,
            ink = colors.ink,
            surface = colors.page,
            border = colors.track,
            height = 52.dp,
            modifier = Modifier.padding(top = 8.dp).widthIn(min = 220.dp),
        )
    }
}

private val ReaderFailureMaxWidth = 420.dp

/** How far a newly revealed chapter travels into place. */
private val ChapterRevealTravel = 40.dp

/**
 * How far the book's first page rises into place as the reader opens: set by navigation while
 * the book sheet turns into the reader (Stack's Resume motion); 0 elsewhere, where it fades in.
 */
val LocalReaderOpeningRise = compositionLocalOf { 0.dp }

/**
 * Whether the reader may take the window over (hide and colour the system bars, cutout mode):
 * false while navigation is still bringing the reader in over the page it opened from.
 */
val LocalReaderWindowReady = compositionLocalOf { true }

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
