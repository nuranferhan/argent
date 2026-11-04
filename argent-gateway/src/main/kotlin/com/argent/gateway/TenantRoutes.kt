package com.argent.gateway

import com.argent.gateway.api.ErrorResponse
import com.argent.gateway.api.MeResponse
import com.argent.gateway.api.TenantInvitationDto
import com.argent.gateway.api.TenantInviteAcceptRequest
import com.argent.gateway.api.TenantInviteRequest
import com.argent.gateway.api.TenantMemberDto
import com.argent.gateway.api.TenantMemberRevokedResponse
import com.argent.gateway.api.TenantMembershipDto
import com.argent.gateway.api.TenantRoleUpdateRequest
import com.argent.gateway.api.TenantRoleUpdateResponse
import com.argent.gateway.api.TenantSwitchRequest
import com.argent.gateway.api.TenantWorkspaceClosedResponse
import com.argent.gateway.api.TokenResponse
import com.argent.gateway.auth.TenantRole
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.routing

internal fun io.ktor.server.routing.Route.registerTenantRoutes(ctx: GatewayRouteContext) {
    with(ctx) {
        authenticate("auth") {
            get("/api/v1/me") {
                val p = call.requireArgentPrincipal()
                val user = auth.getUser(p.userId) ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))
                call.respond(
                    MeResponse(user.id, user.email, user.displayName, user.role.name, p.tenantId, p.tenantRole.name),
                )
            }
            get("/api/v1/tenants") {
                val p = call.requireArgentPrincipal()
                call.respond(
                    auth.memberships(p.userId).map { TenantMembershipDto(it.tenantId, it.tenantName, it.role.name) },
                )
            }
            get("/api/v1/tenants/members") {
                val p = call.requireArgentPrincipal()
                if (p.tenantRole !in setOf(TenantRole.OWNER, TenantRole.ADMIN)) {
                    return@get call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse("Tenant administrator role required"),
                    )
                }
                call.respond(
                    auth.tenantMembers(p.tenantId, p.userId).map {
                        TenantMemberDto(it.userId, it.email, it.displayName, it.role.name)
                    },
                )
            }
            patch("/api/v1/tenants/members/{userId}/role") {
                val p = call.requireArgentPrincipal()
                if (p.tenantRole !in setOf(TenantRole.OWNER, TenantRole.ADMIN)) {
                    return@patch call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse("Tenant administrator role required"),
                    )
                }
                val targetUserId = call.parameters["userId"] ?: throw IllegalArgumentException("userId is required")
                val body = call.receive<TenantRoleUpdateRequest>()
                val newRole =
                    runCatching { TenantRole.valueOf(body.role.trim().uppercase()) }.getOrElse {
                        throw IllegalArgumentException("Role must be ADMIN, TRADER, or VIEWER")
                    }
                val targetAccess =
                    auth.tenantAccess(targetUserId, p.tenantId)
                        ?: return@patch call.respond(HttpStatusCode.NotFound, ErrorResponse("Tenant member not found"))
                trading.withAccountLock(targetAccess.accountId) {
                    auth.updateTenantRole(p.tenantId, p.userId, targetUserId, newRole)
                }
                call.respond(TenantRoleUpdateResponse(targetUserId, newRole.name, true))
            }
            delete("/api/v1/tenants/members/{userId}") {
                val p = call.requireArgentPrincipal()
                if (p.tenantRole !in setOf(TenantRole.OWNER, TenantRole.ADMIN)) {
                    return@delete call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse("Tenant administrator role required"),
                    )
                }
                val targetUserId = call.parameters["userId"] ?: throw IllegalArgumentException("userId is required")
                val target =
                    auth.tenantAccess(targetUserId, p.tenantId)
                        ?: return@delete call.respond(HttpStatusCode.NotFound, ErrorResponse("Tenant member not found"))
                val revoked =
                    trading.withAccountLock(target.accountId) {
                        orders.findActiveByUser(target.accountId).forEach { order ->
                            trading.cancel(target.accountId, order.id, order.symbol)
                        }
                        if (orders.findActiveByUser(target.accountId).isNotEmpty()) {
                            false
                        } else {
                            auth.deactivateMember(p.tenantId, p.userId, targetUserId)
                            true
                        }
                    }
                if (!revoked) {
                    return@delete call.respond(
                        HttpStatusCode.Conflict,
                        ErrorResponse("Active orders could not all be cancelled; membership remains active"),
                    )
                }
                call.respond(TenantMemberRevokedResponse(targetUserId, true, true))
            }
            delete("/api/v1/tenants/current") {
                val p = call.requireArgentPrincipal()
                if (p.tenantRole != TenantRole.OWNER) {
                    return@delete call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse("Workspace owner role required"),
                    )
                }
                val closed =
                    trading.withAccountLock(p.accountId) {
                        orders.findActiveByUser(p.accountId).forEach { order ->
                            trading.cancel(p.accountId, order.id, order.symbol)
                        }
                        if (orders.findActiveByUser(p.accountId).isNotEmpty()) {
                            false
                        } else {
                            auth.deactivateWorkspace(p.tenantId, p.userId)
                            true
                        }
                    }
                if (!closed) {
                    return@delete call.respond(
                        HttpStatusCode.Conflict,
                        ErrorResponse("Active orders could not all be cancelled; workspace remains open"),
                    )
                }
                call.respond(TenantWorkspaceClosedResponse(p.tenantId, true))
            }
            post("/api/v1/tenants/switch") {
                val p = call.requireArgentPrincipal()
                val body = call.receive<TenantSwitchRequest>()
                val tokens = auth.switchTenant(p.userId, body.tenantId)
                call.respond(TokenResponse(tokens.accessToken, tokens.refreshToken, tokens.expiresInSeconds))
            }
            post("/api/v1/tenants/invitations") {
                val p = call.requireArgentPrincipal()
                val body = call.receive<TenantInviteRequest>()
                val role =
                    runCatching { TenantRole.valueOf(body.role.trim().uppercase()) }.getOrElse {
                        throw IllegalArgumentException("Tenant role must be TRADER or VIEWER")
                    }
                val invitation = auth.createInvitation(p.tenantId, p.userId, body.email, role)
                call.respond(
                    HttpStatusCode.Created,
                    TenantInvitationDto(
                        invitation.tenantId,
                        invitation.email,
                        invitation.role.name,
                        invitation.token,
                        invitation.expiresAt,
                    ),
                )
            }
            post("/api/v1/tenants/invitations/accept") {
                val p = call.requireArgentPrincipal()
                val body = call.receive<TenantInviteAcceptRequest>()
                val member = auth.acceptInvitation(p.userId, body.token)
                call.respond(TenantMembershipDto(member.tenantId, member.tenantName, member.role.name))
            }
        }
    }
}
