package com.itexpert120.yomu.feature.reader

import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import com.itexpert120.yomu.core.reader.ReaderNavigator

/**
 * Hosts the engine's reading view inside Compose, in a [FragmentContainerView] the navigator fills
 * itself — the engine type never appears here. Yomu chrome is drawn over this by [ReaderScreen].
 */
@Composable
fun ReaderNavigatorHost(
    navigator: ReaderNavigator,
    backgroundArgb: Long,
    immersive: Boolean,
    modifier: Modifier = Modifier,
) {
    val activity = LocalContext.current.findFragmentActivity()
    // Stable across configuration changes so a restored fragment can find its container view.
    val containerId = rememberSaveable { View.generateViewId() }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            FragmentContainerView(context).apply {
                id = containerId
                setBackgroundColor(backgroundArgb.toInt())
                navigator.setImmersive(immersive)
                navigator.host(this, activity.supportFragmentManager)
            }
        },
        update = { container ->
            container.setBackgroundColor(backgroundArgb.toInt())
            navigator.setImmersive(immersive)
        },
    )

    DisposableEffect(navigator) {
        ReaderHostActivity.attach(activity)
        onDispose {
            ReaderHostActivity.detach(activity)
            navigator.unhost()
        }
    }
}

private fun Context.findFragmentActivity(): FragmentActivity {
    var context = this
    while (context is ContextWrapper) {
        if (context is FragmentActivity) return context
        context = context.baseContext
    }
    error("Reader must be hosted in a FragmentActivity")
}
