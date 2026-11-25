package com.itexpert120.yomu.app.di

import com.itexpert120.yomu.core.reader.ReaderEngine
import com.itexpert120.yomu.core.reader.ReaderRestoreGuard
import com.itexpert120.yomu.data.reader.SwitchingReaderEngine
import com.itexpert120.yomu.data.reader.readium.ReadiumRestoreGuard
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
    abstract fun bindReaderEngine(impl: SwitchingReaderEngine): ReaderEngine

    @Binds
    abstract fun bindReaderRestoreGuard(impl: ReadiumRestoreGuard): ReaderRestoreGuard
}
