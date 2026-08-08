package com.itexpert120.yomu

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.animation.PathInterpolator
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.splashscreen.SplashScreenViewProvider
import androidx.fragment.app.FragmentActivity
import com.itexpert120.yomu.app.AppViewModel
import com.itexpert120.yomu.app.ExternalOpenViewModel
import com.itexpert120.yomu.app.SplashThemeStore
import com.itexpert120.yomu.app.YomuApp
import com.itexpert120.yomu.app.YomuSplashTheme
import com.itexpert120.yomu.app.enableYomuEdgeToEdge
import com.itexpert120.yomu.app.toSplashTheme
import com.itexpert120.yomu.app.updateYomuSystemBarIcons
import com.itexpert120.yomu.core.designsystem.yomuAnimationsEnabled
import com.itexpert120.yomu.core.model.ThemePreference
import com.itexpert120.yomu.data.reader.readium.readiumRestoreFragmentFactory
import com.itexpert120.yomu.data.reader.readium.removeRestoredReadiumNavigatorFragments
import dagger.hilt.android.AndroidEntryPoint

// FragmentActivity (not ComponentActivity) so the Readium EpubNavigatorFragment can be hosted.
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    // Activity-scoped so the same instance drives both the import (triggered here) and the
    // navigation to the imported book (collected inside the Compose nav host).
    private val appViewModel: AppViewModel by viewModels()
    private val externalOpenViewModel: ExternalOpenViewModel by viewModels()
    private val splashThemeStore by lazy { SplashThemeStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashTheme = splashThemeStore.read()
        setTheme(splashTheme.styleRes)
        persistPlatformSplashTheme(splashTheme)
        val splashScreen = installSplashScreen()
        splashScreen.setOnExitAnimationListener(::animateSplashExit)
        // Set before super.onCreate so a navigator fragment saved before a config change (rotation)
        // can be re-instantiated during restore instead of crashing; the reader replaces it.
        supportFragmentManager.fragmentFactory = readiumRestoreFragmentFactory()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { !appViewModel.appearance.value.isLoaded }
        // Readium navigator fragments cannot be resumed from FragmentManager-saved state. If the
        // activity was relaunched (rotation/process recreation) while the reader was open, discard
        // the restored dummy before FragmentActivity dispatches onResume to it.
        removeRestoredReadiumNavigatorFragments(supportFragmentManager)
        enableYomuEdgeToEdge()
        // Cold start from an external "Open with"/share.
        handleExternalIntent(intent)
        setContent {
            YomuApp(
                appViewModel = appViewModel,
                externalOpenViewModel = externalOpenViewModel,
                onResolvedThemeChange = { updateYomuSystemBarIcons(it) },
                onSplashThemeChange = ::updateSplashTheme,
            )
        }
    }

    /**
     * Keeps the platform splash visible until the first Compose frame is ready, then lets the
     * library take over through a short, reduced-motion-aware fade. The icon gets a small emphasis
     * scale so the brand does not appear to blink out between the starting window and the app bar.
     */
    private fun animateSplashExit(provider: SplashScreenViewProvider) {
        if (!yomuAnimationsEnabled()) {
            provider.remove()
            return
        }

        val splashView = provider.view
        val exitInterpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
        provider.iconView.animate()
            .scaleX(1.08f)
            .scaleY(1.08f)
            .setDuration(SPLASH_EXIT_DURATION_MS)
            .setInterpolator(exitInterpolator)
            .start()
        splashView.animate()
            .alpha(0f)
            .setDuration(SPLASH_EXIT_DURATION_MS)
            .setInterpolator(exitInterpolator)
            .setListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        provider.remove()
                    }
                },
            )
            .start()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Warm start: the app is already running and the user opened another EPUB.
        handleExternalIntent(intent)
    }

    /** Pulls an EPUB URI out of an ACTION_VIEW (intent.data) or ACTION_SEND (EXTRA_STREAM) intent. */
    private fun handleExternalIntent(intent: Intent?) {
        val uri = intent.extractEpubUri() ?: return
        externalOpenViewModel.onExternalUri(uri)
    }

    private fun updateSplashTheme(preference: ThemePreference, oledDark: Boolean) {
        val splashTheme = preference.toSplashTheme(oledDark)
        if (splashThemeStore.write(splashTheme)) persistPlatformSplashTheme(splashTheme)
    }

    private fun persistPlatformSplashTheme(theme: YomuSplashTheme) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setSplashScreenTheme(theme.styleRes)
        }
    }

    private companion object {
        const val SPLASH_EXIT_DURATION_MS = 220L
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
private fun Intent.parcelableStreamExtra(): Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
} else {
    getParcelableExtra(Intent.EXTRA_STREAM)
}
