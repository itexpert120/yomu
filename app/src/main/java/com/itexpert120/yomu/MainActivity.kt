package com.itexpert120.yomu

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.itexpert120.yomu.app.AppViewModel
import com.itexpert120.yomu.app.ExternalOpenViewModel
import com.itexpert120.yomu.app.YomuApp
import com.itexpert120.yomu.app.enableYomuEdgeToEdge
import com.itexpert120.yomu.app.updateYomuSystemBarIcons
import com.itexpert120.yomu.widget.WidgetDeepLink
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// FragmentActivity (not ComponentActivity) so the Readium EpubNavigatorFragment can be hosted.
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    // Activity-scoped so the same instance drives both the import (triggered here) and the
    // navigation to the imported book (collected inside the Compose nav host).
    private val externalOpenViewModel: ExternalOpenViewModel by viewModels()

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
        // Cold start from an external "Open with"/share, or a widget tap.
        handleExternalIntent(intent)
        handleWidgetIntent(intent)
        setContent {
            val appViewModel: AppViewModel = hiltViewModel()
            YomuApp(
                appViewModel = appViewModel,
                externalOpenViewModel = externalOpenViewModel,
                openBookFromWidget = openBookFromWidget.asSharedFlow(),
                onResolvedThemeChange = { updateYomuSystemBarIcons(it) },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Warm start: the app is already running and the user opened another EPUB / tapped a widget.
        handleExternalIntent(intent)
        handleWidgetIntent(intent)
    }

    /** Pulls an EPUB URI out of an ACTION_VIEW (intent.data) or ACTION_SEND (EXTRA_STREAM) intent. */
    private fun handleExternalIntent(intent: Intent?) {
        val uri = intent.extractEpubUri() ?: return
        externalOpenViewModel.onExternalUri(uri)
    }

    private fun handleWidgetIntent(intent: Intent?) {
        val bookId = intent?.getStringExtra(WidgetDeepLink.EXTRA_OPEN_BOOK_ID) ?: return
        openBookFromWidget.tryEmit(bookId)
    }
}

private fun Intent?.extractEpubUri(): Uri? {
    if (this == null) return null
    return when (action) {
        Intent.ACTION_VIEW -> data
        Intent.ACTION_SEND -> parcelableStreamExtra()
        else -> null
    }
}

@Suppress("DEPRECATION")
private fun Intent.parcelableStreamExtra(): Uri? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        getParcelableExtra(Intent.EXTRA_STREAM)
    }
