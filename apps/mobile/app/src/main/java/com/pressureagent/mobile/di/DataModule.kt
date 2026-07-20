package com.pressureagent.mobile.di

import com.pressureagent.mobile.BuildConfig
import com.pressureagent.mobile.data.remote.AgentApiService
import com.pressureagent.mobile.data.remote.SseClient
import com.pressureagent.mobile.data.repository.DefaultWorldStateRepository
import com.pressureagent.mobile.data.repository.WorldStateRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideSseClient(
        @Named("baseUrl") baseUrl: String,
        json: Json,
    ): SseClient = SseClient(baseUrl = baseUrl, json = json, token = BuildConfig.AGENT_API_TOKEN)

    @Provides
    @Singleton
    fun provideWorldStateRepository(
        api: AgentApiService,
        sseClient: SseClient,
    ): WorldStateRepository = DefaultWorldStateRepository(api = api, sseClient = sseClient)
}
