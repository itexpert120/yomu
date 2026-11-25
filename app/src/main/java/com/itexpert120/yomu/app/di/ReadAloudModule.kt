package com.itexpert120.yomu.app.di

import com.itexpert120.yomu.data.readaloud.AndroidAudioOutput
import com.itexpert120.yomu.data.readaloud.AndroidSpeechEngine
import com.itexpert120.yomu.data.readaloud.ReadAloudAudioOutput
import com.itexpert120.yomu.data.readaloud.ReadAloudHost
import com.itexpert120.yomu.data.readaloud.ReadAloudScope
import com.itexpert120.yomu.data.readaloud.ReadAloudSettingsStore
import com.itexpert120.yomu.data.readaloud.ServiceReadAloudHost
import com.itexpert120.yomu.data.readaloud.SpeechEngine
import com.itexpert120.yomu.data.settings.ReadAloudSettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ReadAloudModule {
    @Binds
    abstract fun bindSpeechEngineFactory(impl: AndroidSpeechEngine.Factory): SpeechEngine.Factory

    @Binds
    abstract fun bindAudioOutput(impl: AndroidAudioOutput): ReadAloudAudioOutput

    @Binds
    abstract fun bindHost(impl: ServiceReadAloudHost): ReadAloudHost

    @Binds
    abstract fun bindSettingsStore(impl: ReadAloudSettingsRepository): ReadAloudSettingsStore

    companion object {
        @Provides
        @Singleton
        @ReadAloudScope
        fun provideReadAloudScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
}
