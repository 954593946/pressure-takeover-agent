package com.pressureagent.mobile.data.remote

import com.pressureagent.mobile.domain.model.Event
import com.pressureagent.mobile.domain.model.EventResponse
import com.pressureagent.mobile.domain.model.WorldState
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Retrofit interface matching the agent-api OpenAPI contract v0.2.
 */
interface AgentApiService {

    @GET("health")
    suspend fun getHealth(): HealthResponse

    @GET("v1/state")
    suspend fun getWorldState(): WorldState

    @POST("v1/event")
    suspend fun submitEvent(@Body event: Event): EventResponse

    @POST("v1/session/reset")
    suspend fun resetSession(@Body body: ResetRequest): WorldState
}

@kotlinx.serialization.Serializable
data class ResetRequest(
    val scenario_id: String = "happy-path",
)

@kotlinx.serialization.Serializable
data class HealthResponse(
    val status: String,
)
