package com.itexpert120.yomu.feature.reader

import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import com.itexpert120.yomu.core.reader.ReaderNavigator

/**
 * Hosts the engine's navigator fragment inside Compose: a [FragmentContainerView] is created and
 * the fragment added via the navigator's [androidx.fragment.app.FragmentFactory] and class
 * name — the engine type never appears here. Yomu chrome is drawn over this by [ReaderScreen].
 */
@Composable
fun ReaderNavigatorHost(
    navigator: ReaderNavigator,
    backgroundArgb: Long,
    immersive: Boolean,
    modifier: Modifier = Modifier,
) {
    val activity = LocalContext.current.findFragmentActivity()
    val fragmentManager = activity.supportFragmentManager
    val tag = remember(navigator) { "reader-navigator" }
    // Stable across configuration changes so a restored fragment can find its container view.
    val containerId = rememberSaveable { View.generateViewId() }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            FragmentContainerView(context).apply {
                id = containerId
                setBackgroundColor(backgroundArgb.toInt())
                applyReadiumInsetMode(immersive)
                doOnAttach {
                    // After a config change a placeholder navigator may have been restored (via the
                    // activity's restore factory). Remove whatever is there and add a fresh fragment
                    // bound to the real navigator factory, so the reader rebuilds correctly.
                    @Suppress("UNCHECKED_CAST")
                    val fragmentClass =
                        Class.forName(navigator.fragmentClassName) as Class<out Fragment>
                    fragmentManager.fragmentFactory = navigator.fragmentFactory
                    val transaction = fragmentManager.beginTransaction()
                        .setReorderingAllowed(true)
                    fragmentManager.findFragmentByTag(tag)?.let(transaction::remove)
                    transaction
                        .add(id, fragmentClass, Bundle(), tag)
                        // Let Compose render the shell and cover before Readium constructs its
                        // WebView. Hosting is still one ordered transaction, but no longer blocks
                        // the first frame with commitNow/remove+add work.
                        .runOnCommit { navigator.onFragmentHosted(fragmentManager, tag) }
                        .commitAllowingStateLoss()
                }
            }
        },
        update = { container ->
            container.setBackgroundColor(backgroundArgb.toInt())
            container.applyReadiumInsetMode(immersive)
        },
    )

    DisposableEffect(navigator) {
        onDispose {
            fragmentManager.findFragmentByTag(tag)?.let { fragment ->
                fragmentManager.beginTransaction().remove(fragment).commitAllowingStateLoss()
            }
        }
    }
}

private fun FragmentContainerView.applyReadiumInsetMode(immersive: Boolean) {
    if (immersive) {
        // Install before the Readium fragment is attached; otherwise R2WebView can cache a first
        // layout with the status/cutout inset already reserved.
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, _ ->
            WindowInsetsCompat.CONSUMED
        }
    } else {
        // Let Readium's own R2WebView inset dispatcher see the status/nav bars.
        ViewCompat.setOnApplyWindowInsetsListener(this, null)
    }
    ViewCompat.requestApplyInsets(this)
}

private fun Context.findFragmentActivity(): FragmentActivity {
    var context = this
    while (context is ContextWrapper) {
        if (context is FragmentActivity) return context
        context = context.baseContext
    }
    error("Reader must be hosted in a FragmentActivity")
}
