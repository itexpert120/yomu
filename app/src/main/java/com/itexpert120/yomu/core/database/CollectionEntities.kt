package com.itexpert120.yomu.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A user collection. [id] is an app-generated UUID so collections match across synced devices. */
@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val position: Int,
    val createdAt: Long,
)

/**
 * A book's membership in a collection. No foreign key to [CollectionEntity]: a synced membership
 * may arrive before its collection does, and rows for an unknown collection are simply not shown.
 * Deleting a collection or a book removes its rows explicitly.
 */
@Entity(
    tableName = "book_collections",
    primaryKeys = ["bookId", "collectionId"],
    indices = [Index(value = ["collectionId"])],
)
data class BookCollectionEntity(
    val bookId: String,
    val collectionId: String,
)
