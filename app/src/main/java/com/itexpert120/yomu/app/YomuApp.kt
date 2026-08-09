package com.itexpert120.yomu.app

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.app.navigation.YomuNavHost
import com.itexpert120.yomu.core.designsystem.YomuDesignTheme
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
    val appearance by appViewModel.appearance.collectAsState()
    val preference = appearance.themePreference
    val oledDark = appearance.oledDark
    val systemDark = isSystemInDarkTheme()
    val externalError by externalOpenViewModel.error.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val dark = when (preference) {
        ThemePreference.System -> systemDark
        ThemePreference.Light -> false
        ThemePreference.Dark -> true
    }
    val resolved = when {
        !dark -> YomuThemeMode.Light
        oledDark -> YomuThemeMode.Oled
        else -> YomuThemeMode.Dark
    }
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
        dynamicColors = appearance.dynamicColors,
    ) {
        // Opaque app-coloured backing so the seamless screen transition never reveals the window
        // background (which would torch during navigation in dark mode).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(YomuTheme.colors.appBackground),
        ) {
            YomuNavHost(
                appViewModel = appViewModel,
                externalOpenViewModel = externalOpenViewModel,
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 568.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
        }
    }
}
