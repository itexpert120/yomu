package com.itexpert120.yomu.app.di

import android.content.Context
import androidx.room.Room
import com.itexpert120.yomu.core.database.BookDao
import com.itexpert120.yomu.core.database.BookmarkDao
import com.itexpert120.yomu.core.database.HighlightDao
import com.itexpert120.yomu.core.database.YomuDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): YomuDatabase = Room.databaseBuilder(context, YomuDatabase::class.java, "yomu.db")
        .addMigrations(
            YomuDatabase.MIGRATION_1_2,
            YomuDatabase.MIGRATION_2_3,
            YomuDatabase.MIGRATION_3_4,
            YomuDatabase.MIGRATION_4_5,
            YomuDatabase.MIGRATION_5_6,
            YomuDatabase.MIGRATION_6_7,
            YomuDatabase.MIGRATION_7_8,
            YomuDatabase.MIGRATION_8_9,
        )
        .build()

    @Provides
    fun provideBookDao(database: YomuDatabase): BookDao = database.bookDao()

    @Provides
    fun provideHighlightDao(database: YomuDatabase): HighlightDao = database.highlightDao()

    @Provides
    fun provideBookmarkDao(database: YomuDatabase): BookmarkDao = database.bookmarkDao()
}
