package com.itexpert120.yomu.core.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Extended FAB with stable container padding so expanding text never snaps the icon sideways. */
@Composable
fun YomuExtendedFloatingActionButton(
    expanded: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val labelEnter = if (yomuAnimationsEnabled()) {
        fadeIn(
            tween(
                durationMillis = YomuMotion.FabLabelFadeInMillis,
                delayMillis = YomuMotion.FabLabelFadeInDelayMillis,
                easing = YomuMotion.EmphasizedDecel,
            ),
        ) + expandHorizontally(
            animationSpec = tween(
                durationMillis = YomuMotion.FabExpandMillis,
                easing = YomuMotion.EmphasizedDecel,
            ),
            expandFrom = Alignment.Start,
        )
    } else {
        EnterTransition.None
    }
    val labelExit = if (yomuAnimationsEnabled()) {
        fadeOut(
            tween(
                durationMillis = YomuMotion.FabLabelFadeOutMillis,
                easing = YomuMotion.EmphasizedAccel,
            ),
        ) + shrinkHorizontally(
            animationSpec = tween(
                durationMillis = YomuMotion.FabCollapseMillis,
                easing = YomuMotion.EmphasizedAccel,
            ),
            shrinkTowards = Alignment.Start,
        )
    } else {
        ExitTransition.None
    }

    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.semantics { this.contentDescription = contentDescription },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = null)
            AnimatedVisibility(
                visible = expanded,
                enter = labelEnter,
                exit = labelExit,
            ) {
                Row(
                    modifier = Modifier.clearAndSetSemantics {},
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.width(12.dp))
                    Text(label)
                }
            }
        }
    }
}
