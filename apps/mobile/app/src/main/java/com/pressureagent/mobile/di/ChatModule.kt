package com.pressureagent.mobile.di

import android.content.Context
import com.pressureagent.mobile.BuildConfig
import com.pressureagent.mobile.data.local.CalendarHelper
import com.pressureagent.mobile.data.mock.MockChatRepository
import com.pressureagent.mobile.data.remote.ChatApiService
import com.pressureagent.mobile.data.remote.ChatSseClient
import com.pressureagent.mobile.data.repository.ChatRepository
import com.pressureagent.mobile.data.repository.DefaultChatRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ChatModule {

    @Provides
    @Singleton
    fun provideCalendarHelper(
        @ApplicationContext context: Context,
    ): CalendarHelper = CalendarHelper(context)

    @Provides
    @Singleton
    fun provideChatSseClient(
        @Named("baseUrl") baseUrl: String,
        json: Json,
    ): ChatSseClient = ChatSseClient(baseUrl = baseUrl, json = json)

    @Provides
    @Singleton
    fun provideChatApiService(retrofit: retrofit2.Retrofit): ChatApiService =
        retrofit.create(ChatApiService::class.java)

    @Provides
    @Singleton
    fun provideChatRepository(
        api: ChatApiService,
        sseClient: ChatSseClient,
        calendarHelper: CalendarHelper,
    ): ChatRepository = if (BuildConfig.USE_MOCK_AGENT) {
        MockChatRepository(calendarHelper)
    } else {
        DefaultChatRepository(api = api, sseClient = sseClient)
    }
}
