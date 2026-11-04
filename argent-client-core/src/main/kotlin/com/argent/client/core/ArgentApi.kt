package com.argent.client.core

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class ArgentApi(var baseUrl: String = "http://127.0.0.1:8080") {
    var token: String? = null
    internal var refreshToken: String? = null
    internal val newsImageCache =
        object : LinkedHashMap<String, ByteArray>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?): Boolean = size > 16
        }

    fun clearSession() {
        token = null
        refreshToken = null
    }

    suspend fun logout() {
        val currentRefreshToken = refreshToken
        if (currentRefreshToken != null) {
            runCatching {
                client.post("$baseUrl/api/v1/auth/logout") {
                    contentType(ContentType.Application.Json)
                    setBody(RefreshRequest(currentRefreshToken))
                }
            }
        }
        clearSession()
    }

    internal val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    internal val client =
        HttpClient(CIO) {
            install(ContentNegotiation) { json(json) }
            install(WebSockets)
            install(HttpTimeout) {
                requestTimeoutMillis = 75_000
                connectTimeoutMillis = 5_000
                socketTimeoutMillis = 75_000
            }
            expectSuccess = false
        }

    internal suspend inline fun <reified T> get(path: String): T {
        var response =
            client.get("$baseUrl$path") {
                token?.let { header("Authorization", "Bearer $it") }
            }
        if (response.status == HttpStatusCode.Unauthorized && refreshSession()) {
            response = client.get("$baseUrl$path") { token?.let { header("Authorization", "Bearer $it") } }
        }
        if (!response.status.isSuccess()) fail(response.bodyAsText())
        return response.body()
    }

    internal suspend inline fun <reified B, reified T> post(
        path: String,
        body: B,
    ): T {
        var response =
            client.post("$baseUrl$path") {
                contentType(ContentType.Application.Json)
                token?.let { header("Authorization", "Bearer $it") }
                setBody(body)
            }
        if (path != "/api/v1/auth/refresh" && response.status == HttpStatusCode.Unauthorized && refreshSession()) {
            response =
                client.post("$baseUrl$path") {
                    contentType(ContentType.Application.Json)
                    token?.let { header("Authorization", "Bearer $it") }
                    setBody(body)
                }
        }
        if (!response.status.isSuccess()) fail(response.bodyAsText())
        return response.body()
    }

    internal fun saveTokens(tokens: TokenResponse) {
        token = tokens.accessToken
        refreshToken = tokens.refreshToken
    }

    internal suspend fun refreshSession(): Boolean {
        val currentRefreshToken = refreshToken ?: return false
        return runCatching {
            val response =
                client.post("$baseUrl/api/v1/auth/refresh") {
                    contentType(ContentType.Application.Json)
                    setBody(RefreshRequest(currentRefreshToken))
                }
            if (!response.status.isSuccess()) return false
            saveTokens(response.body())
            true
        }.getOrDefault(false)
    }

    internal fun fail(body: String): Nothing {
        val parsed = runCatching { json.decodeFromString(ErrorBody.serializer(), body) }.getOrNull()
        error(parsed?.error ?: parsed?.reason ?: body.take(280))
    }
}
