package com.itexpert120.yomu.core.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * The screen's floating primary action as a Stack ink pill (56dp, icon + 16/600 label) with the
 * soft static shadow floating things carry. When [expanded] is false (e.g. while scrolling) the
 * label folds away and it becomes a 56dp ink circle.
 */
@Composable
fun YomuExtendedFloatingActionButton(
    expanded: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val stack = YomuTheme.stack
    val motion = yomuAnimationsEnabled()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntSize>()
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    Row(
        modifier = modifier
            .dropShadow(CircleShape, FabShadow)
            .clip(CircleShape)
            .yomuPressable(onClick = onClick, role = Role.Button, shape = CircleShape)
            .semantics { this.contentDescription = contentDescription }
            .background(stack.ink)
            .defaultMinSize(minWidth = 56.dp, minHeight = 56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = stack.onInk, modifier = Modifier.size(24.dp))
        AnimatedVisibility(
            visible = expanded,
            enter = if (motion) expandHorizontally(spatial, Alignment.Start) + fadeIn(effects) else fadeIn(effects),
            exit = if (motion) shrinkHorizontally(spatial, Alignment.Start) + fadeOut(effects) else fadeOut(effects),
        ) {
            Row {
                Spacer(Modifier.width(8.dp))
                Text(label, style = StackType.Button, color = stack.onInk, maxLines = 1, modifier = Modifier.padding(end = 6.dp))
            }
        }
    }
}

private val FabShadow = Shadow(radius = 24.dp, spread = (-10).dp, offset = DpOffset(0.dp, 8.dp), color = Color(0xFF16191C).copy(alpha = 0.45f))
