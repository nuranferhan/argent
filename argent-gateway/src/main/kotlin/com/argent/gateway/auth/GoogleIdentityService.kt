package com.argent.gateway.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.RSAKeyProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.cert.CertificateFactory
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom

/** Validates Google-issued ID tokens at the gateway; client-supplied profile data is never trusted. */
data class GoogleProfile(val subject: String, val email: String, val name: String?, val emailVerified: Boolean)

class GoogleIdentityService(clientIds: Set<String>) {
    private val audiences = clientIds.map(String::trim).filter(String::isNotEmpty).toSet()
    private val nonces = ConcurrentHashMap<String, Long>()
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var cachedKeys: Pair<Long, Map<String, RSAPublicKey>>? = null

    fun configured() = audiences.isNotEmpty()

    fun issueNonce(): String {
        check(configured()) { "Google ile giriş yapılandırılmamış" }
        val now = System.currentTimeMillis()
        nonces.entries.removeIf { it.value < now }
        val nonce = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
            .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        nonces[nonce] = now + NONCE_TTL_MS
        return nonce
    }

    fun verify(idToken: String, nonce: String): GoogleProfile {
        require(configured()) { "Google ile giriş yapılandırılmamış" }
        require(idToken.length in 100..16_384 && nonce.length in 20..128) { "Geçersiz Google kimlik bilgisi" }
        val expiresAt = nonces.remove(nonce) ?: throw IllegalArgumentException("Google giriş isteği geçersiz veya süresi dolmuş")
        require(expiresAt >= System.currentTimeMillis()) { "Google giriş isteğinin süresi dolmuş" }
        val decoded = JWT.decode(idToken)
        require(decoded.algorithm == "RS256") { "Desteklenmeyen Google token imzası" }
        val issuer = decoded.issuer
        require(issuer == "accounts.google.com" || issuer == "https://accounts.google.com") { "Google token issuer geçersiz" }
        val key = googleKeys()[decoded.keyId] ?: throw IllegalArgumentException("Google imza anahtarı bulunamadı")
        val verified = JWT.require(Algorithm.RSA256(key, null))
            .withIssuer(issuer)
            .withAudience(*audiences.toTypedArray())
            .acceptLeeway(60)
            .build()
            .verify(idToken)
        val tokenNonce = verified.getClaim("nonce").asString()
        require(tokenNonce != null && java.security.MessageDigest.isEqual(tokenNonce.toByteArray(), nonce.toByteArray())) {
            "Google token nonce doğrulanamadı"
        }
        return GoogleProfile(
            subject = verified.subject?.takeIf(String::isNotBlank) ?: throw IllegalArgumentException("Google subject eksik"),
            email = verified.getClaim("email").asString()?.takeIf(String::isNotBlank) ?: throw IllegalArgumentException("Google e-posta adresi eksik"),
            name = verified.getClaim("name").asString(),
            emailVerified = verified.getClaim("email_verified").asBoolean() == true
        )
    }

    private fun googleKeys(): Map<String, RSAPublicKey> {
        val now = System.currentTimeMillis()
        cachedKeys?.takeIf { it.first > now }?.let { return it.second }
        synchronized(this) {
            cachedKeys?.takeIf { it.first > now }?.let { return it.second }
            val connection = URL(GOOGLE_CERTS_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 4_000
            connection.readTimeout = 4_000
            connection.setRequestProperty("Accept", "application/json")
            try {
                require(connection.responseCode == 200) { "Google imza anahtarları alınamadı" }
                val payload = connection.inputStream.use { json.parseToJsonElement(it.readBytes().toString(Charsets.UTF_8)).jsonObject }
                val keys = payload.mapNotNull { (kid, pemValue) ->
                    val pem = pemValue.jsonPrimitive.contentOrNull ?: return@mapNotNull null
                    runCatching {
                        val der = pem.replace("-----BEGIN CERTIFICATE-----", "")
                            .replace("-----END CERTIFICATE-----", "").replace("\n", "").replace("\r", "")
                        val cert = CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(Base64.getDecoder().decode(der)))
                        kid to (cert.publicKey as RSAPublicKey)
                    }.getOrNull()
                }.toMap()
                require(keys.isNotEmpty()) { "Google imza anahtarları boş döndü" }
                val maxAge = Regex("max-age=(\\d+)").find(connection.getHeaderField("Cache-Control").orEmpty())
                    ?.groupValues?.get(1)?.toLongOrNull()?.coerceIn(60, 86_400) ?: 300
                cachedKeys = (now + maxAge * 1000) to keys
                return keys
            } finally { connection.disconnect() }
        }
    }

    companion object {
        private const val GOOGLE_CERTS_URL = "https://www.googleapis.com/oauth2/v1/certs"
        private const val NONCE_TTL_MS = 5 * 60 * 1000L
    }
}
