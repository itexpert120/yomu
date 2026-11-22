package com.itexpert120.yomu.data.reader.readium

import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.itexpert120.yomu.core.reader.ReaderRestoreGuard
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.ExperimentalReadiumApi
import javax.inject.Inject

/**
 * Keeps a saved Readium navigator fragment from crashing activity re-creation.
 *
 * Readium's EPUB navigator has no no-arg constructor and explicitly does not support restoration
 * after Activity/process death: it throws from `onResume` if a saved fragment reaches that state.
 * Installed before `super.onCreate`, the guard sets a factory that can instantiate the saved
 * fragment with placeholder data, then discards it once the activity is created, before it can
 * start. The reader hosts a fresh, session-backed navigator.
 */
@OptIn(ExperimentalReadiumApi::class)
class ReadiumRestoreGuard @Inject constructor() : ReaderRestoreGuard {
    override fun install(activity: FragmentActivity) {
        activity.supportFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
        activity.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                // Dispatched after onCreate (fragments restored), before onStart.
                override fun onCreate(owner: LifecycleOwner) {
                    owner.lifecycle.removeObserver(this)
                    removeRestoredNavigators(activity.supportFragmentManager)
                }
            },
        )
    }

    private fun removeRestoredNavigators(fragmentManager: FragmentManager) {
        fragmentManager.fragments
            .filterIsInstance<EpubNavigatorFragment>()
            .forEach { fragment ->
                fragmentManager.beginTransaction()
                    .remove(fragment)
                    .commitNowAllowingStateLoss()
            }
    }
}
