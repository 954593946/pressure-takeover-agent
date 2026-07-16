package com.pressureagent.mobile.di

import com.pressureagent.mobile.BuildConfig
import com.pressureagent.mobile.data.mock.MockAgent
import com.pressureagent.mobile.data.remote.AgentApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Provides
    @Singleton
    @Named("baseUrl")
    fun provideBaseUrl(): String = if (BuildConfig.USE_MOCK_AGENT) {
        MockAgent.MOCK_BASE_URL
    } else {
        BuildConfig.AGENT_API_BASE_URL
    }

    @Provides
    @Singleton
    fun provideMockAgent(): MockAgent? = if (BuildConfig.USE_MOCK_AGENT) MockAgent() else null

    @Provides
    @Singleton
    fun provideOkHttpClient(mockAgent: MockAgent?): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    })
                }
                if (mockAgent != null) {
                    addInterceptor(mockAgent)
                }
            }
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(
        @Named("baseUrl") baseUrl: String,
        okHttpClient: OkHttpClient,
        json: Json,
    ): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideAgentApiService(retrofit: Retrofit): AgentApiService =
        retrofit.create(AgentApiService::class.java)
}
