package com.itexpert120.yomu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.itexpert120.yomu.app.AppViewModel
import com.itexpert120.yomu.app.YomuApp
import com.itexpert120.yomu.app.enableYomuEdgeToEdge
import com.itexpert120.yomu.app.rememberYomuGraph
import com.itexpert120.yomu.app.updateYomuSystemBarIcons

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableYomuEdgeToEdge()
        setContent {
            val graph = rememberYomuGraph()
            val appViewModel: AppViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { AppViewModel(graph.appSettingsRepository) }
                },
            )
            YomuApp(
                appViewModel = appViewModel,
                onResolvedThemeChange = { updateYomuSystemBarIcons(it) },
            )
        }
    }
}
