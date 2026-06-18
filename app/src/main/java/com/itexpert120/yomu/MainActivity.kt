package com.itexpert120.yomu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import com.itexpert120.yomu.core.designsystem.YomuThemeMode
import com.itexpert120.yomu.feature.library.YomuLibraryApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        setContent {
            YomuLibraryApp(
                onThemeModeChange = { mode ->
                    val useDarkIcons = mode == YomuThemeMode.Light
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = useDarkIcons
                        isAppearanceLightNavigationBars = useDarkIcons
                    }
                }
            )
        }
    }
}
