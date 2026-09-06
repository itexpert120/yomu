package com.itexpert120.yomu.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Minimal durable idempotency receipt; contains no reading-history payload. */
@Entity(tableName = "reading_write_receipts")
data class ReadingWriteReceipt(@PrimaryKey val id: String)
