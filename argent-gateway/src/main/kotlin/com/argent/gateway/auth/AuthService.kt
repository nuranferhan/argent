package com.argent.gateway.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.mindrot.jbcrypt.BCrypt
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
    val tenantId: String
)

class AuthService(
    private val db: Database,
    private val jwtSecret: String,
    private val accessTtlMs: Long = 3_600_000L,
    private val refreshTtlMs: Long = 30L * 86_400_000
) {
    private val algorithm = Algorithm.HMAC256(jwtSecret)

    object Users : Table("users") {
        val id = varchar("id", 64)
        val email = varchar("email", 255).uniqueIndex()
        val passwordHash = varchar("password_hash", 128)
        val displayName = varchar("display_name", 128)
        val role = varchar("role", 16)
        val active = bool("active")
        val createdAt = long("created_at")
        override val primaryKey = PrimaryKey(id)
    }

    object RefreshTokens : Table("refresh_tokens") {
        val id = varchar("id", 64)
        val userId = varchar("user_id", 64)
        val tenantId = varchar("tenant_id", 64).nullable()
        val tokenHash = varchar("token_hash", 128)
        val expiresAt = long("expires_at")
        val revoked = bool("revoked")
        val createdAt = long("created_at")
        override val primaryKey = PrimaryKey(id)
    }

    object Tenants : Table("tenants") {
        val id = varchar("id", 64)
        val name = varchar("name", 128)
        val active = bool("active")
        val createdAt = long("created_at")
        override val primaryKey = PrimaryKey(id)
    }

    object Memberships : Table("tenant_memberships") {
        val tenantId = varchar("tenant_id", 64)
        val userId = varchar("user_id", 64)
        val role = varchar("role", 16)
        val active = bool("active").default(true)
        val createdAt = long("created_at")
        override val primaryKey = PrimaryKey(tenantId, userId)
    }

    object TenantAccounts : Table("tenant_accounts") {
        val tenantId = varchar("tenant_id", 64)
        val userId = varchar("user_id", 64)
        val accountId = varchar("account_id", 64).uniqueIndex()
        val createdAt = long("created_at")
        override val primaryKey = PrimaryKey(tenantId, userId)
    }

    object GoogleIdentities : Table("google_identities") {
        val subject = varchar("subject", 255)
        val userId = varchar("user_id", 64).uniqueIndex()
        val createdAt = long("created_at")
        override val primaryKey = PrimaryKey(subject)
    }

    object Invitations : Table("tenant_invitations") {
        val id = varchar("id", 64)
        val tenantId = varchar("tenant_id", 64)
        val email = varchar("email", 255)
        val role = varchar("role", 16)
        val tokenHash = varchar("token_hash", 64).uniqueIndex()
        val expiresAt = long("expires_at")
        val acceptedAt = long("accepted_at").nullable()
        val createdBy = varchar("created_by", 64)
        val createdAt = long("created_at")
        override val primaryKey = PrimaryKey(id)
    }

    fun initSchema() {
        transaction(db) {
            SchemaUtils.createMissingTablesAndColumns(Users, RefreshTokens, Tenants, Memberships, TenantAccounts, Invitations, GoogleIdentities)
            if (Tenants.selectAll().where { Tenants.id eq PUBLIC_TENANT_ID }.empty()) {
                Tenants.insert { it[id] = PUBLIC_TENANT_ID; it[name] = PUBLIC_TENANT_NAME; it[active] = true; it[createdAt] = System.currentTimeMillis() }
            }
            // Additive migration: old refresh tokens have no tenant binding until next rotation.
            runCatching { exec("ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64)") }
            // Existing users are isolated into individual workspaces; no cross-user data is exposed by backfill.
            Users.selectAll().forEach { row ->
                val userId = row[Users.id]
                val membership = Memberships.selectAll().where { Memberships.userId eq userId }.firstOrNull()
                if (membership == null) {
                    val tenantId = UUID.randomUUID().toString()
                    val name = "${row[Users.displayName].take(112)} Workspace"
                    Tenants.insert {
                        it[id] = tenantId
                        it[Tenants.name] = name
                        it[active] = true
                        it[createdAt] = row[Users.createdAt]
                    }
                    Memberships.insert {
                        it[Memberships.tenantId] = tenantId
                        it[Memberships.userId] = userId
                        it[role] = TenantRole.OWNER.name
                        it[createdAt] = row[Users.createdAt]
                    }
                }
                val tenantId = Memberships.selectAll().where { Memberships.userId eq userId }.first()[Memberships.tenantId]
                if (TenantAccounts.selectAll().where {
                        (TenantAccounts.tenantId eq tenantId) and (TenantAccounts.userId eq userId)
                    }.empty()) {
                    TenantAccounts.insert {
                        it[TenantAccounts.tenantId] = tenantId
                        it[TenantAccounts.userId] = userId
                        it[accountId] = userId
                        it[createdAt] = row[Users.createdAt]
                    }
                }
            }
        }
    }

    fun register(email: String, password: String, displayName: String, role: UserRole = UserRole.USER): UserAccount {
        val normalized = email.trim().lowercase()
        require(normalized.contains("@") && normalized.length in 5..255) { "Invalid email" }
        require(validNewPasswordInput(password)) { "New passwords must contain at least 12 characters and at most 72 UTF-8 bytes" }
        require(displayName.isNotBlank() && displayName.length <= 128) { "Invalid display name" }

        return transaction(db) {
            val exists = Users.selectAll().where { Users.email eq normalized }.count() > 0
            if (exists) throw IllegalArgumentException("Email already registered")
            val id = UUID.randomUUID().toString()
            val hash = BCrypt.hashpw(password, BCrypt.gensalt(12))
            val now = Instant.now().toEpochMilli()
            Users.insert {
                it[Users.id] = id
                it[Users.email] = normalized
                it[passwordHash] = hash
                it[Users.displayName] = displayName.trim()
                it[Users.role] = role.name
                it[active] = true
                it[createdAt] = now
            }
            val tenantId = UUID.randomUUID().toString()
            Tenants.insert {
                it[Tenants.id] = tenantId
                it[Tenants.name] = "${displayName.trim().take(112)} Workspace"
                it[active] = true
                it[createdAt] = now
            }
            Memberships.insert {
                it[Memberships.tenantId] = tenantId
                it[Memberships.userId] = id
                it[Memberships.role] = TenantRole.OWNER.name
                it[createdAt] = now
            }
            TenantAccounts.insert {
                it[TenantAccounts.tenantId] = tenantId
                it[TenantAccounts.userId] = id
                it[accountId] = id
                it[createdAt] = now
            }
            UserAccount(id, normalized, displayName.trim(), role, now, true, tenantId)
        }
    }

    /** Public sign-up always creates a least-privilege trader in the shared paper-trading tenant. */
    fun registerPublic(email: String, password: String, displayName: String): AuthenticatedRegistration {
        val normalized = email.trim().lowercase()
        require(normalized.contains("@") && normalized.length in 5..255) { "Invalid email" }
        require(validNewPasswordInput(password)) { "New passwords must contain at least 12 characters and at most 72 UTF-8 bytes" }
        require(displayName.isNotBlank() && displayName.length <= 128) { "Invalid display name" }
        return transaction(db) {
            require(Users.selectAll().where { Users.email eq normalized }.empty()) { "Email already registered" }
            val id = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            Users.insert {
                it[Users.id] = id; it[Users.email] = normalized
                it[passwordHash] = BCrypt.hashpw(password, BCrypt.gensalt(12))
                it[Users.displayName] = displayName.trim(); it[Users.role] = UserRole.USER.name
                it[active] = true; it[createdAt] = now
            }
            Memberships.insert {
                it[tenantId] = PUBLIC_TENANT_ID; it[userId] = id; it[role] = TenantRole.TRADER.name
                it[active] = true; it[createdAt] = now
            }
            TenantAccounts.insert {
                it[tenantId] = PUBLIC_TENANT_ID; it[userId] = id; it[accountId] = id; it[createdAt] = now
            }
            val user = UserAccount(id, normalized, displayName.trim(), UserRole.USER, now, true, PUBLIC_TENANT_ID)
            AuthenticatedRegistration(user, issueTokens(id, UserRole.USER, PUBLIC_TENANT_ID))
        }
    }

    /** Link only verified Google identities; existing email accounts retain their roles and workspaces. */
    fun loginWithGoogle(profile: GoogleProfile): FederatedLoginResult = transaction(db) {
        require(profile.emailVerified && profile.subject.isNotBlank()) { "Google account email must be verified" }
        val identity = GoogleIdentities.selectAll().where { GoogleIdentities.subject eq profile.subject }.firstOrNull()
        if (identity != null) {
            val user = Users.selectAll().where { Users.id eq identity[GoogleIdentities.userId] }.firstOrNull()
                ?: throw IllegalArgumentException("Google account is not linked to an active ARGENT user")
            require(user[Users.active]) { "Account disabled" }
            val tenantId = defaultTenantId(user[Users.id])
            return@transaction FederatedLoginResult(issueTokens(user[Users.id], UserRole.valueOf(user[Users.role]), tenantId), user[Users.id], false)
        }
        val email = profile.email.trim().lowercase()
        require(email.contains("@") && email.length <= 255) { "Google did not provide a valid email" }
        val existing = Users.selectAll().where { Users.email eq email }.firstOrNull()
        if (existing != null) {
            require(existing[Users.active]) { "Account disabled" }
            val userId = existing[Users.id]
            val priorGoogleLink = GoogleIdentities.selectAll().where { GoogleIdentities.userId eq userId }.firstOrNull()
            require(priorGoogleLink == null) { "Bu ARGENT hesabı başka bir Google hesabına bağlı." }
            GoogleIdentities.insert {
                it[subject] = profile.subject
                it[GoogleIdentities.userId] = userId
                it[createdAt] = System.currentTimeMillis()
            }
            val role = UserRole.valueOf(existing[Users.role])
            val tenantId = defaultTenantId(userId)
            return@transaction FederatedLoginResult(issueTokens(userId, role, tenantId), userId, false)
        }
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val displayName = profile.name?.take(128)?.ifBlank { null } ?: email.substringBefore('@')
        Users.insert {
            it[Users.id] = id; it[Users.email] = email
            it[passwordHash] = BCrypt.hashpw(UUID.randomUUID().toString(), BCrypt.gensalt(12))
            it[Users.displayName] = displayName; it[Users.role] = UserRole.USER.name; it[active] = true; it[createdAt] = now
        }
        Memberships.insert { it[tenantId] = PUBLIC_TENANT_ID; it[userId] = id; it[role] = TenantRole.TRADER.name; it[active] = true; it[createdAt] = now }
        TenantAccounts.insert { it[tenantId] = PUBLIC_TENANT_ID; it[userId] = id; it[accountId] = id; it[createdAt] = now }
        GoogleIdentities.insert { it[subject] = profile.subject; it[userId] = id; it[createdAt] = now }
        FederatedLoginResult(issueTokens(id, UserRole.USER, PUBLIC_TENANT_ID), id, true)
    }
    fun login(email: String, password: String): TokenPair {
        if (!validPasswordInput(password)) throw IllegalArgumentException("Invalid credentials")
        val normalized = email.trim().lowercase()
        return transaction(db) {
            val row = Users.selectAll().where { Users.email eq normalized }.firstOrNull()
                ?: throw IllegalArgumentException("Invalid credentials")
            if (!row[Users.active]) throw IllegalArgumentException("Account disabled")
            if (!BCrypt.checkpw(password, row[Users.passwordHash])) {
                throw IllegalArgumentException("Invalid credentials")
            }
            issueTokens(row[Users.id], UserRole.valueOf(row[Users.role]), defaultTenantId(row[Users.id]))
        }
    }

    /** Re-checks the account password immediately before a user-confirmed sensitive action. */
    fun verifyPassword(userId: String, password: String): Boolean {
        if (!validPasswordInput(password)) return false
        return transaction(db) {
            val user = Users.selectAll().where { Users.id eq userId }.firstOrNull() ?: return@transaction false
            user[Users.active] && BCrypt.checkpw(password, user[Users.passwordHash])
        }
    }

    fun registerFromInvitation(email: String, password: String, displayName: String, rawToken: String): TokenPair {
        val normalized = email.trim().lowercase()
        require(normalized.contains("@") && normalized.length in 5..255) { "Invalid email" }
        require(validNewPasswordInput(password)) { "New passwords must contain at least 12 characters and at most 72 UTF-8 bytes" }
        require(displayName.isNotBlank() && displayName.length <= 128) { "Invalid display name" }
        require(rawToken.isNotBlank() && rawToken.length <= 128) { "Invalid invitation" }
        return transaction(db) {
            require(Users.selectAll().where { Users.email eq normalized }.empty()) { "Email already registered" }
            val invite = Invitations.selectAll().where { Invitations.tokenHash eq hashToken(rawToken) }.firstOrNull()
                ?: throw IllegalArgumentException("Invalid invitation")
            require(invite[Invitations.acceptedAt] == null && invite[Invitations.expiresAt] >= System.currentTimeMillis()) {
                "Invitation expired or already used"
            }
            require(invite[Invitations.email] == normalized) { "Invitation email does not match account" }
            val now = System.currentTimeMillis()
            val userId = UUID.randomUUID().toString()
            Users.insert {
                it[Users.id] = userId
                it[Users.email] = normalized
                it[passwordHash] = BCrypt.hashpw(password, BCrypt.gensalt(12))
                it[Users.displayName] = displayName.trim()
                it[role] = UserRole.USER.name
                it[active] = true
                it[createdAt] = now
            }
            Memberships.insert {
                it[Memberships.tenantId] = invite[Invitations.tenantId]
                it[Memberships.userId] = userId
                it[role] = invite[Invitations.role]
                it[createdAt] = now
            }
            TenantAccounts.insert {
                it[TenantAccounts.tenantId] = invite[Invitations.tenantId]
                it[TenantAccounts.userId] = userId
                it[accountId] = UUID.randomUUID().toString()
                it[createdAt] = now
            }
            val consumed = Invitations.update({
                (Invitations.id eq invite[Invitations.id]) and (Invitations.acceptedAt.isNull())
            }) { it[acceptedAt] = now }
            require(consumed == 1) { "Invitation has already been used" }
            issueTokens(userId, UserRole.USER, invite[Invitations.tenantId])
        }
    }

    fun refresh(refreshToken: String): TokenPair {
        val hash = hashToken(refreshToken)
        return transaction(db) {
            val row = RefreshTokens.selectAll()
                .where { (RefreshTokens.tokenHash eq hash) and (RefreshTokens.revoked eq false) }
                .firstOrNull() ?: throw IllegalArgumentException("Invalid refresh token")
            if (row[RefreshTokens.expiresAt] < Instant.now().toEpochMilli()) {
                throw IllegalArgumentException("Refresh token expired")
            }
            val revoked = RefreshTokens.update({ (RefreshTokens.id eq row[RefreshTokens.id]) and (RefreshTokens.revoked eq false) }) {
                it[RefreshTokens.revoked] = true
            }
            if (revoked != 1) throw IllegalArgumentException("Invalid refresh token")
            val user = Users.selectAll().where { Users.id eq row[RefreshTokens.userId] }.first()
            if (!user[Users.active]) throw IllegalArgumentException("Account disabled")
            val tenantId = row[RefreshTokens.tenantId] ?: defaultTenantId(user[Users.id])
            issueTokens(user[Users.id], UserRole.valueOf(user[Users.role]), tenantId)
        }
    }

    fun revokeRefreshToken(refreshToken: String): Boolean = transaction(db) {
        val hash = hashToken(refreshToken)
        RefreshTokens.update({ (RefreshTokens.tokenHash eq hash) and (RefreshTokens.revoked eq false) }) {
            it[RefreshTokens.revoked] = true
        } == 1
    }

    fun getUser(userId: String): UserAccount? = transaction(db) {
        val row = Users.selectAll().where { Users.id eq userId }.firstOrNull() ?: return@transaction null
        val tenantId = Memberships.selectAll().where {
            (Memberships.userId eq userId) and (Memberships.active eq true)
        }.firstOrNull()?.get(Memberships.tenantId) ?: return@transaction null
        UserAccount(
            id = row[Users.id],
            email = row[Users.email],
            displayName = row[Users.displayName],
            role = UserRole.valueOf(row[Users.role]),
            createdAt = row[Users.createdAt],
            active = row[Users.active],
            tenantId = tenantId
        )
    }

    fun findByEmail(email: String): UserAccount? = transaction(db) {
        Users.selectAll().where { Users.email eq email.trim().lowercase() }.map {
            UserAccount(
                id = it[Users.id],
                email = it[Users.email],
                displayName = it[Users.displayName],
                role = UserRole.valueOf(it[Users.role]),
                createdAt = it[Users.createdAt],
                active = it[Users.active],
                tenantId = defaultTenantId(it[Users.id])
            )
        }.firstOrNull()
    }

    fun promoteToAdmin(email: String): Boolean = transaction(db) {
        Users.update({ Users.email eq email.trim().lowercase() }) {
            it[role] = UserRole.ADMIN.name
        } > 0
    }

    fun memberships(userId: String): List<TenantMembership> = transaction(db) {
        Memberships.selectAll().where { (Memberships.userId eq userId) and (Memberships.active eq true) }.mapNotNull { membership ->
            val tenant = Tenants.selectAll().where {
                (Tenants.id eq membership[Memberships.tenantId]) and (Tenants.active eq true)
            }.firstOrNull() ?: return@mapNotNull null
            TenantMembership(tenant[Tenants.id], tenant[Tenants.name], TenantRole.valueOf(membership[Memberships.role]))
        }
    }

    fun tenantMembers(tenantId: String, actorUserId: String): List<TenantMember> = transaction(db) {
        val actorRole = membershipRole(tenantId, actorUserId)
        require(actorRole == TenantRole.OWNER || actorRole == TenantRole.ADMIN) { "Tenant administrator role required" }
        Memberships.selectAll().where { (Memberships.tenantId eq tenantId) and (Memberships.active eq true) }.mapNotNull { membership ->
            val user = Users.selectAll().where { Users.id eq membership[Memberships.userId] }.firstOrNull()
                ?: return@mapNotNull null
            TenantMember(user[Users.id], user[Users.email], user[Users.displayName], TenantRole.valueOf(membership[Memberships.role]))
        }.sortedBy { it.email }
    }

    fun updateTenantRole(tenantId: String, actorUserId: String, targetUserId: String, newRole: TenantRole) = transaction(db) {
        require(newRole != TenantRole.OWNER) { "Owner role cannot be assigned or changed" }
        val actorRole = membershipRole(tenantId, actorUserId)
        val targetRole = membershipRole(tenantId, targetUserId)
        require(actorRole == TenantRole.OWNER || actorRole == TenantRole.ADMIN) { "Tenant administrator role required" }
        require(targetRole != null) { "Tenant member not found" }
        require(targetRole != TenantRole.OWNER) { "Owner role cannot be changed" }
        if (actorRole == TenantRole.ADMIN) {
            require(targetRole in setOf(TenantRole.TRADER, TenantRole.VIEWER)) { "Tenant admin cannot manage another admin" }
            require(newRole in setOf(TenantRole.TRADER, TenantRole.VIEWER)) { "Tenant admin cannot grant administrator role" }
        }
        Memberships.update({ (Memberships.tenantId eq tenantId) and (Memberships.userId eq targetUserId) }) {
            it[role] = newRole.name
        }
        // Access tokens carrying the prior role stop validating immediately; refresh rotates to the new role.
    }

    fun hasMembership(userId: String, tenantId: String): Boolean = transaction(db) {
        Memberships.selectAll().where {
            (Memberships.userId eq userId) and (Memberships.tenantId eq tenantId) and (Memberships.active eq true)
        }.count() > 0 && Tenants.selectAll().where {
            (Tenants.id eq tenantId) and (Tenants.active eq true)
        }.count() > 0
    }

    fun tenantAccess(userId: String, tenantId: String): TenantAccess? = transaction(db) {
        val membership = Memberships.selectAll().where {
            (Memberships.userId eq userId) and (Memberships.tenantId eq tenantId) and (Memberships.active eq true)
        }.firstOrNull() ?: return@transaction null
        if (Tenants.selectAll().where { (Tenants.id eq tenantId) and (Tenants.active eq true) }.empty()) return@transaction null
        val account = TenantAccounts.selectAll().where {
            (TenantAccounts.userId eq userId) and (TenantAccounts.tenantId eq tenantId)
        }.firstOrNull() ?: return@transaction null
        TenantAccess(TenantRole.valueOf(membership[Memberships.role]), account[TenantAccounts.accountId])
    }

    fun switchTenant(userId: String, tenantId: String): TokenPair = transaction(db) {
        val user = Users.selectAll().where { Users.id eq userId }.firstOrNull()
            ?: throw IllegalArgumentException("User not found")
        require(user[Users.active]) { "Account disabled" }
        require(hasMembership(userId, tenantId)) { "Tenant membership required" }
        issueTokens(userId, UserRole.valueOf(user[Users.role]), tenantId)
    }

    fun deactivateMember(tenantId: String, actorUserId: String, targetUserId: String): String = transaction(db) {
        require(actorUserId != targetUserId) { "You cannot revoke your own membership" }
        val actorRole = membershipRole(tenantId, actorUserId)
        val targetRole = membershipRole(tenantId, targetUserId)
        require(actorRole == TenantRole.OWNER || actorRole == TenantRole.ADMIN) { "Tenant administrator role required" }
        require(targetRole != null) { "Tenant member not found" }
        require(targetRole != TenantRole.OWNER) { "The workspace owner cannot be removed" }
        if (actorRole == TenantRole.ADMIN) {
            require(targetRole in setOf(TenantRole.TRADER, TenantRole.VIEWER)) { "Tenant admin cannot remove another admin" }
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

    fun deactivateWorkspace(tenantId: String, ownerUserId: String) = transaction(db) {
        require(membershipRole(tenantId, ownerUserId) == TenantRole.OWNER) { "Workspace owner role required" }
        val activeMembers = Memberships.selectAll().where {
            (Memberships.tenantId eq tenantId) and (Memberships.active eq true)
        }.count()
        require(activeMembers == 1L) { "Remove all other members before closing the workspace" }
        Tenants.update({ (Tenants.id eq tenantId) and (Tenants.active eq true) }) { it[active] = false }
        RefreshTokens.update({
            (RefreshTokens.tenantId eq tenantId) and (RefreshTokens.revoked eq false)
        }) { it[revoked] = true }
    }

    fun createInvitation(tenantId: String, actorUserId: String, email: String, role: TenantRole): TenantInvitation = transaction(db) {
        require(role != TenantRole.OWNER) { "Owner role cannot be assigned by invitation" }
        val actorRole = membershipRole(tenantId, actorUserId)
        require(actorRole == TenantRole.OWNER || actorRole == TenantRole.ADMIN) { "Tenant administrator role required" }
        if (actorRole == TenantRole.ADMIN) {
            require(role in setOf(TenantRole.TRADER, TenantRole.VIEWER)) { "Tenant admin cannot invite another administrator" }
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
            it[tokenHash] = hashToken(rawToken)
            it[expiresAt] = expiry
            it[acceptedAt] = null
            it[createdBy] = actorUserId
            it[createdAt] = now
        }
        TenantInvitation(tenantId, normalized, role, rawToken, expiry)
    }

    fun acceptInvitation(userId: String, rawToken: String): TenantMembership = transaction(db) {
        val user = Users.selectAll().where { Users.id eq userId }.firstOrNull()
            ?: throw IllegalArgumentException("User not found")
        val invite = Invitations.selectAll().where { Invitations.tokenHash eq hashToken(rawToken) }.firstOrNull()
            ?: throw IllegalArgumentException("Invalid invitation")
        require(user[Users.email] == invite[Invitations.email]) { "Invitation email does not match account" }
        val existingMembership = Memberships.selectAll().where {
            (Memberships.tenantId eq invite[Invitations.tenantId]) and (Memberships.userId eq userId)
        }.firstOrNull()
        if (invite[Invitations.acceptedAt] != null) {
            require(existingMembership?.get(Memberships.active) == true) { "Invitation expired or already used" }
            val tenant = Tenants.selectAll().where { Tenants.id eq invite[Invitations.tenantId] }.first()
            return@transaction TenantMembership(
                tenant[Tenants.id], tenant[Tenants.name], TenantRole.valueOf(existingMembership[Memberships.role])
            )
        }
        require(invite[Invitations.expiresAt] >= System.currentTimeMillis()) { "Invitation expired" }
        require(existingMembership == null || !existingMembership[Memberships.active]) { "Already a tenant member" }
        val now = System.currentTimeMillis()
        if (existingMembership == null) {
            Memberships.insert {
                it[Memberships.tenantId] = invite[Invitations.tenantId]
                it[Memberships.userId] = userId
                it[Memberships.role] = invite[Invitations.role]
                it[Memberships.active] = true
                it[Memberships.createdAt] = now
            }
        } else {
            Memberships.update({
                (Memberships.tenantId eq invite[Invitations.tenantId]) and (Memberships.userId eq userId)
            }) {
                it[Memberships.active] = true
                it[Memberships.role] = invite[Invitations.role]
            }
        }
        if (TenantAccounts.selectAll().where {
                (TenantAccounts.tenantId eq invite[Invitations.tenantId]) and (TenantAccounts.userId eq userId)
            }.empty()) {
            TenantAccounts.insert {
                it[TenantAccounts.tenantId] = invite[Invitations.tenantId]
                it[TenantAccounts.userId] = userId
                it[accountId] = UUID.randomUUID().toString()
                it[createdAt] = now
            }
        }
        val accepted = Invitations.update({
            (Invitations.id eq invite[Invitations.id]) and (Invitations.acceptedAt.isNull())
        }) { it[acceptedAt] = now }
        require(accepted == 1) { "Invitation has already been used" }
        val tenant = Tenants.selectAll().where { Tenants.id eq invite[Invitations.tenantId] }.first()
        TenantMembership(tenant[Tenants.id], tenant[Tenants.name], TenantRole.valueOf(invite[Invitations.role]))
    }

    private fun membershipRole(tenantId: String, userId: String): TenantRole? = Memberships.selectAll().where {
        (Memberships.tenantId eq tenantId) and (Memberships.userId eq userId) and (Memberships.active eq true)
    }.firstOrNull()?.let { TenantRole.valueOf(it[Memberships.role]) }

    private fun defaultTenantId(userId: String): String = Memberships.selectAll().where {
        (Memberships.userId eq userId) and (Memberships.active eq true)
    }.firstOrNull()?.get(Memberships.tenantId) ?: throw IllegalArgumentException("No active workspace is available for this account")

    private fun issueTokens(userId: String, role: UserRole, tenantId: String): TokenPair {
        val tenantAccessRecord = tenantAccess(userId, tenantId) ?: throw IllegalArgumentException("Tenant membership required")
        val now = System.currentTimeMillis()
        val access = JWT.create()
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

    private fun validPasswordInput(password: String): Boolean =
        password.toByteArray(Charsets.UTF_8).size in MIN_PASSWORD_BYTES..MAX_PASSWORD_BYTES

    private fun validNewPasswordInput(password: String): Boolean =
        password.length >= MIN_NEW_PASSWORD_CHARS &&
            password.toByteArray(Charsets.UTF_8).size in MIN_PASSWORD_BYTES..MAX_PASSWORD_BYTES

    companion object {
        const val PUBLIC_TENANT_ID = "argent-public-paper"
        const val PUBLIC_TENANT_NAME = "ARGENT Paper Trading"
        private const val MIN_PASSWORD_BYTES = 8
        private const val MIN_NEW_PASSWORD_CHARS = 12
        private const val MAX_PASSWORD_BYTES = 72 // bcrypt only incorporates the first 72 input bytes
        private const val INVITATION_TTL_MS = 7L * 24 * 60 * 60 * 1000
    }

    data class TokenPair(
        val accessToken: String,
        val refreshToken: String,
        val expiresInSeconds: Long
    )
}
