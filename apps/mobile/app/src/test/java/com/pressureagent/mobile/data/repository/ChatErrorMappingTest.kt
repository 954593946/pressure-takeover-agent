package com.pressureagent.mobile.data.repository

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class ChatErrorMappingTest {

    @Test
    fun `authentication and conflict errors are not retryable`() {
        assertError(httpError(401), "CHAT_AUTH_REQUIRED", false)
        assertError(httpError(409), "CHAT_CONFLICT", false)
    }

    @Test
    fun `rate limit and server errors are retryable`() {
        assertError(httpError(429), "CHAT_RATE_LIMITED", true)
        assertError(httpError(503), "CHAT_UPSTREAM_UNAVAILABLE", true)
    }

    @Test
    fun `transport errors are retryable but client errors are not`() {
        assertError(IOException("offline"), "CHAT_TRANSPORT_UNAVAILABLE", true)
        assertError(IllegalStateException("invalid response"), "CHAT_CLIENT_ERROR", false)
    }

    private fun assertError(error: Throwable, code: String, retryable: Boolean) {
        val mapped = error.toChatError()
        assertEquals(code, mapped.code)
        assertEquals(retryable, mapped.retryable)
    }

    private fun httpError(status: Int): HttpException {
        val body = "{}".toResponseBody("application/json".toMediaType())
        return HttpException(Response.error<Any>(status, body))
    }
}
