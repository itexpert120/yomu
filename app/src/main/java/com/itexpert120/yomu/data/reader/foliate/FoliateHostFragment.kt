package com.itexpert120.yomu.data.reader.foliate

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment

/**
 * Puts the foliate reader's view into the reader screen's FragmentContainerView, which only takes
 * fragment views. The session hands the view over through [pendingView] right before adding the
 * fragment. A fragment the framework restores on its own (rotation, process death) has no view to
 * show and stays empty until the reader hosts a fresh one in its place.
 */
class FoliateHostFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = pendingView ?: return FrameLayout(inflater.context)
        pendingView = null
        (view.parent as? ViewGroup)?.removeView(view)
        return view
    }

    companion object {
        internal var pendingView: View? = null
    }
}
