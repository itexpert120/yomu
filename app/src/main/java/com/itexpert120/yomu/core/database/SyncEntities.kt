package com.itexpert120.yomu.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Small sync state: cursor, clock, device id, and pending "continue from" progress offers. */
@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val key: String,
    val value: String,
)

/**
 * The sync ledger: one row per logical record (e.g. `bookmark/<id>`). [payloadJson] is the payload
 * as last reconciled with the group, so a fresh local snapshot that differs from it is a local
 * change. The HLC columns order changes across devices; [dirty] rows still need pushing.
 * Rows outlive their book on this device so deleting a local copy never deletes synced data.
 */
@Entity(
    tableName = "sync_records",
    indices = [
        Index(value = ["bookSha256"]),
        Index(value = ["dirty"]),
    ],
)
data class SyncLedgerEntity(
    @PrimaryKey val recordKey: String,
    val bookSha256: String?,
    val kind: String,
    val entityId: String,
    // null once the record is deleted.
    val payloadJson: String?,
    val deleted: Boolean,
    val physicalTime: Long,
    val logicalCounter: Long,
    val originDeviceId: String,
    val dirty: Boolean,
)
