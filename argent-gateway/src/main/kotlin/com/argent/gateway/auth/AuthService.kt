package com.argent.gateway.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.Date
import java.util.UUID

enum class UserRole { USER, ADMIN, MARKET_MAKER }

enum class TenantRole { OWNER, ADMIN, TRADER, VIEWER }

data class TenantMembership(val tenantId: String, val tenantName: String, val role: TenantRole)

data class TenantAccess(val role: TenantRole, val accountId: String)

data class TenantMember(val userId: String, val email: String, val displayName: String, val role: TenantRole)

data class TenantInvitation(val tenantId: String, val email: String, val role: TenantRole, val token: String, val expiresAt: Long)

data class AuthenticatedRegistration(val user: UserAccount, val tokens: AuthService.TokenPair)

data class FederatedLoginResult(val tokens: AuthService.TokenPair, val accountId: String, val created: Boolean)

data class UserAccount(
    val id: String,
    val email: String,
    val displayName: String,
    val role: UserRole,
    val createdAt: Long,
    val active: Boolean,
    val tenantId: String,
)

class AuthService(
    private val db: Database,
    private val jwtSecret: String,
    private val accessTtlMs: Long = 3_600_000L,
    private val refreshTtlMs: Long = 30L * 86_400_000,
) {
    private val algorithm = Algorithm.HMAC256(jwtSecret)

    fun initSchema() = AuthSchemaInitializer(db).initSchema()

    private val credentials by lazy {
        AuthCredentialService(db, ::issueTokens, ::defaultTenantId, ::hashToken)
    }

    fun register(
        email: String,
        password: String,
        displayName: String,
        role: UserRole = UserRole.USER,
    ): UserAccount = credentials.register(email, password, displayName, role)

    fun registerPublic(
        email: String,
        password: String,
        displayName: String,
    ): AuthenticatedRegistration = credentials.registerPublic(email, password, displayName)

    fun loginWithGoogle(profile: GoogleProfile): FederatedLoginResult = credentials.loginWithGoogle(profile)

    fun login(
        email: String,
        password: String,
    ): TokenPair = credentials.login(email, password)

    fun verifyPassword(
        userId: String,
        password: String,
    ): Boolean = credentials.verifyPassword(userId, password)

    fun registerFromInvitation(
        email: String,
        password: String,
        displayName: String,
        rawToken: String,
    ): TokenPair = credentials.registerFromInvitation(email, password, displayName, rawToken)

    fun refresh(refreshToken: String): TokenPair {
        val hash = hashToken(refreshToken)
        return transaction(db) {
            val row =
                RefreshTokens.selectAll()
                    .where { (RefreshTokens.tokenHash eq hash) and (RefreshTokens.revoked eq false) }
                    .firstOrNull() ?: throw IllegalArgumentException("Invalid refresh token")
            if (row[RefreshTokens.expiresAt] < Instant.now().toEpochMilli()) {
                throw IllegalArgumentException("Refresh token expired")
            }
            val revoked =
                RefreshTokens.update(
                    { (RefreshTokens.id eq row[RefreshTokens.id]) and (RefreshTokens.revoked eq false) },
                ) {
                    it[RefreshTokens.revoked] = true
                }
            if (revoked != 1) throw IllegalArgumentException("Invalid refresh token")
            val user = Users.selectAll().where { Users.id eq row[RefreshTokens.userId] }.first()
            if (!user[Users.active]) throw IllegalArgumentException("Account disabled")
            val tenantId = row[RefreshTokens.tenantId] ?: defaultTenantId(user[Users.id])
            issueTokens(user[Users.id], UserRole.valueOf(user[Users.role]), tenantId)
        }
    }

    fun revokeRefreshToken(refreshToken: String): Boolean =
        transaction(db) {
            val hash = hashToken(refreshToken)
            RefreshTokens.update({ (RefreshTokens.tokenHash eq hash) and (RefreshTokens.revoked eq false) }) {
                it[RefreshTokens.revoked] = true
            } == 1
        }

    fun getUser(userId: String): UserAccount? =
        transaction(db) {
            val row = Users.selectAll().where { Users.id eq userId }.firstOrNull() ?: return@transaction null
            val tenantId =
                Memberships.selectAll().where {
                    (Memberships.userId eq userId) and (Memberships.active eq true)
                }.firstOrNull()?.get(Memberships.tenantId) ?: return@transaction null
            UserAccount(
                id = row[Users.id],
                email = row[Users.email],
                displayName = row[Users.displayName],
                role = UserRole.valueOf(row[Users.role]),
                createdAt = row[Users.createdAt],
                active = row[Users.active],
                tenantId = tenantId,
            )
        }

    fun findByEmail(email: String): UserAccount? =
        transaction(db) {
            Users.selectAll().where { Users.email eq email.trim().lowercase() }.map {
                UserAccount(
                    id = it[Users.id],
                    email = it[Users.email],
                    displayName = it[Users.displayName],
                    role = UserRole.valueOf(it[Users.role]),
                    createdAt = it[Users.createdAt],
                    active = it[Users.active],
                    tenantId = defaultTenantId(it[Users.id]),
                )
            }.firstOrNull()
        }

    fun promoteToAdmin(email: String): Boolean =
        transaction(db) {
            Users.update({ Users.email eq email.trim().lowercase() }) {
                it[role] = UserRole.ADMIN.name
            } > 0
        }

    private val tenantMemberships by lazy { TenantMembershipService(db, ::issueTokens, ::hashToken) }

    fun memberships(userId: String): List<TenantMembership> = tenantMemberships.memberships(userId)

    fun tenantMembers(
        tenantId: String,
        actorUserId: String,
    ): List<TenantMember> =
        tenantMemberships.tenantMembers(
            tenantId,
            actorUserId,
        )

    fun updateTenantRole(
        tenantId: String,
        actorUserId: String,
        targetUserId: String,
        newRole: TenantRole,
    ) = tenantMemberships.updateTenantRole(tenantId, actorUserId, targetUserId, newRole)

    fun hasMembership(
        userId: String,
        tenantId: String,
    ): Boolean = tenantMemberships.hasMembership(userId, tenantId)

    fun tenantAccess(
        userId: String,
        tenantId: String,
    ): TenantAccess? = tenantMemberships.tenantAccess(userId, tenantId)

    fun switchTenant(
        userId: String,
        tenantId: String,
    ): TokenPair = tenantMemberships.switchTenant(userId, tenantId)

    fun deactivateMember(
        tenantId: String,
        actorUserId: String,
        targetUserId: String,
    ): String = tenantMemberships.deactivateMember(tenantId, actorUserId, targetUserId)

    fun deactivateWorkspace(
        tenantId: String,
        ownerUserId: String,
    ) = tenantMemberships.deactivateWorkspace(
        tenantId,
        ownerUserId,
    )

    fun createInvitation(
        tenantId: String,
        actorUserId: String,
        email: String,
        role: TenantRole,
    ): TenantInvitation = tenantMemberships.createInvitation(tenantId, actorUserId, email, role)

    fun acceptInvitation(
        userId: String,
        rawToken: String,
    ): TenantMembership =
        tenantMemberships.acceptInvitation(
            userId,
            rawToken,
        )

    private fun defaultTenantId(userId: String): String =
        Memberships.selectAll().where {
            (Memberships.userId eq userId) and (Memberships.active eq true)
        }.firstOrNull()?.get(Memberships.tenantId) ?: throw IllegalArgumentException("No active workspace is available for this account")

    private fun issueTokens(
        userId: String,
        role: UserRole,
        tenantId: String,
    ): TokenPair {
        val tenantAccessRecord = tenantAccess(userId, tenantId) ?: throw IllegalArgumentException("Tenant membership required")
        val now = System.currentTimeMillis()
        val access =
            JWT.create()
                .withSubject(userId)
                .withClaim("userId", userId)
                .withClaim("role", role.name)
                .withClaim("tenantId", tenantId)
                .withClaim("accountId", tenantAccessRecord.accountId)
                .withClaim("tenantRole", tenantAccessRecord.role.name)
                .withIssuedAt(Date(now))
                .withExpiresAt(Date(now + accessTtlMs))
                .sign(algorithm)

        val refreshRaw = UUID.randomUUID().toString() + "." + UUID.randomUUID().toString()
        RefreshTokens.insert {
            it[id] = UUID.randomUUID().toString()
            it[RefreshTokens.userId] = userId
            it[RefreshTokens.tenantId] = tenantId
            it[tokenHash] = hashToken(refreshRaw)
            it[expiresAt] = now + refreshTtlMs
            it[revoked] = false
            it[createdAt] = now
        }
        return TokenPair(access, refreshRaw, accessTtlMs / 1000)
    }

    private fun hashToken(token: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(token.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val PUBLIC_TENANT_ID = "argent-public-paper"
        const val PUBLIC_TENANT_NAME = "ARGENT Paper Trading"
        private const val INVITATION_TTL_MS = 7L * 24 * 60 * 60 * 1000
    }

    data class TokenPair(
        val accessToken: String,
        val refreshToken: String,
        val expiresInSeconds: Long,
    )
}
