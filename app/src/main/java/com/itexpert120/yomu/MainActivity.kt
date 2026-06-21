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
import dagger.hilt.android.AndroidEntryPoint

// FragmentActivity (not ComponentActivity) so the Readium EpubNavigatorFragment can be hosted.
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    // Activity-scoped so the same instance drives both the import (triggered here) and the
    // navigation to the imported book (collected inside the Compose nav host).
    private val externalOpenViewModel: ExternalOpenViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableYomuEdgeToEdge()
        // Cold start from an external "Open with"/share.
        handleExternalIntent(intent)
        setContent {
            val appViewModel: AppViewModel = hiltViewModel()
            YomuApp(
                appViewModel = appViewModel,
                externalOpenViewModel = externalOpenViewModel,
                onResolvedThemeChange = { updateYomuSystemBarIcons(it) },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Warm start: the app is already running and the user opened another EPUB from outside.
        handleExternalIntent(intent)
    }

    /** Pulls an EPUB URI out of an ACTION_VIEW (intent.data) or ACTION_SEND (EXTRA_STREAM) intent. */
    private fun handleExternalIntent(intent: Intent?) {
        val uri = intent.extractEpubUri() ?: return
        externalOpenViewModel.onExternalUri(uri)
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
