package com.itexpert120.yomu.feature.reader

import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
    val container = remember { mutableStateOf<FragmentContainerView?>(null) }

    // Leaving the reader removes this view while the page (a WebView) may hold focus. Android then
    // asks the window to refocus during the removal, which makes Compose search for focus and
    // remeasure lazy lists in the middle of applying the screen change — an internal Compose crash
    // ("pending composition has not been applied"). Drop the page's focus as soon as the reader
    // starts leaving (the destination pauses when its exit transition begins), before removal.
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        container.value?.let { if (it.hasFocus()) it.clearFocus() }
    }

    AndroidView(
        modifier = modifier,
        onRelease = { it.clearFocus() },
        factory = { context ->
            FragmentContainerView(context).also { container.value = it }.apply {
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
