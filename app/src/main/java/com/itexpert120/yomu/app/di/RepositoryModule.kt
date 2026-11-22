package com.itexpert120.yomu.app.di

import com.itexpert120.yomu.data.bookmarks.BookmarkRepository
import com.itexpert120.yomu.data.bookmarks.RoomBookmarkRepository
import com.itexpert120.yomu.data.books.BookRepository
import com.itexpert120.yomu.data.books.RoomBookRepository
import com.itexpert120.yomu.data.highlights.HighlightRepository
import com.itexpert120.yomu.data.highlights.RoomHighlightRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindBookRepository(impl: RoomBookRepository): BookRepository

    @Binds
    @Singleton
    abstract fun bindHighlightRepository(impl: RoomHighlightRepository): HighlightRepository

    @Binds
    @Singleton
    abstract fun bindBookmarkRepository(impl: RoomBookmarkRepository): BookmarkRepository
}
