package com.argent.terminal

import com.argent.terminal.api.ArgentApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.Desktop
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Native desktop OAuth using loopback redirect and PKCE. */
suspend fun googleDesktopSignIn(api: ArgentApi) {
    val config = api.googleAuthConfig()
    val clientId = config.desktopClientId?.takeIf { config.configured }
        ?: error("Google ile masaüstü girişi için GOOGLE_DESKTOP_CLIENT_ID ayarlanmalı.")
    val nonce = api.googleNonce().nonce
    val encoder = Base64.getUrlEncoder().withoutPadding()
    val verifier = encoder.encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))
    val challenge = encoder.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(StandardCharsets.US_ASCII)))
    val state = encoder.encodeToString(ByteArray(24).also(SecureRandom()::nextBytes))
    val server = HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0)
    val redirectUri = "http://127.0.0.1:${server.address.port}/oauth2callback"
    val result = CompletableFuture<String>()
    server.createContext("/oauth2callback") { exchange ->
        val query = exchange.requestURI.rawQuery.orEmpty().split('&').mapNotNull { part ->
            val pair = part.split('=', limit = 2)
            if (pair.size == 2) URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair[1], "UTF-8") else null
        }.toMap()
        val page = if (query["state"] == state && query["code"] != null) {
            result.complete(query.getValue("code")); "ARGENT Google girişi tamamlandı. Bu pencereyi kapatabilirsiniz."
        } else {
            result.completeExceptionally(IllegalArgumentException(query["error"] ?: "Google OAuth state doğrulanamadı"))
            "Google girişi tamamlanamadı. ARGENT uygulamasına dönün."
        }
        val bytes = "<html><meta charset=\"utf-8\"><body style=\"font:16px sans-serif;background:#10151c;color:#e5e8ed;padding:40px\">$page</body></html>".toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
        exchange.sendResponseHeaders(200, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
    }
    server.start()
    try {
        val encode = { value: String -> URLEncoder.encode(value, "UTF-8") }
        val authUrl = "https://accounts.google.com/o/oauth2/v2/auth?" + listOf(
            "client_id=${encode(clientId)}", "redirect_uri=${encode(redirectUri)}", "response_type=code",
            "scope=${encode("openid email profile")}", "state=${encode(state)}", "nonce=${encode(nonce)}",
            "code_challenge=${encode(challenge)}", "code_challenge_method=S256", "access_type=online"
        ).joinToString("&")
        check(Desktop.isDesktopSupported()) { "Bu ortam varsayılan tarayıcı açmayı desteklemiyor" }
        Desktop.getDesktop().browse(URI(authUrl))
        val code = withContext(Dispatchers.IO) {
            try { result.get(30, TimeUnit.SECONDS) }
            catch (_: TimeoutException) { throw IllegalStateException("Google giriş isteği 30 saniyede tamamlanmadı. Tekrar deneyin.") }
        }
        val form = listOf(
            "code=${encode(code)}", "client_id=${encode(clientId)}", "redirect_uri=${encode(redirectUri)}",
            "grant_type=authorization_code", "code_verifier=${encode(verifier)}"
        ).joinToString("&")
        val request = HttpRequest.newBuilder(URI("https://oauth2.googleapis.com/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form)).build()
        val response = withContext(Dispatchers.IO) { HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()) }
        check(response.statusCode() in 200..299) { "Google token değişimi başarısız (HTTP ${response.statusCode()})" }
        val idToken = Json.parseToJsonElement(response.body()).jsonObject["id_token"]?.jsonPrimitive?.content
            ?: error("Google ID token döndürmedi")
        api.loginWithGoogle(idToken, nonce)
    } finally { server.stop(0) }
}
