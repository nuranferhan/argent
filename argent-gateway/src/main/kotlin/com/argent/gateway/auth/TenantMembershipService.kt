package com.argent.gateway.auth

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

/** Workspace membership and invitation operations, separated from credential authentication. */
internal class TenantMembershipService(
    private val db: Database,
    private val createTokens: (String, UserRole, String) -> AuthService.TokenPair,
    private val digestToken: (String) -> String,
) {
    fun memberships(userId: String): List<TenantMembership> =
        transaction(db) {
            Memberships.selectAll().where { (Memberships.userId eq userId) and (Memberships.active eq true) }.mapNotNull {
                    membership ->
                val tenant =
                    Tenants.selectAll().where {
                        (Tenants.id eq membership[Memberships.tenantId]) and (Tenants.active eq true)
                    }.firstOrNull() ?: return@mapNotNull null
                TenantMembership(
                    tenant[Tenants.id],
                    tenant[Tenants.name],
                    TenantRole.valueOf(membership[Memberships.role]),
                )
            }
        }

    fun tenantMembers(
        tenantId: String,
        actorUserId: String,
    ): List<TenantMember> =
        transaction(db) {
            val actorRole = membershipRole(tenantId, actorUserId)
            require(
                actorRole == TenantRole.OWNER || actorRole == TenantRole.ADMIN,
            ) { "Tenant administrator role required" }
            Memberships.selectAll().where { (Memberships.tenantId eq tenantId) and (Memberships.active eq true) }.mapNotNull {
                    membership ->
                val user =
                    Users.selectAll().where { Users.id eq membership[Memberships.userId] }.firstOrNull()
                        ?: return@mapNotNull null
                TenantMember(
                    user[Users.id],
                    user[Users.email],
                    user[Users.displayName],
                    TenantRole.valueOf(membership[Memberships.role]),
                )
            }.sortedBy { it.email }
        }

    fun updateTenantRole(
        tenantId: String,
        actorUserId: String,
        targetUserId: String,
        newRole: TenantRole,
    ) = transaction(db) {
        require(newRole != TenantRole.OWNER) { "Owner role cannot be assigned or changed" }
        val actorRole = membershipRole(tenantId, actorUserId)
        val targetRole = membershipRole(tenantId, targetUserId)
        require(actorRole == TenantRole.OWNER || actorRole == TenantRole.ADMIN) { "Tenant administrator role required" }
        require(targetRole != null) { "Tenant member not found" }
        require(targetRole != TenantRole.OWNER) { "Owner role cannot be changed" }
        if (actorRole == TenantRole.ADMIN) {
            require(
                targetRole in setOf(TenantRole.TRADER, TenantRole.VIEWER),
            ) { "Tenant admin cannot manage another admin" }
            require(
                newRole in setOf(TenantRole.TRADER, TenantRole.VIEWER),
            ) { "Tenant admin cannot grant administrator role" }
        }
        Memberships.update({
            (Memberships.tenantId eq tenantId) and (Memberships.userId eq targetUserId)
        }) { it[role] = newRole.name }
    }

    fun hasMembership(
        userId: String,
        tenantId: String,
    ): Boolean =
        transaction(db) {
            Memberships.selectAll().where {
                (Memberships.userId eq userId) and (Memberships.tenantId eq tenantId) and (Memberships.active eq true)
            }.count() > 0 && Tenants.selectAll().where {
                (Tenants.id eq tenantId) and (Tenants.active eq true)
            }.count() > 0
        }

    fun tenantAccess(
        userId: String,
        tenantId: String,
    ): TenantAccess? =
        transaction(db) {
            val membership =
                Memberships.selectAll().where {
                    (Memberships.userId eq userId) and (Memberships.tenantId eq tenantId) and (Memberships.active eq true)
                }.firstOrNull() ?: return@transaction null
            if (Tenants.selectAll().where { (Tenants.id eq tenantId) and (Tenants.active eq true) }.empty()) return@transaction null
            val account =
                TenantAccounts.selectAll().where {
                    (TenantAccounts.userId eq userId) and (TenantAccounts.tenantId eq tenantId)
                }.firstOrNull() ?: return@transaction null
            TenantAccess(TenantRole.valueOf(membership[Memberships.role]), account[TenantAccounts.accountId])
        }

    fun switchTenant(
        userId: String,
        tenantId: String,
    ): AuthService.TokenPair =
        transaction(db) {
            val user =
                Users.selectAll().where {
                    Users.id eq userId
                }.firstOrNull() ?: throw IllegalArgumentException("User not found")
            require(user[Users.active]) { "Account disabled" }
            require(hasMembership(userId, tenantId)) { "Tenant membership required" }
            createTokens(userId, UserRole.valueOf(user[Users.role]), tenantId)
        }

    fun deactivateMember(
        tenantId: String,
        actorUserId: String,
        targetUserId: String,
    ): String =
        transaction(db) {
            require(actorUserId != targetUserId) { "You cannot revoke your own membership" }
            val actorRole = membershipRole(tenantId, actorUserId)
            val targetRole = membershipRole(tenantId, targetUserId)
            require(
                actorRole == TenantRole.OWNER || actorRole == TenantRole.ADMIN,
            ) { "Tenant administrator role required" }
            require(targetRole != null) { "Tenant member not found" }
            require(targetRole != TenantRole.OWNER) { "The workspace owner cannot be removed" }
            if (actorRole == TenantRole.ADMIN) {
                require(targetRole in setOf(TenantRole.TRADER, TenantRole.VIEWER)) {
                    "Tenant admin cannot remove another admin"
                }
            }
            Memberships.update({
                (Memberships.tenantId eq tenantId) and (Memberships.userId eq targetUserId) and (Memberships.active eq true)
            }) { it[active] = false }
            RefreshTokens.update({
                (RefreshTokens.userId eq targetUserId) and (RefreshTokens.tenantId eq tenantId) and (RefreshTokens.revoked eq false)
            }) { it[revoked] = true }
            TenantAccounts.selectAll().where {
                (TenantAccounts.tenantId eq tenantId) and (TenantAccounts.userId eq targetUserId)
            }.first()[TenantAccounts.accountId]
        }

    fun deactivateWorkspace(
        tenantId: String,
        ownerUserId: String,
    ) = transaction(db) {
        require(membershipRole(tenantId, ownerUserId) == TenantRole.OWNER) { "Workspace owner role required" }
        val activeMembers =
            Memberships.selectAll().where {
                (Memberships.tenantId eq tenantId) and (Memberships.active eq true)
            }.count()
        require(activeMembers == 1L) { "Remove all other members before closing the workspace" }
        Tenants.update({ (Tenants.id eq tenantId) and (Tenants.active eq true) }) { it[active] = false }
        RefreshTokens.update({
            (RefreshTokens.tenantId eq tenantId) and (RefreshTokens.revoked eq false)
        }) { it[revoked] = true }
    }

    fun createInvitation(
        tenantId: String,
        actorUserId: String,
        email: String,
        role: TenantRole,
    ): TenantInvitation =
        transaction(db) {
            require(role != TenantRole.OWNER) { "Owner role cannot be assigned by invitation" }
            val actorRole = membershipRole(tenantId, actorUserId)
            require(
                actorRole == TenantRole.OWNER || actorRole == TenantRole.ADMIN,
            ) { "Tenant administrator role required" }
            if (actorRole == TenantRole.ADMIN) {
                require(role in setOf(TenantRole.TRADER, TenantRole.VIEWER)) {
                    "Tenant admin cannot invite another administrator"
                }
            }
            val normalized = email.trim().lowercase()
            require(normalized.contains("@") && normalized.length in 5..255) { "Invalid email" }
            val rawToken = UUID.randomUUID().toString() + UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val expiry = now + INVITATION_TTL_MS
            Invitations.insert {
                it[id] = UUID.randomUUID().toString()
                it[Invitations.tenantId] = tenantId
                it[Invitations.email] = normalized
                it[Invitations.role] = role.name
                it[tokenHash] = digestToken(rawToken)
                it[expiresAt] = expiry
                it[acceptedAt] = null
                it[createdBy] = actorUserId
                it[createdAt] = now
            }
            TenantInvitation(tenantId, normalized, role, rawToken, expiry)
        }

    fun acceptInvitation(
        userId: String,
        rawToken: String,
    ): TenantMembership =
        transaction(db) {
            val user =
                Users.selectAll().where {
                    Users.id eq userId
                }.firstOrNull() ?: throw IllegalArgumentException("User not found")
            val invite =
                Invitations.selectAll().where { Invitations.tokenHash eq digestToken(rawToken) }.firstOrNull()
                    ?: throw IllegalArgumentException("Invalid invitation")
            require(user[Users.email] == invite[Invitations.email]) { "Invitation email does not match account" }
            val tenantId = invite[Invitations.tenantId]
            val existing =
                Memberships.selectAll().where {
                    (Memberships.tenantId eq tenantId) and (Memberships.userId eq userId)
                }.firstOrNull()
            if (invite[Invitations.acceptedAt] != null) {
                require(existing?.get(Memberships.active) == true) { "Invitation expired or already used" }
                val tenant = Tenants.selectAll().where { Tenants.id eq tenantId }.first()
                return@transaction TenantMembership(
                    tenant[Tenants.id],
                    tenant[Tenants.name],
                    TenantRole.valueOf(existing[Memberships.role]),
                )
            }
            require(invite[Invitations.expiresAt] >= System.currentTimeMillis()) { "Invitation expired" }
            require(existing == null || !existing[Memberships.active]) { "Already a tenant member" }
            val now = System.currentTimeMillis()
            if (existing == null) {
                Memberships.insert {
                    it[Memberships.tenantId] = tenantId
                    it[Memberships.userId] = userId
                    it[Memberships.role] = invite[Invitations.role]
                    it[Memberships.active] = true
                    it[Memberships.createdAt] = now
                }
            } else {
                Memberships.update({
                    (Memberships.tenantId eq tenantId) and (Memberships.userId eq userId)
                }) {
                    it[Memberships.active] = true
                    it[Memberships.role] = invite[Invitations.role]
                }
            }
            if (TenantAccounts.selectAll().where {
                    (TenantAccounts.tenantId eq tenantId) and (TenantAccounts.userId eq userId)
                }.empty()
            ) {
                TenantAccounts.insert {
                    it[TenantAccounts.tenantId] = tenantId
                    it[TenantAccounts.userId] = userId
                    it[accountId] = UUID.randomUUID().toString()
                    it[createdAt] = now
                }
            }
            val accepted =
                Invitations.update({
                    (Invitations.id eq invite[Invitations.id]) and Invitations.acceptedAt.isNull()
                }) { it[acceptedAt] = now }
            require(accepted == 1) { "Invitation has already been used" }
            val tenant = Tenants.selectAll().where { Tenants.id eq tenantId }.first()
            TenantMembership(tenant[Tenants.id], tenant[Tenants.name], TenantRole.valueOf(invite[Invitations.role]))
        }

    private fun membershipRole(
        tenantId: String,
        userId: String,
    ): TenantRole? =
        Memberships.selectAll().where {
            (Memberships.tenantId eq tenantId) and (Memberships.userId eq userId) and (Memberships.active eq true)
        }.firstOrNull()?.let { TenantRole.valueOf(it[Memberships.role]) }

    companion object {
        private const val INVITATION_TTL_MS = 7L * 24 * 60 * 60 * 1000
    }
}
