package com.itexpert120.yomu.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [BookEntity::class], version = 1, exportSchema = true)
abstract class YomuDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
}
