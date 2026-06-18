package com.itexpert120.yomu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.itexpert120.yomu.app.enableYomuEdgeToEdge
import com.itexpert120.yomu.app.updateYomuSystemBarIcons
import com.itexpert120.yomu.core.designsystem.YomuThemeMode
import com.itexpert120.yomu.app.devgallery.YomuGalleryApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableYomuEdgeToEdge()
        updateYomuSystemBarIcons(YomuThemeMode.Light)
        setContent {
            YomuGalleryApp(onThemeModeChange = ::updateYomuSystemBarIcons)
        }
    }
}
