package com.itexpert120.yomu.data.reader.readium

import androidx.fragment.app.FragmentFactory
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
