package com.itexpert120.yomu.feature.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.core.view.WindowCompat
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.model.ReaderSettings
import com.itexpert120.yomu.core.model.ReaderThemeMode
import com.itexpert120.yomu.core.reader.ReaderSession

@Composable
fun ReaderScreen(
    state: ReaderUiState,
    session: ReaderSession?,
    onBack: () -> Unit,
    onCloseSheet: () -> Unit,
    onSeek: (Double) -> Unit,
    onNextChapter: () -> Unit,
    onPreviousChapter: () -> Unit,
    onUpdateSettings: (ReaderSettings) -> Unit,
    onAbout: () -> Unit,
) {
    // The reader owns its system bars: keep them transparent so the custom chrome (solid bar + the
    // optional fade) defines how the status/nav areas look, instead of a system scrim.
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = view.context.findActivity()?.window
        val prevStatus = window?.statusBarColor
        val prevNav = window?.navigationBarColor
        window?.statusBarColor = Color.Transparent.toArgb()
        window?.navigationBarColor = Color.Transparent.toArgb()
        onDispose {
            prevStatus?.let { window?.statusBarColor = it }
            prevNav?.let { window?.navigationBarColor = it }
        }
    }
    LaunchedEffect(state.settings.theme) {
        val window = view.context.findActivity()?.window ?: return@LaunchedEffect
        val lightBars = state.settings.theme == ReaderThemeMode.Light ||
            state.settings.theme == ReaderThemeMode.Sepia
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = lightBars
    }

    val density = LocalDensity.current
    var topBarPx by remember { mutableIntStateOf(0) }
    var footerPx by remember { mutableIntStateOf(0) }
    val topInset = if (state.settings.showTopBar) with(density) { topBarPx.toDp() } else 0.dp
    val bottomInset = if (state.settings.showFooter) with(density) { footerPx.toDp() } else 0.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(YomuTheme.colors.appBackground),
    ) {
        when {
            state.loading -> CenteredMessage("Opening…")
            state.failed -> CenteredMessage("This book couldn't be opened.")
            session != null -> {
                // Inset the content so the solid bars never sit on top of the text (esp. in paged
                // mode); the fade strips still bleed over the content below/above the solid part.
                ReaderNavigatorHost(
                    session = session,
                    modifier = Modifier.fillMaxSize().padding(top = topInset, bottom = bottomInset),
                )

                if (state.settings.showTopBar) {
                    ReaderTopBar(
                        chapter = state.chapterTitle ?: state.title,
                        edgeShadow = state.settings.edgeShadows,
                        onBack = onBack,
                        onSolidHeight = { topBarPx = it },
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }

                if (state.settings.showFooter) {
                    ReaderFooter(
                        progressPercent = state.progressPercent,
                        settings = state.settings,
                        onSolidHeight = { footerPx = it },
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
