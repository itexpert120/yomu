package com.itexpert120.yomu.feature.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.model.ReaderLayout
import com.itexpert120.yomu.core.model.ReaderSettings
import com.itexpert120.yomu.core.reader.ReaderSession

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
    onAbout: () -> Unit,
) {
    val view = LocalView.current
    // Keep the system bars transparent with no enforced scrim, so the reader chrome defines their
    // look. The top bar is always present, so the status area never sits over content.
    DisposableEffect(Unit) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val prevStatus = window?.statusBarColor
        val prevNav = window?.navigationBarColor
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window?.isStatusBarContrastEnforced = false
            window?.isNavigationBarContrastEnforced = false
        }
        // Full-screen reading: hide both system bars (swipe to reveal). The footer already shows the
        // time + battery, so the status bar is redundant; the permanent top bar shows the chapter.
        controller?.let {
            it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            it.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            window?.let {
                prevStatus?.let { c -> it.statusBarColor = c }
                prevNav?.let { c -> it.navigationBarColor = c }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    it.isStatusBarContrastEnforced = true
                    it.isNavigationBarContrastEnforced = true
                }
            }
            controller?.show(WindowInsetsCompat.Type.systemBars())
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
    // Drive the window screen brightness: defer to the system level, or pin it to the reader setting.
    LaunchedEffect(state.settings.useSystemBrightness, state.settings.brightness) {
        val window = view.context.findActivity()?.window ?: return@LaunchedEffect
        val lp = window.attributes
        lp.screenBrightness = if (state.settings.useSystemBrightness) {
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        } else {
            state.settings.brightness.coerceIn(0f, 1f)
        }
        window.attributes = lp
    }

    val density = LocalDensity.current
    var topBarPx by remember { mutableIntStateOf(0) }
    var footerPx by remember { mutableIntStateOf(0) }
    // Scroll mode's webview already insets the status bar; paged mode does not. Subtract the status
    // bar in scroll mode so the content sits right under the bar without a double-counted gap.
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val fullTop = with(density) { topBarPx.toDp() }
    val topInset = if (state.settings.layout == ReaderLayout.Scroll) {
        (fullTop - statusTop).coerceAtLeast(0.dp)
    } else {
        fullTop
    }
    // Footer off: content flows under a fully transparent gesture bar (no inset).
    val bottomInset = if (state.settings.showFooter) with(density) { footerPx.toDp() } else 0.dp
    val background = Color(state.settings.backgroundArgb)
    val onBackground = Color(state.settings.textArgb)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
    ) {
        when {
            state.loading -> CenteredMessage("Opening…")
            state.failed -> CenteredMessage("This book couldn't be opened.")
            session != null -> {
                ReaderNavigatorHost(
                    session = session,
                    modifier = Modifier.fillMaxSize().padding(top = topInset, bottom = bottomInset),
                )

                ReaderTopBar(
                    chapter = state.chapterTitle ?: state.title,
                    edgeShadow = state.settings.edgeShadows,
                    background = background,
                    content = onBackground,
                    onBack = onBack,
                    onOpenSheet = onOpenSheet,
                    onContentHeight = { topBarPx = it },
                    modifier = Modifier.align(Alignment.TopCenter),
                )

                if (state.settings.showFooter) {
                    ReaderFooter(
                        progressPercent = state.progressPercent,
                        settings = state.settings,
                        onContentHeight = { footerPx = it },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }

                ReaderControlsSheet(
                    visible = state.sheetVisible,
                    state = state,
                    onDismiss = onCloseSheet,
                    onSeek = onSeek,
                    onNextChapter = onNextChapter,
                    onPreviousChapter = onPreviousChapter,
                    onUpdateSettings = onUpdateSettings,
                    onAbout = onAbout,
                )
            }
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
