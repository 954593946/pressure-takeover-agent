package com.pressureagent.mobile.di

import android.content.Context
import com.pressureagent.mobile.BuildConfig
import com.pressureagent.mobile.data.voice.AndroidVoiceOutputProvider
import com.pressureagent.mobile.data.voice.MockVoiceInputProvider
import com.pressureagent.mobile.data.voice.MockVoiceOutputProvider
import com.pressureagent.mobile.domain.voice.VoiceInputProvider
import com.pressureagent.mobile.domain.voice.VoiceOutputProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Voice I/O bindings.
 *
 * Currently uses mock implementations. To switch to real ASR/TTS:
 * 1. Replace [MockVoiceInputProvider] → AndroidVoiceInputProvider
 * 2. Replace [MockVoiceOutputProvider] → AndroidVoiceOutputProvider(...)
 * 3. No UI code changes needed.
 */
@Module
@InstallIn(SingletonComponent::class)
object VoiceModule {

    @Provides
    @Singleton
    fun provideVoiceInputProvider(): VoiceInputProvider = MockVoiceInputProvider()

    @Provides
    @Singleton
    fun provideVoiceOutputProvider(): VoiceOutputProvider = MockVoiceOutputProvider()
}
