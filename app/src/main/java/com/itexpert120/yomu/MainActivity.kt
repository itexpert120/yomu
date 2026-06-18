package com.itexpert120.yomu

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.itexpert120.yomu.app.AppViewModel
import com.itexpert120.yomu.app.YomuApp
import com.itexpert120.yomu.app.enableYomuEdgeToEdge
import com.itexpert120.yomu.app.updateYomuSystemBarIcons
import dagger.hilt.android.AndroidEntryPoint

// FragmentActivity (not ComponentActivity) so the Readium EpubNavigatorFragment can be hosted.
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableYomuEdgeToEdge()
        setContent {
            val appViewModel: AppViewModel = hiltViewModel()
            YomuApp(
                appViewModel = appViewModel,
                onResolvedThemeChange = { updateYomuSystemBarIcons(it) },
            )
        }
    }
}
