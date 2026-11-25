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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuMotion
import com.itexpert120.yomu.core.designsystem.YomuNavigationBar
import com.itexpert120.yomu.core.designsystem.YomuNavigationBarItem
import com.itexpert120.yomu.core.designsystem.YomuNavigationRail
import com.itexpert120.yomu.core.designsystem.YomuNavigationRailItem
import com.itexpert120.yomu.core.designsystem.YomuWidthClass
import com.itexpert120.yomu.core.designsystem.icons.StackIcons

internal enum class YomuTopLevelDestination(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Library("Library", StackIcons.ImportContacts, StackIcons.ImportContactsFilled),
    Statistics("Statistics", StackIcons.Monitoring, StackIcons.MonitoringFilled),
    Settings("Settings", StackIcons.Tune, StackIcons.TuneFilled),
}

/**
 * How much of the window's bottom the top-level navigation bar currently covers, so app-wide notices
 * can float above it instead of over it. Written by [YomuTopLevelNavigation]; 0 while no bar shows.
 */
@Stable
class YomuBottomChromeInset {
    var height: Dp by mutableStateOf(0.dp)
        internal set
}

val LocalYomuBottomChromeInset = staticCompositionLocalOf { YomuBottomChromeInset() }

/**
 * Whether the bottom navigation bar is showing under this content: the content then ends above the
 * bar (which pads itself for the gesture area), so it doesn't pad for the gesture area again.
 * False on wide screens (the rail) and while a contextual toolbar has replaced the bar.
 */
val LocalYomuNavigationBarShown = compositionLocalOf { false }

/**
 * Called by the start screen once its first frame shows real content (not a loading placeholder),
 * so the platform splash can hand over straight to it. A no-op outside the app shell.
 */
val LocalYomuContentReady = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * Stack's navigation: bar A on phones (icon + label, active in ink with a filled icon), a rail on
 * medium and wider screens. The bar stays put while the content scrolls (the screens pin a compact
 * header at the top instead); a contextual toolbar (library selection, search) replaces it via
 * [barVisible].
 */
@Composable
internal fun YomuTopLevelNavigation(
    selected: YomuTopLevelDestination,
    onSelected: (YomuTopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
    barVisible: Boolean = true,
    content: @Composable (Modifier) -> Unit,
) {
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
                        .fillMaxHeight(),
                )
            }
        } else {
            val density = LocalDensity.current
            val showBar = barVisible
            val inset = LocalYomuBottomChromeInset.current
            var barHeight by remember { mutableStateOf(0.dp) }
            // While the bar is away nothing covers the bottom but the system gesture area.
            val covered = if (showBar) barHeight else 0.dp
            SideEffect { inset.height = covered }
            DisposableEffect(inset) { onDispose { inset.height = 0.dp } }
            Column(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    CompositionLocalProvider(LocalYomuNavigationBarShown provides showBar) {
                        content(Modifier.fillMaxSize())
                    }
                }
                // Contextual toolbars (e.g. library selection) replace the bar rather than stack on it.
                AnimatedVisibility(
                    visible = showBar,
                    enter = expandVertically(YomuMotion.scheme.defaultSpatialSpec(), expandFrom = Alignment.Top),
                    exit = shrinkVertically(YomuMotion.scheme.defaultSpatialSpec(), shrinkTowards = Alignment.Top),
                ) {
                    YomuNavigationBar(
                        modifier = Modifier.onSizeChanged { size ->
                            barHeight = with(density) { size.height.toDp() }
                        },
                    ) {
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
