package com.itexpert120.yomu.app.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.itexpert120.yomu.core.designsystem.YomuNavigationBar
import com.itexpert120.yomu.core.designsystem.YomuNavigationBarItem
import com.itexpert120.yomu.core.designsystem.YomuNavigationRail
import com.itexpert120.yomu.core.designsystem.YomuNavigationRailItem
import com.itexpert120.yomu.core.designsystem.YomuWidthClass

internal enum class YomuTopLevelDestination(
    val label: String,
    val icon: ImageVector,
) {
    Library("Library", Icons.AutoMirrored.Rounded.LibraryBooks),
    Statistics("Statistics", Icons.Rounded.Insights),
    Settings("Settings", Icons.Rounded.Settings),
}

/** Material's compact bottom navigation becomes a side rail on medium and expanded screens. */
@Composable
internal fun YomuTopLevelNavigation(
    selected: YomuTopLevelDestination,
    onSelected: (YomuTopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val wide = YomuWidthClass.fromWidth(maxWidth).isWide
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                YomuNavigationRail(modifier = Modifier.fillMaxHeight()) {
                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        YomuTopLevelDestination.entries.forEach { destination ->
                            YomuNavigationRailItem(
                                selected = selected == destination,
                                onClick = { onSelected(destination) },
                                icon = destination.icon,
                                label = destination.label,
                            )
                        }
                    }
                }
                content(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    content(Modifier.fillMaxSize())
                }
                YomuNavigationBar {
                    YomuTopLevelDestination.entries.forEach { destination ->
                        YomuNavigationBarItem(
                            selected = selected == destination,
                            onClick = { onSelected(destination) },
                            icon = destination.icon,
                            label = destination.label,
                        )
                    }
                }
            }
        }
    }
}
