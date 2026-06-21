package com.itexpert120.yomu

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.itexpert120.yomu.app.AppViewModel
import com.itexpert120.yomu.app.YomuApp
import com.itexpert120.yomu.app.enableYomuEdgeToEdge
import com.itexpert120.yomu.app.updateYomuSystemBarIcons
import com.itexpert120.yomu.widget.WidgetDeepLink
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// FragmentActivity (not ComponentActivity) so the Readium EpubNavigatorFragment can be hosted.
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    // One-shot book-open requests coming from a home-screen widget tap. Buffered so a request made
    // on cold start (before the nav host collects) is not dropped.
    private val openBookFromWidget = MutableSharedFlow<String>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableYomuEdgeToEdge()
        // Cold start from a widget tap.
        handleWidgetIntent(intent)
        setContent {
            val appViewModel: AppViewModel = hiltViewModel()
            YomuApp(
                appViewModel = appViewModel,
                openBookFromWidget = openBookFromWidget.asSharedFlow(),
                onResolvedThemeChange = { updateYomuSystemBarIcons(it) },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Warm start: the app is already running and the user tapped a widget.
        handleWidgetIntent(intent)
    }

    private fun handleWidgetIntent(intent: Intent?) {
        val bookId = intent?.getStringExtra(WidgetDeepLink.EXTRA_OPEN_BOOK_ID) ?: return
        openBookFromWidget.tryEmit(bookId)
    }
}
