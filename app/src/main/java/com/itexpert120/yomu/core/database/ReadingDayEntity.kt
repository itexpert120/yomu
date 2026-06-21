package com.itexpert120.yomu.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Accumulated reading time for a single calendar day (local). [date] is an ISO `yyyy-MM-dd` string;
 * [seconds] is the total foreground reading time recorded that day. Backs totals + streaks.
 */
@Entity(tableName = "reading_days")
data class ReadingDayEntity(
    @PrimaryKey val date: String,
    val seconds: Long,
)
