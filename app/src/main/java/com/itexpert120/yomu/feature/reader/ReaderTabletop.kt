package com.itexpert120.yomu.feature.reader

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.flow.map

/**
 * How much of the window lies below a half-opened horizontal fold (tabletop posture): the reading
 * page stays above the hinge and the controls take the half lying flat. 0.dp in every other
 * posture and on devices without a fold, so their layout is unchanged.
 */
@Composable
internal fun rememberTabletopBelowHinge(): Dp {
    val activity = LocalActivity.current
    if (LocalInspectionMode.current || activity == null) return 0.dp
    val hingeTop = remember(activity) {
        WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).map { info ->
            info.displayFeatures
                .filterIsInstance<FoldingFeature>()
                .firstOrNull { it.state == FoldingFeature.State.HALF_OPENED && it.orientation == FoldingFeature.Orientation.HORIZONTAL }
                ?.bounds
                ?.top
        }
    }.collectAsStateWithLifecycle(initialValue = null).value ?: return 0.dp
    // The reader fills the window edge to edge, so window coordinates are the reader's own.
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val below = (windowHeight - hingeTop).coerceAtLeast(0)
    // A fold too near either edge leaves no useful half; keep the normal layout.
    if (below < windowHeight / 4 || below > windowHeight * 3 / 4) return 0.dp
    return with(LocalDensity.current) { below.toDp() }
}

/** The flat half of a tabletop posture: [content] (the controls panel) laid out below the hinge. */
@Composable
internal fun BoxScope.ReaderBelowHinge(height: Dp, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(height),
        content = content,
    )
}
