package com.itexpert120.yomu.app

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.itexpert120.yomu.app.navigation.YomuNavHost
import com.itexpert120.yomu.core.designsystem.YomuDesignTheme
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.YomuThemeMode
import com.itexpert120.yomu.core.model.ThemePreference

/**
 * Root composable: resolves the concrete theme mode (combining the saved preference, the OLED
 * toggle, and the system dark setting) and hosts navigation. Reports the resolved mode up to the
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
    val accentSelection = appearance.accentSelection
    val systemDark = isSystemInDarkTheme()

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
    val accentColor = Color(accentSelection.resolve(dark))

    LaunchedEffect(resolved) { onResolvedThemeChange(resolved) }
    LaunchedEffect(preference, oledDark) { onSplashThemeChange(preference, oledDark) }

    YomuDesignTheme(themeMode = resolved, accent = accentColor) {
        // Opaque app-coloured backing so the shared-axis transition never reveals the window
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
        }
    }
}
