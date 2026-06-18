package com.itexpert120.yomu.app

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import com.itexpert120.yomu.core.designsystem.YomuThemeMode

fun ComponentActivity.enableYomuEdgeToEdge() {
    enableEdgeToEdge()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
    }
}

fun ComponentActivity.updateYomuSystemBarIcons(themeMode: YomuThemeMode) {
    val useDarkIcons = themeMode == YomuThemeMode.Light
    WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = useDarkIcons
        isAppearanceLightNavigationBars = useDarkIcons
    }
}
