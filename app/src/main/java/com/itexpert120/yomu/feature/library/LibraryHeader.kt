package com.itexpert120.yomu.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuTheme

@Composable
internal fun LibraryHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Library",
                    color = YomuTheme.colors.textPrimary,
                    style = YomuTheme.type.display,
                )
                Text(
                    text = "12 books",
                    color = YomuTheme.colors.textMuted,
                    style = YomuTheme.type.caption,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                HeaderIconAction(onClick = {}) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Import",
                        tint = YomuTheme.colors.appBackground,
                        modifier = Modifier.size(22.dp),
                    )
                }
                HeaderIconAction(onClick = {}) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        tint = YomuTheme.colors.appBackground,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
        SearchHint()
        LibraryOrganizationRow()
    }
}

@Composable
private fun SearchHint() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(YomuTheme.radius.pill))
            .background(YomuTheme.colors.surface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Search books, authors, groups",
            color = YomuTheme.colors.textMuted,
            style = YomuTheme.type.body,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = "Search",
            tint = YomuTheme.colors.textSecondary,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun LibraryOrganizationRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OrganizationDropdown(label = "Group", value = "None", onClick = {})
        OrganizationDropdown(label = "Sort", value = "Recent", onClick = {})
    }
}

@Composable
private fun OrganizationDropdown(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(YomuTheme.radius.pill))
            .background(YomuTheme.colors.surfaceRaised)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = label, color = YomuTheme.colors.textMuted, style = YomuTheme.type.caption)
        Text(text = value, color = YomuTheme.colors.textPrimary, style = YomuTheme.type.caption)
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            tint = YomuTheme.colors.textMuted,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun HeaderIconAction(onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(YomuTheme.colors.textPrimary)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}
