package com.itexpert120.yomu.data.reader.readium

import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.FragmentManager
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.ExperimentalReadiumApi

/**
 * A [FragmentFactory] that can instantiate [EpubNavigatorFragment] with placeholder data.
 *
 * Set this on an activity's FragmentManager *before* `super.onCreate`, so that after a configuration
 * change (e.g. rotation) the framework can re-instantiate a saved navigator fragment instead of
 * crashing with "could not find Fragment constructor" (the real fragment has no no-arg constructor).
 * The reader screen then removes this placeholder and adds the real, session-backed fragment.
 *
 * Returns an androidx [FragmentFactory] so callers don't touch Readium types directly.
 */
@OptIn(ExperimentalReadiumApi::class)
fun readiumRestoreFragmentFactory(): FragmentFactory = EpubNavigatorFragment.createDummyFactory()

/**
 * Removes navigator fragments restored by the framework before they can resume.
 *
 * Readium's EPUB navigator explicitly does not support restoration after Activity/process death and
 * throws from `onResume` if a saved fragment reaches that lifecycle state. The dummy factory above
 * only lets FragmentManager instantiate the saved fragment during `super.onCreate`; the host must
 * then discard it immediately and let Compose create a fresh, session-backed navigator.
 */
@OptIn(ExperimentalReadiumApi::class)
fun removeRestoredReadiumNavigatorFragments(fragmentManager: FragmentManager) {
    fragmentManager.fragments
        .filterIsInstance<EpubNavigatorFragment>()
        .forEach { fragment ->
            fragmentManager.beginTransaction()
                .remove(fragment)
                .commitNowAllowingStateLoss()
        }
}
