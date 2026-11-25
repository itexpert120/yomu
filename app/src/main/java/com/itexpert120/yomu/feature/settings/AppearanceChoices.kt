@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.itexpert120.yomu.feature.settings

import androidx.compose.foundation.layout.height
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.selected
import com.itexpert120.yomu.core.designsystem.YomuConnectedChoiceGroup
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.model.ThemePreference

/** Light/dark mode as a connected button group — mutually exclusive options, not a switch. */
@Composable
internal fun ThemeChoiceRow(selectedTheme: ThemePreference, onSelectTheme: (ThemePreference) -> Unit) {
    YomuConnectedChoiceGroup(
        options = ThemePreference.entries,
        height = ButtonDefaults.MediumContainerHeight,
        selected = selectedTheme,
        label = { it.label },
        icon = {
            when (it) {
                ThemePreference.System -> StackIcons.BrightnessAuto
                ThemePreference.Light -> StackIcons.LightMode
                ThemePreference.Dark -> StackIcons.DarkMode
            }
        },
        onSelect = onSelectTheme,
    )
}
