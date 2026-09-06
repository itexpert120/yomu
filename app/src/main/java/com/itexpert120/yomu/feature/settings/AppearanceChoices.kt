package com.itexpert120.yomu.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuThemeMode
import com.itexpert120.yomu.core.designsystem.yomuAnimationsEnabled
import com.itexpert120.yomu.core.designsystem.yomuStaticColorScheme
import com.itexpert120.yomu.core.model.ThemePreference

@Composable
internal fun ThemeChoiceRow(selectedTheme: ThemePreference, onSelectTheme: (ThemePreference) -> Unit) {
    val listState = rememberLazyListState()
    val selectedIndex = ThemePreference.entries.indexOf(selectedTheme)
    LaunchedEffect(selectedIndex) {
        val layout = listState.layoutInfo
        val selectedItem = layout.visibleItemsInfo.firstOrNull { it.index == selectedIndex }
        val fullyVisible = selectedItem != null &&
            selectedItem.offset >= layout.viewportStartOffset &&
            selectedItem.offset + selectedItem.size <= layout.viewportEndOffset
        if (!fullyVisible) {
            if (yomuAnimationsEnabled()) listState.animateScrollToItem(selectedIndex) else listState.scrollToItem(selectedIndex)
        }
    }
    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(ThemePreference.entries, key = { it.name }) { theme ->
            val selected = theme == selectedTheme
            val preview = when (theme) {
                ThemePreference.Light -> yomuStaticColorScheme(YomuThemeMode.Light)
                ThemePreference.Dark -> yomuStaticColorScheme(YomuThemeMode.Dark)
                ThemePreference.System -> MaterialTheme.colorScheme
            }
            Surface(
                modifier = Modifier.width(132.dp).clip(RoundedCornerShape(24.dp))
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelectTheme(theme) }),
                shape = RoundedCornerShape(24.dp),
                color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(
                        Modifier.fillMaxWidth().height(88.dp).clip(RoundedCornerShape(12.dp))
                            .background(preview.surface).padding(10.dp).clearAndSetSemantics {},
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.width(36.dp).height(6.dp).background(preview.onSurface, CircleShape))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(28.dp).background(preview.primaryContainer, RoundedCornerShape(8.dp)))
                            Box(Modifier.size(28.dp).background(preview.tertiaryContainer, RoundedCornerShape(8.dp)))
                        }
                        Box(Modifier.fillMaxWidth().height(8.dp).background(preview.secondaryContainer, CircleShape))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(theme.label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
