package com.itexpert120.yomu.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Retained solely so databases created by the experimental v13 sync work remain readable. */
@Entity(tableName = "sync_metadata")
data class LegacySyncMetadataEntity(
    @PrimaryKey val key: String,
    val value: String,
)

/** Retained solely for schema and data compatibility; no current feature reads or writes it. */
@Entity(
    tableName = "sync_records",
    indices = [
        Index(value = ["bookSha256"]),
        Index(value = ["dirty"]),
    ],
)
data class LegacySyncRecordEntity(
    @PrimaryKey val recordKey: String,
    val bookSha256: String?,
    val kind: String,
    val entityId: String,
    val payloadJson: String?,
    val deleted: Boolean,
    val physicalTime: Long,
    val logicalCounter: Long,
    val originDeviceId: String,
    val dirty: Boolean,
)
