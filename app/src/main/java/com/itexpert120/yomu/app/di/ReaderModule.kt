package com.itexpert120.yomu.app.di

import com.itexpert120.yomu.core.reader.ReaderEngine
import com.itexpert120.yomu.data.reader.readium.ReadiumReaderEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ReaderModule {

    @Binds
    @Singleton
    abstract fun bindReaderEngine(impl: ReadiumReaderEngine): ReaderEngine
}
