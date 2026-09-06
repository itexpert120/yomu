package com.itexpert120.yomu.feature.reader

import android.os.Build
import android.view.View
import android.view.Window
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/** Snapshot only properties owned by reading; unrelated window changes remain untouched. */
@Suppress("DEPRECATION")
internal fun captureReaderWindow(window: Window, view: View): () -> Unit {
    val controller = WindowCompat.getInsetsController(window, view)
    val brightness = window.attributes.screenBrightness
    val cutout = if (Build.VERSION.SDK_INT >= 28) window.attributes.layoutInDisplayCutoutMode else null
    val status = window.statusBarColor
    val navigation = window.navigationBarColor
    val flags = window.decorView.systemUiVisibility
    val lightStatus = controller.isAppearanceLightStatusBars
    val lightNavigation = controller.isAppearanceLightNavigationBars
    val behavior = controller.systemBarsBehavior
    val statusContrast = if (Build.VERSION.SDK_INT >= 29) window.isStatusBarContrastEnforced else null
    val navigationContrast = if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced else null
    return {
        window.attributes = window.attributes.apply {
            screenBrightness = brightness
            if (Build.VERSION.SDK_INT >= 28 && cutout != null) layoutInDisplayCutoutMode = cutout
        }
        window.statusBarColor = status
        window.navigationBarColor = navigation
        window.decorView.systemUiVisibility = flags
        controller.isAppearanceLightStatusBars = lightStatus
        controller.isAppearanceLightNavigationBars = lightNavigation
        controller.systemBarsBehavior = behavior
        if (Build.VERSION.SDK_INT >= 29) {
            window.isStatusBarContrastEnforced = statusContrast!!
            window.isNavigationBarContrastEnforced = navigationContrast!!
        }
        if (flags and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) controller.show(WindowInsetsCompat.Type.statusBars())
        if (flags and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION == 0) controller.show(WindowInsetsCompat.Type.navigationBars())
    }
}
