package com.itexpert120.yomu.core.designsystem

import androidx.compose.material3.Icon
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * The screen's primary action as an M3 Expressive small extended FAB (the size that replaces the
 * baseline FAB — proportionate on phones). It collapses to its icon on scroll using the component's own
 * expressive motion.
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
    SmallExtendedFloatingActionButton(
        onClick = onClick,
        expanded = expanded,
        icon = { Icon(imageVector = icon, contentDescription = null) },
        text = { Text(label) },
        modifier = modifier.semantics { this.contentDescription = contentDescription },
    )
}
