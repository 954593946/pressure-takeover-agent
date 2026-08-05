package com.pressureagent.mobile.di

import android.content.Context
import com.pressureagent.mobile.BuildConfig
import com.pressureagent.mobile.data.voice.MockVoiceInputProvider
import com.pressureagent.mobile.data.voice.MockVoiceOutputProvider
import com.pressureagent.mobile.data.voice.SherpaVoiceInputProvider
import com.pressureagent.mobile.data.voice.XunfeiTtsClient
import com.pressureagent.mobile.data.voice.XunfeiVoiceOutputProvider
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
 * Real mode (USE_MOCK_AGENT=false): sherpa-onnx offline ASR + Xunfei super-human-like TTS
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
    fun provideXunfeiTtsClient(): XunfeiTtsClient = XunfeiTtsClient(
        appId = BuildConfig.XUNFEI_TTS_APP_ID,
        apiKey = BuildConfig.XUNFEI_TTS_API_KEY,
        apiSecret = BuildConfig.XUNFEI_TTS_API_SECRET,
    )

    @Provides
    @Singleton
    fun provideVoiceOutputProvider(
        client: XunfeiTtsClient,
    ): VoiceOutputProvider =
        if (BuildConfig.USE_MOCK_AGENT) MockVoiceOutputProvider()
        else XunfeiVoiceOutputProvider(client)
}
