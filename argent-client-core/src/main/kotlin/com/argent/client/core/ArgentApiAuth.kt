package com.argent.client.core

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

suspend fun ArgentApi.register(
    email: String,
    password: String,
    name: String,
): TokenResponse {
    val tokens: TokenResponse = post("/api/v1/auth/register", RegisterRequest(email, password, name))
    saveTokens(tokens)
    return tokens
}

suspend fun ArgentApi.googleAuthConfig(): GoogleAuthConfigDto = get("/api/v1/auth/google/config")

suspend fun ArgentApi.googleNonce(): GoogleNonceDto = get("/api/v1/auth/google/nonce")

suspend fun ArgentApi.loginWithGoogle(
    idToken: String,
    nonce: String,
): TokenResponse {
    val tokens: TokenResponse = post("/api/v1/auth/google", GoogleLoginRequestDto(idToken, nonce))
    saveTokens(tokens)
    return tokens
}

suspend fun ArgentApi.registerFromInvitation(
    email: String,
    password: String,
    name: String,
    token: String,
): TokenResponse {
    val tokens: TokenResponse =
        post("/api/v1/auth/register/invitation", InvitationRegistrationRequest(email, password, name, token))
    saveTokens(tokens)
    return tokens
}

suspend fun ArgentApi.login(
    email: String,
    password: String,
): TokenResponse {
    val tokens: TokenResponse = post("/api/v1/auth/login", LoginRequest(email, password))
    saveTokens(tokens)
    return tokens
}

suspend fun ArgentApi.me(): MeResponse = get("/api/v1/me")

suspend fun ArgentApi.tenants(): List<TenantDto> = get("/api/v1/tenants")

suspend fun ArgentApi.tenantMembers(): List<TenantMemberDto> = get("/api/v1/tenants/members")

suspend fun ArgentApi.inviteTenantMember(
    email: String,
    role: String,
): TenantInvitationDto = post("/api/v1/tenants/invitations", TenantInviteRequest(email, role))

suspend fun ArgentApi.acceptTenantInvitation(token: String): TenantMembershipDto =
    post("/api/v1/tenants/invitations/accept", TenantInviteAcceptRequest(token))

suspend fun ArgentApi.updateTenantMemberRole(
    userId: String,
    role: String,
) {
    val response =
        client.patch("$baseUrl/api/v1/tenants/members/$userId/role") {
            contentType(ContentType.Application.Json)
            token?.let { header("Authorization", "Bearer $it") }
            setBody(TenantRoleUpdateRequest(role))
        }
    if (!response.status.isSuccess()) fail(response.bodyAsText())
}

suspend fun ArgentApi.removeTenantMember(userId: String) {
    val response =
        client.delete("$baseUrl/api/v1/tenants/members/$userId") {
            token?.let { header("Authorization", "Bearer $it") }
        }
    if (!response.status.isSuccess()) fail(response.bodyAsText())
}

suspend fun ArgentApi.closeWorkspace() {
    val response =
        client.delete("$baseUrl/api/v1/tenants/current") {
            token?.let { header("Authorization", "Bearer $it") }
        }
    if (!response.status.isSuccess()) fail(response.bodyAsText())
    clearSession()
}

suspend fun ArgentApi.switchTenant(tenantId: String) {
    val tokens: TokenResponse = post("/api/v1/tenants/switch", TenantSwitchRequest(tenantId))
    saveTokens(tokens)
}
