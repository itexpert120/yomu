package com.itexpert120.yomu.feature.collections

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.ui.graphics.vector.ImageVector
import com.itexpert120.yomu.core.model.ShelfStatus

/** The one icon per shelf status, shared by the library tabs, Book Details and Organize. */
val ShelfStatus.icon: ImageVector
    get() = when (this) {
        ShelfStatus.ToRead -> Icons.Rounded.Book
        ShelfStatus.Reading -> Icons.Rounded.PlayArrow
        ShelfStatus.Finished -> Icons.Rounded.CheckCircle
        ShelfStatus.OnHold -> Icons.Rounded.PauseCircle
        ShelfStatus.Dropped -> Icons.Rounded.DoNotDisturbOn
    }
