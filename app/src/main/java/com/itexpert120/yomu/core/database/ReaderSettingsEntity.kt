package com.itexpert120.yomu.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-book reader settings override, stored as a JSON blob so the schema doesn't churn as settings
 * grow. Presence means the book has its own settings that supersede the global default.
 */
@Entity(tableName = "reader_settings")
data class ReaderSettingsEntity(
    @PrimaryKey val bookId: String,
    val json: String,
)
