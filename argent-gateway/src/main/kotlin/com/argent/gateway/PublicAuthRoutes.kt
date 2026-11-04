package com.argent.gateway

import com.argent.gateway.api.ErrorResponse
import com.argent.gateway.api.GoogleAuthConfigResponse
import com.argent.gateway.api.GoogleLoginRequest
import com.argent.gateway.api.GoogleNonceResponse
import com.argent.gateway.api.InvitationRegistrationRequest
import com.argent.gateway.api.LoginRequest
import com.argent.gateway.api.RefreshRequest
import com.argent.gateway.api.RegisterRequest
import com.argent.gateway.api.TokenResponse
import com.argent.gateway.auth.AuthService
import com.argent.gateway.auth.GoogleIdentityService
import com.argent.gateway.config.AppConfig
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

internal fun Route.publicAuthRoutes(
    config: AppConfig,
    auth: AuthService,
    googleIdentity: GoogleIdentityService,
    seedPublicPaperBalance: (String) -> Unit,
    seedBalanceForTrader: (String) -> Unit,
) {
    get("/api/v1/auth/google/config") {
        call.respond(
            GoogleAuthConfigResponse(
                googleIdentity.configured(),
                config.googleWebClientId,
                config.googleDesktopClientId,
            ),
        )
    }
    get("/api/v1/auth/google/nonce") {
        if (!googleIdentity.configured()) {
            return@get call.respond(
                HttpStatusCode.ServiceUnavailable,
                ErrorResponse("Google ile giriş için OAuth istemci kimliklerini yapılandırın"),
            )
        }
        call.respond(GoogleNonceResponse(googleIdentity.issueNonce()))
    }
    post("/api/v1/auth/register") {
        if (!config.publicSignupEnabled) {
            return@post call.respond(
                HttpStatusCode.Forbidden,
                ErrorResponse("Yeni hesap kaydı şu anda kapalı"),
            )
        }
        val body = call.receive<RegisterRequest>()
        val result = auth.registerPublic(body.email, body.password, body.displayName)
        seedPublicPaperBalance(result.user.id)
        val tokens = result.tokens
        call.respond(
            HttpStatusCode.Created,
            TokenResponse(tokens.accessToken, tokens.refreshToken, tokens.expiresInSeconds),
        )
    }
    post("/api/v1/auth/google") {
        if (!config.publicSignupEnabled) {
            return@post call.respond(
                HttpStatusCode.Forbidden,
                ErrorResponse("Yeni hesap kaydı şu anda kapalı"),
            )
        }
        val body = call.receive<GoogleLoginRequest>()
        val profile = googleIdentity.verify(body.idToken, body.nonce)
        val result = auth.loginWithGoogle(profile)
        if (result.created) seedPublicPaperBalance(result.accountId)
        val tokens = result.tokens
        call.respond(TokenResponse(tokens.accessToken, tokens.refreshToken, tokens.expiresInSeconds))
    }
    post("/api/v1/auth/register/invitation") {
        val body = call.receive<InvitationRegistrationRequest>()
        val tokens = auth.registerFromInvitation(body.email, body.password, body.displayName, body.token)
        call.respond(
            HttpStatusCode.Created,
            TokenResponse(tokens.accessToken, tokens.refreshToken, tokens.expiresInSeconds),
        )
    }
    post("/api/v1/auth/login") {
        val body = call.receive<LoginRequest>()
        val tokens = auth.login(body.email, body.password)
        seedBalanceForTrader(tokens.accessToken)
        call.respond(TokenResponse(tokens.accessToken, tokens.refreshToken, tokens.expiresInSeconds))
    }
    post("/api/v1/auth/refresh") {
        val body = call.receive<RefreshRequest>()
        val tokens = auth.refresh(body.refreshToken)
        call.respond(TokenResponse(tokens.accessToken, tokens.refreshToken, tokens.expiresInSeconds))
    }
    post("/api/v1/auth/logout") {
        val body = call.receive<RefreshRequest>()
        call.respond(mapOf("revoked" to auth.revokeRefreshToken(body.refreshToken)))
    }
}
