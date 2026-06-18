package com.itexpert120.yomu.feature.reader

import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnAttach
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import com.itexpert120.yomu.core.reader.ReaderSession

/**
 * Hosts the engine's navigator fragment inside Compose: a [FragmentContainerView] is created and
 * the fragment added via the session's (Readium) [FragmentFactory] and class name — the engine
 * type never appears here. Yomu chrome is drawn over this by [ReaderScreen].
 */
@Composable
fun ReaderNavigatorHost(session: ReaderSession, modifier: Modifier = Modifier) {
    val activity = LocalContext.current.findFragmentActivity()
    val fragmentManager = activity.supportFragmentManager
    val tag = remember(session) { "reader-navigator" }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            FragmentContainerView(context).apply {
                id = View.generateViewId()
                // Add the fragment only once the container is attached, otherwise the
                // FragmentManager can't find the container view ("No view found for id").
                doOnAttach {
                    if (fragmentManager.findFragmentByTag(tag) == null) {
                        @Suppress("UNCHECKED_CAST")
                        val fragmentClass =
                            Class.forName(session.fragmentClassName) as Class<out Fragment>
                        fragmentManager.fragmentFactory = session.fragmentFactory
                        fragmentManager.beginTransaction()
                            .setReorderingAllowed(true)
                            .add(id, fragmentClass, Bundle(), tag)
                            .commitNow()
                    }
                    session.onFragmentHosted(fragmentManager, tag)
                }
            }
        },
    )

    DisposableEffect(session) {
        onDispose {
            fragmentManager.findFragmentByTag(tag)?.let { fragment ->
                fragmentManager.beginTransaction().remove(fragment).commitAllowingStateLoss()
            }
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
