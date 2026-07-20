package com.pressureagent.mobile.di

import android.content.Context
import com.pressureagent.mobile.BuildConfig
import com.pressureagent.mobile.data.voice.AndroidVoiceOutputProvider
import com.pressureagent.mobile.data.voice.MockVoiceInputProvider
import com.pressureagent.mobile.data.voice.MockVoiceOutputProvider
import com.pressureagent.mobile.data.voice.SherpaVoiceInputProvider
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
 * Mock mode (USE_MOCK_AGENT=true):  Mock ASR + Mock TTS
 * Real mode (USE_MOCK_AGENT=false): sherpa-onnx offline ASR + Android TextToSpeech
 */
@Module
@InstallIn(SingletonComponent::class)
object VoiceModule {

    @Provides
    @Singleton
    fun provideVoiceInputProvider(
        @ApplicationContext context: Context,
    ): VoiceInputProvider =
        if (BuildConfig.USE_MOCK_AGENT) MockVoiceInputProvider()
        else SherpaVoiceInputProvider(context)

    @Provides
    @Singleton
    fun provideVoiceOutputProvider(
        @ApplicationContext context: Context,
    ): VoiceOutputProvider =
        if (BuildConfig.USE_MOCK_AGENT) MockVoiceOutputProvider()
        else AndroidVoiceOutputProvider(context).also { it.initialize() }
}
