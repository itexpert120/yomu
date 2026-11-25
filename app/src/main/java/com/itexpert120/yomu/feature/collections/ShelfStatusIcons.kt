package com.itexpert120.yomu.feature.collections

import androidx.compose.ui.graphics.vector.ImageVector
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.model.ShelfStatus

/** The one icon per shelf status, shared by the library tabs, Book Details and Organize. */
val ShelfStatus.icon: ImageVector
    get() = when (this) {
        ShelfStatus.ToRead -> StackIcons.Book
        ShelfStatus.Reading -> StackIcons.PlayArrow
        ShelfStatus.Finished -> StackIcons.CheckCircle
        ShelfStatus.OnHold -> StackIcons.PauseCircle
        ShelfStatus.Dropped -> StackIcons.DoNotDisturbOn
    }
