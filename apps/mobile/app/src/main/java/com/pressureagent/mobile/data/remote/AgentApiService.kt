package com.pressureagent.mobile.data.remote

import com.pressureagent.mobile.domain.model.Event
import com.pressureagent.mobile.domain.model.EventResponse
import com.pressureagent.mobile.domain.model.WorldState
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Retrofit interface matching the agent-api OpenAPI contract.
 * Base URL is injected at construction time (debug → MockAgent, release → real server).
 */
interface AgentApiService {

    @GET("health")
    suspend fun getHealth(): HealthResponse

    @GET("v1/world-state")
    suspend fun getWorldState(): WorldState

    @POST("v1/events")
    suspend fun submitEvent(@Body event: Event): EventResponse
}

@kotlinx.serialization.Serializable
data class HealthResponse(
    val status: String,
)
