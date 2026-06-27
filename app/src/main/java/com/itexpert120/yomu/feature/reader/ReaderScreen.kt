package com.itexpert120.yomu.feature.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.yomuChromeBlur
import com.itexpert120.yomu.core.designsystem.yomuChromeEnter
import com.itexpert120.yomu.core.designsystem.yomuChromeExit
import com.itexpert120.yomu.core.model.CustomReaderTheme
import com.itexpert120.yomu.core.model.ReaderLayout
import com.itexpert120.yomu.core.model.ReaderSettings
import com.itexpert120.yomu.core.reader.ReaderSession

// Intentionally colours the system bars to the reading theme via the (now-deprecated) window
// setters — the only way to keep the bars seamless with the page without a system scrim.
@Suppress("DEPRECATION")
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReaderScreen(
    state: ReaderUiState,
    session: ReaderSession?,
    onBack: () -> Unit,
    onOpenSheet: () -> Unit,
    onCloseSheet: () -> Unit,
    onSeek: (Double) -> Unit,
    onNextChapter: () -> Unit,
    onPreviousChapter: () -> Unit,
    onUpdateSettings: (ReaderSettings) -> Unit,
    onResetSettings: () -> Unit,
    onOpenCustomTheme: () -> Unit,
    onCloseCustomTheme: () -> Unit,
    onApplyCustomTheme: (CustomReaderTheme) -> Unit,
    onSaveCustomTheme: (String) -> Unit,
    onDeleteCustomTheme: (String) -> Unit,
    // Browse sheet (Contents / Bookmarks / Highlights / Search).
    onOpenBrowse: () -> Unit,
    onSelectBrowseTab: (BrowseTab) -> Unit,
    onCloseBrowse: () -> Unit,
    onJumpToLocator: (String) -> Unit,
    onJumpToBookmark: (String) -> Unit,
    onJumpToHighlight: (String) -> Unit,
    onJumpToSearchResult: (String) -> Unit,
    onDeleteBookmarkById: (String) -> Unit,
    onDeleteHighlightById: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    // More overflow sheet.
    onOpenMore: () -> Unit,
    onCloseMore: () -> Unit,
    onChapterStart: () -> Unit,
    onChapterEnd: () -> Unit,
    // Word lookup + footnote popups.
    onCloseLookup: () -> Unit,
    onLookUpWord: (String) -> Unit,
    onLookupBack: () -> Unit,
    onPronounce: (String) -> Unit,
    onCloseFootnote: () -> Unit,
    // Highlight edit popup + bookmark toggle.
    onDeleteHighlight: () -> Unit,
    onSetHighlightColor: (Int) -> Unit,
    onCloseEditHighlight: () -> Unit,
    onToggleBookmark: () -> Unit,
    onReadingResumed: () -> Unit,
    onReadingPaused: () -> Unit,
) {
    val view = LocalView.current
    var brightnessPreview by remember { mutableStateOf<Float?>(null) }
    var dimPreview by remember { mutableStateOf<Float?>(null) }

    // Keep the display awake while reading, per the user's setting; released when leaving the reader.
    DisposableEffect(state.settings.keepScreenOn) {
        view.keepScreenOn = state.settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
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
    // Keep the system bars transparent with no enforced scrim, so the reader chrome defines their
    // look. The top bar is always present, so the status area never sits over content.
    DisposableEffect(Unit) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val prevStatus = window?.statusBarColor
        val prevNav = window?.navigationBarColor
        // Capture the app's bar-icon appearance so leaving the reader restores legible icons for the
        // app theme (the reader flips these to match the reading page).
        val prevLightStatus = controller?.isAppearanceLightStatusBars
        val prevLightNav = controller?.isAppearanceLightNavigationBars
        val prevStatusContrast = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window?.isStatusBarContrastEnforced
        } else {
            null
        }
        val prevNavContrast = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window?.isNavigationBarContrastEnforced
        } else {
            null
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window?.isStatusBarContrastEnforced = false
            window?.isNavigationBarContrastEnforced = false
        }
        // Full-screen reading: hide both system bars (swipe to reveal). The footer already shows the
        // time + battery, so the status bar is redundant; the permanent top bar shows the chapter.
        controller?.let {
            it.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            it.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            window?.let {
                prevStatus?.let { c -> it.statusBarColor = c }
                prevNav?.let { c -> it.navigationBarColor = c }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    prevStatusContrast?.let { enforced ->
                        it.isStatusBarContrastEnforced = enforced
                    }
                    prevNavContrast?.let { enforced ->
                        it.isNavigationBarContrastEnforced = enforced
                    }
                }
            }
            controller?.let { c ->
                prevLightStatus?.let { c.isAppearanceLightStatusBars = it }
                prevLightNav?.let { c.isAppearanceLightNavigationBars = it }
                c.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    // Colour the system bars to the reading background so the status area matches the page on every
    // Android version (on API 35+ the bar is transparent and the chrome backdrop shows through).
    LaunchedEffect(state.settings.backgroundArgb, state.settings.isLightBackground) {
        val window = view.context.findActivity()?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        val barColor = Color(state.settings.backgroundArgb).toArgb()
        window.statusBarColor = barColor
        window.navigationBarColor = barColor
        controller.isAppearanceLightStatusBars = state.settings.isLightBackground
        controller.isAppearanceLightNavigationBars = state.settings.isLightBackground
    }
    LaunchedEffect(
        state.settings.useSystemBrightness,
        state.settings.brightness,
        brightnessPreview
    ) {
        val preview = brightnessPreview
        if (state.settings.useSystemBrightness) {
            brightnessPreview = null
        } else if (preview != null && kotlin.math.abs(preview - state.settings.brightness) < 0.001f) {
            brightnessPreview = null
        }
    }

    LaunchedEffect(state.settings.dimLevel, dimPreview) {
        val preview = dimPreview
        if (preview != null && kotlin.math.abs(preview - state.settings.dimLevel) < 0.001f) {
            dimPreview = null
        }
    }

    val effectiveBrightness = brightnessPreview ?: state.settings.brightness
    val effectiveDim = (dimPreview ?: state.settings.dimLevel).coerceIn(0f, 1f)
    val sheetState = if (brightnessPreview != null || dimPreview != null) {
        state.copy(
            settings = state.settings.copy(
                brightness = effectiveBrightness,
                dimLevel = effectiveDim,
            ),
        )
    } else {
        state
    }

    // Drive the window screen brightness: defer to the system level, or pin it to the reader setting.
    LaunchedEffect(state.settings.useSystemBrightness, effectiveBrightness) {
        val window = view.context.findActivity()?.window ?: return@LaunchedEffect
        val lp = window.attributes
        lp.screenBrightness = if (state.settings.useSystemBrightness) {
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        } else {
            effectiveBrightness.coerceIn(0f, 1f)
        }
        window.attributes = lp
    }

    val density = LocalDensity.current
    var topBarPx by remember { mutableIntStateOf(0) }
    var footerPx by remember { mutableIntStateOf(0) }
    // The top bar is static, so content needs an explicit inset: the navigator host consumes system
    // insets, so the WebView won't add a safe area for us. Wide / landscape layouts need the full bar
    // reserved to keep chapter headings clear of the chrome. Compact portrait already has generous
    // EPUB top whitespace; reserving the status/cutout strip as well creates the large blank gap seen
    // on phones, so there we reserve just the controls row.
    val configuration = LocalConfiguration.current
    val compactPortrait =
        configuration.screenWidthDp < 600 && configuration.screenHeightDp > configuration.screenWidthDp
    val fullTop = with(density) { topBarPx.toDp() }
    val statusTop = WindowInsets.statusBarsIgnoringVisibility
        .asPaddingValues()
        .calculateTopPadding()
    val baseTopInset = if (state.settings.layout == ReaderLayout.Scroll && compactPortrait) {
        (fullTop - statusTop).coerceAtLeast(0.dp)
    } else {
        fullTop
    }
    val footerHeight = if (state.settings.showFooter) with(density) { footerPx.toDp() } else 0.dp
    val scrollEndPadding =
        if (state.settings.layout == ReaderLayout.Scroll && state.settings.showFooter) {
            // Just enough breathing room so the last line doesn't sit tight under the footer; the footer
            // height itself is already reserved below.
            4.dp
        } else {
            0.dp
        }
    // Footer off: content flows under a fully transparent gesture bar. In scroll mode, reserve a
    // little extra end space so the last lines of a chapter don't sit tight against the footer.
    val baseBottomInset = footerHeight + scrollEndPadding
    // Immersive mode: the top bar + footer hide with the controls on a centre tap, and the content
    // reclaims their space (animated). The chrome always stays shown while loading or when immersive
    // is off (so the title/Back/footer remain visible).
    val immersive = state.settings.immersiveChrome
    val chromeShown = state.loading || !immersive || state.chapterControlsVisible
    val topInset by animateDpAsState(
        targetValue = if (chromeShown) baseTopInset else 0.dp,
        label = "readerTopInset",
    )
    val bottomInset by animateDpAsState(
        targetValue = if (chromeShown) baseBottomInset else 0.dp,
        label = "readerBottomInset",
    )
    val background = Color(state.settings.backgroundArgb)
    val onBackground = Color(state.settings.textArgb)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
    ) {
        when {
            state.failed -> CenteredMessage("This book couldn't be opened.")
            session != null -> {
                // Host the navigator even while loading so it can paint and fire its ready signal;
                // an opaque scrim below covers the half-rendered page until that first paint.
                ReaderNavigatorHost(
                    session = session,
                    backgroundArgb = state.settings.backgroundArgb,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = topInset, bottom = bottomInset),
                )

                // Until the first page paints, cover the WebView with an opaque "Opening…" scrim.
                // The top bar is drawn after it, so Back stays usable during a slow open.
                if (state.loading) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(background),
                    ) {
                        CenteredMessage("Opening…")
                    }
                }

                // Top bar: chevron · chapter title (reading font) · progress % · bookmark. Hidden with
                // the rest of the chrome in immersive mode; always shown otherwise.
                AnimatedVisibility(
                    visible = chromeShown,
                    enter = yomuChromeEnter(fromBottom = false),
                    exit = yomuChromeExit(toBottom = false),
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    ReaderTopBar(
                        chapter = state.chapterTitle ?: state.title,
                        font = state.settings.font,
                        progressPercent = state.progressPercent,
                        background = background,
                        content = onBackground,
                        isBookmarked = state.currentPageBookmarked,
                        onBack = onBack,
                        onToggleBookmark = onToggleBookmark,
                        onContentHeight = { topBarPx = it },
                        modifier = Modifier.yomuChromeBlur(this),
                    )
                }

                // Footer, overlays and all sheets only appear once the first page has painted.
                if (!state.loading) {
                if (state.settings.showFooter) {
                    AnimatedVisibility(
                        visible = chromeShown,
                        enter = yomuChromeEnter(),
                        exit = yomuChromeExit(),
                        modifier = Modifier.align(Alignment.BottomCenter),
                    ) {
                        ReaderFooter(
                            progressPercent = state.progressPercent,
                            settings = state.settings,
                            onContentHeight = { footerPx = it },
                            modifier = Modifier.yomuChromeBlur(this),
                        )
                    }
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

                // Next-chapter button at the chapter end (above the dim scrim so it stays legible).
                // Hidden while a lookup popup or the chapter-controls bar is open, to avoid overlap.
                if (state.lookup == null && !state.chapterControlsVisible) {
                    ReaderChapterButtons(
                        chapterProgression = state.chapterProgression,
                        hasNext = state.hasNextChapter,
                        bottomInset = bottomInset,
                        background = background,
                        content = onBackground,
                        onNext = onNextChapter,
                    )
                }

                // Bottom chapter-controls bar, toggled by a centre tap.
                ReaderChapterControlsBar(
                    visible = state.chapterControlsVisible,
                    bottomInset = bottomInset,
                    background = background,
                    content = onBackground,
                    hasPrevious = state.hasPreviousChapter,
                    hasNext = state.hasNextChapter,
                    onBrowse = onOpenBrowse,
                    onPrevious = onPreviousChapter,
                    onNext = onNextChapter,
                    onDisplay = onOpenSheet,
                    onMore = onOpenMore,
                )

                ReaderControlsSheet(
                    visible = state.sheetVisible,
                    state = sheetState,
                    onDismiss = onCloseSheet,
                    onSeek = onSeek,
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
                        onUpdateSettings(state.settings.copy(brightness = value))
                    },
                    onPreviewDim = { dim -> dimPreview = dim.coerceIn(0f, 1f) },
                    onCommitDim = { dim ->
                        val value = dim.coerceIn(0f, 1f)
                        dimPreview = value
                        onUpdateSettings(state.settings.copy(dimLevel = value))
                    },
                )

                CustomThemeSheet(
                    visible = state.customSheetVisible,
                    settings = sheetState.settings,
                    customThemes = state.customThemes,
                    onDismiss = onCloseCustomTheme,
                    onUpdateSettings = onUpdateSettings,
                    onSave = onSaveCustomTheme,
                    onApply = onApplyCustomTheme,
                    onDelete = onDeleteCustomTheme,
                )

                ReaderBrowseSheet(
                    tab = state.browseTab,
                    toc = state.toc,
                    tocLoading = state.tocLoading,
                    currentHref = state.currentHref,
                    onJumpToLocator = onJumpToLocator,
                    bookmarks = state.bookmarks,
                    onJumpToBookmark = onJumpToBookmark,
                    onDeleteBookmark = onDeleteBookmarkById,
                    highlights = state.highlights,
                    onJumpToHighlight = onJumpToHighlight,
                    onDeleteHighlight = onDeleteHighlightById,
                    searchQuery = state.searchQuery,
                    searchResults = state.searchResults,
                    searchInProgress = state.searchInProgress,
                    searchPerformed = state.searchPerformed,
                    onSearchQueryChange = onSearchQueryChange,
                    onSubmitSearch = onSubmitSearch,
                    onJumpToSearchResult = onJumpToSearchResult,
                    onSelectTab = onSelectBrowseTab,
                    onDismiss = onCloseBrowse,
                )

                ReaderMoreSheet(
                    visible = state.moreSheetVisible,
                    onChapterStart = onChapterStart,
                    onChapterEnd = onChapterEnd,
                    onDismiss = onCloseMore,
                )

                WordLookupSheet(
                    state = state.lookup,
                    onDismiss = onCloseLookup,
                    onPronounce = onPronounce,
                    onLookUpWord = onLookUpWord,
                    onBack = onLookupBack,
                )

                FootnoteSheet(html = state.footnoteHtml, onDismiss = onCloseFootnote)

                HighlightEditSheet(
                    highlight = state.editingHighlight,
                    onSelectColor = onSetHighlightColor,
                    onDelete = onDeleteHighlight,
                    onDismiss = onCloseEditHighlight,
                )

                }
            }

            else -> CenteredMessage("Opening…")
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.CenteredMessage(text: String) {
    Text(
        text = text,
        color = YomuTheme.colors.textMuted,
        style = YomuTheme.type.body,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.align(Alignment.Center),
    )
}
