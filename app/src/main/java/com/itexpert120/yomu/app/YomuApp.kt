package com.itexpert120.yomu.app

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itexpert120.yomu.app.navigation.YomuNavHost
import com.itexpert120.yomu.core.designsystem.YomuDesignTheme
import com.itexpert120.yomu.core.designsystem.YomuNotice
import com.itexpert120.yomu.core.designsystem.YomuNoticeTone
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.YomuThemeMode
import com.itexpert120.yomu.core.model.ThemePreference

/**
 * Root composable: resolves the concrete theme mode (combining the saved preference, dark-theme
 * options, and the system dark setting) and hosts navigation. Reports the resolved mode up to the
 * Activity so it can keep the system bar icons legible.
 */
@Composable
fun YomuApp(
    appViewModel: AppViewModel,
    externalOpenViewModel: ExternalOpenViewModel,
    onResolvedThemeChange: (YomuThemeMode) -> Unit,
    onSplashThemeChange: (ThemePreference, Boolean) -> Unit,
) {
    val appearance by appViewModel.appearance.collectAsStateWithLifecycle()
    val preference = appearance.themePreference
    val oledDark = appearance.oledDark
    val systemDark = isSystemInDarkTheme()
    val externalError by externalOpenViewModel.error.collectAsStateWithLifecycle()
    val readingWriteError by appViewModel.readingWriteError.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val resolved = appearance.resolveThemeMode(systemDark)
    val dark = resolved != YomuThemeMode.Light
    LaunchedEffect(resolved) { onResolvedThemeChange(resolved) }
    LaunchedEffect(preference, oledDark, dark) { onSplashThemeChange(preference, oledDark && dark) }
    LaunchedEffect(externalError) {
        externalError?.let { message ->
            externalOpenViewModel.clearError()
            snackbarHostState.showSnackbar(message)
        }
    }

    YomuDesignTheme(
        themeMode = resolved,
        accent = appearance.accentSelection,
        colorStyle = appearance.colorStyle,
    ) {
        // Opaque app-coloured backing so the seamless screen transition never reveals the window
        // background (which would torch during navigation in dark mode).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            YomuNavHost(
                appViewModel = appViewModel,
                externalOpenViewModel = externalOpenViewModel,
            )
            val noticeModifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding()
                .widthIn(max = 568.dp)
                .fillMaxWidth()
                .padding(YomuTheme.space.md)
            SnackbarHost(hostState = snackbarHostState, modifier = noticeModifier) { data ->
                YomuNotice(
                    text = data.visuals.message,
                    icon = Icons.Rounded.ErrorOutline,
                    tone = YomuNoticeTone.Floating,
                )
            }
            readingWriteError?.let { message ->
                YomuNotice(
                    text = message,
                    modifier = noticeModifier,
                    icon = Icons.Rounded.ErrorOutline,
                    tone = YomuNoticeTone.Floating,
                    actionLabel = "Retry",
                    onAction = appViewModel::onRetryReadingWrites,
                )
            }
        }
    }
}
