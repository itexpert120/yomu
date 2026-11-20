package com.itexpert120.yomu.app.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.itexpert120.yomu.core.designsystem.YomuMotion
import com.itexpert120.yomu.core.designsystem.YomuNavigationBar
import com.itexpert120.yomu.core.designsystem.YomuNavigationBarItem
import com.itexpert120.yomu.core.designsystem.YomuNavigationRail
import com.itexpert120.yomu.core.designsystem.YomuNavigationRailItem
import com.itexpert120.yomu.core.designsystem.YomuWidthClass

/** The back-motion parallax and dim for the content under a leaving page, minus the app chrome. */
private fun Modifier.backParallax(offset: () -> Float, rtl: Boolean): Modifier = graphicsLayer {
    val travel = offset() * YomuMotion.BackParallaxFraction * size.width
    translationX = if (rtl) travel else -travel
}.drawWithContent {
    drawContent()
    val dim = offset() * YomuMotion.BackPreviewDim
    if (dim > 0f) drawRect(Color.Black, alpha = dim)
}

internal enum class YomuTopLevelDestination(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Library("Library", Icons.AutoMirrored.Outlined.LibraryBooks, Icons.AutoMirrored.Filled.LibraryBooks),
    Statistics("Statistics", Icons.Outlined.Insights, Icons.Filled.Insights),
    Settings("Settings", Icons.Outlined.Settings, Icons.Filled.Settings),
}

/** Material's compact bottom navigation becomes a side rail on medium and expanded screens. */
@Composable
internal fun YomuTopLevelNavigation(
    selected: YomuTopLevelDestination,
    onSelected: (YomuTopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
    barVisible: Boolean = true,
    // 0..1, read in the draw phase: how far back the content still is in a back motion (1 = fully
    // offset). Only the content moves and dims; the bar/rail stays put, as fixed app chrome.
    contentBackOffset: () -> Float = { 0f },
    content: @Composable (Modifier) -> Unit,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val wide = YomuWidthClass.fromWidth(maxWidth).isWide
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                YomuNavigationRail(modifier = Modifier.fillMaxHeight()) {
                    YomuTopLevelDestination.entries.forEach { destination ->
                        YomuNavigationRailItem(
                            selected = selected == destination,
                            onClick = { onSelected(destination) },
                            icon = destination.icon,
                            selectedIcon = destination.selectedIcon,
                            label = destination.label,
                        )
                    }
                }
                content(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .backParallax(contentBackOffset, rtl),
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .backParallax(contentBackOffset, rtl),
                ) {
                    content(Modifier.fillMaxSize())
                }
                // Contextual toolbars (e.g. library selection) replace the bar rather than stack on it.
                AnimatedVisibility(
                    visible = barVisible,
                    enter = expandVertically(YomuMotion.scheme.defaultSpatialSpec(), expandFrom = Alignment.Top),
                    exit = shrinkVertically(YomuMotion.scheme.fastSpatialSpec(), shrinkTowards = Alignment.Top),
                ) {
                    YomuNavigationBar {
                        YomuTopLevelDestination.entries.forEach { destination ->
                            YomuNavigationBarItem(
                                selected = selected == destination,
                                onClick = { onSelected(destination) },
                                icon = destination.icon,
                                selectedIcon = destination.selectedIcon,
                                label = destination.label,
                            )
                        }
                    }
                }
            }
        }
    }
}
