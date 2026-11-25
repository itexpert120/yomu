package com.itexpert120.yomu.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.yomuPopupEnter
import com.itexpert120.yomu.core.designsystem.yomuPopupExit
import com.itexpert120.yomu.core.designsystem.yomuPressable

/**
 * "Back to p. 112" after a jump: a small ink pill floating at the top of the page, in the page's own
 * colours (as the Contents sheet's "Current chapter" pill), with the soft static shadow of Stack's
 * floating notices. It materializes with the popup motion and keeps its last label while leaving.
 */
@Composable
internal fun ReaderJumpBackChip(
    label: String?,
    colors: ReaderChromeColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var last by remember { mutableStateOf(label) }
    if (label != null) last = label
    AnimatedVisibility(
        visible = label != null,
        enter = yomuPopupEnter(TopCenterOrigin),
        exit = yomuPopupExit(TopCenterOrigin),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 2.dp)
                .widthIn(max = 360.dp)
                .heightIn(min = 44.dp)
                .dropShadow(
                    CircleShape,
                    Shadow(radius = 20.dp, spread = (-8).dp, offset = DpOffset(0.dp, 6.dp), color = Color.Black.copy(alpha = 0.35f)),
                )
                .clip(CircleShape)
                .yomuPressable(onClick = onClick, shape = CircleShape)
                .background(colors.ink)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                .padding(start = 14.dp, end = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(StackIcons.ArrowBack, contentDescription = null, tint = colors.page, modifier = Modifier.size(18.dp))
            Text(
                text = last.orEmpty(),
                color = colors.page,
                style = StackType.Label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val TopCenterOrigin = TransformOrigin(0.5f, 0f)
