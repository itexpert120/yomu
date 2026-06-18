package com.itexpert120.yomu.app.di

import com.itexpert120.yomu.data.books.BookRepository
import com.itexpert120.yomu.data.books.RoomBookRepository
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
}
