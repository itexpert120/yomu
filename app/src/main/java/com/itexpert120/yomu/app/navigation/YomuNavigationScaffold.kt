package com.itexpert120.yomu.app.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
    selected: YomuTopLevelDestination?,
    onSelected: (YomuTopLevelDestination) -> Unit,
    content: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val wide = YomuWidthClass.fromWidth(maxWidth).isWide
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = selected != null,
                    enter = expandHorizontally(),
                    exit = shrinkHorizontally(),
                ) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxHeight(),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            YomuTopLevelDestination.entries.forEach { destination ->
                                NavigationRailItem(
                                    selected = selected == destination,
                                    onClick = { onSelected(destination) },
                                    icon = {
                                        Icon(
                                            imageVector = destination.icon,
                                            contentDescription = destination.label,
                                        )
                                    },
                                    label = { Text(destination.label) },
                                )
                            }
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
                AnimatedVisibility(
                    visible = selected != null,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        YomuTopLevelDestination.entries.forEach { destination ->
                            NavigationBarItem(
                                selected = selected == destination,
                                onClick = { onSelected(destination) },
                                icon = {
                                    Icon(
                                        imageVector = destination.icon,
                                        contentDescription = destination.label,
                                    )
                                },
                                label = { Text(destination.label) },
                            )
                        }
                    }
                }
            }
        }
    }
}
