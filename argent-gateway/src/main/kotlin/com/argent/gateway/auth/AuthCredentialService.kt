package com.argent.gateway.auth

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.mindrot.jbcrypt.BCrypt
import java.time.Instant
import java.util.UUID

internal class AuthCredentialService(
    private val db: Database,
    private val issueTokens: (String, UserRole, String) -> AuthService.TokenPair,
    private val defaultTenantId: (String) -> String,
    private val hashToken: (String) -> String,
) {
    private fun validPasswordInput(password: String): Boolean =
        password.toByteArray(Charsets.UTF_8).size in MIN_PASSWORD_BYTES..MAX_PASSWORD_BYTES

    private fun validNewPasswordInput(password: String): Boolean =
        password.length >= MIN_NEW_PASSWORD_CHARS &&
            password.toByteArray(Charsets.UTF_8).size in MIN_PASSWORD_BYTES..MAX_PASSWORD_BYTES

    fun register(
        email: String,
        password: String,
        displayName: String,
        role: UserRole = UserRole.USER,
    ): UserAccount {
        val normalized = email.trim().lowercase()
        require(normalized.contains("@") && normalized.length in 5..255) { "Invalid email" }
        require(validNewPasswordInput(password)) {
            "New passwords must contain at least 12 characters and at most 72 UTF-8 bytes"
        }
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
    fun registerPublic(
        email: String,
        password: String,
        displayName: String,
    ): AuthenticatedRegistration {
        val normalized = email.trim().lowercase()
        require(normalized.contains("@") && normalized.length in 5..255) { "Invalid email" }
        require(validNewPasswordInput(password)) {
            "New passwords must contain at least 12 characters and at most 72 UTF-8 bytes"
        }
        require(displayName.isNotBlank() && displayName.length <= 128) { "Invalid display name" }
        return transaction(db) {
            require(Users.selectAll().where { Users.email eq normalized }.empty()) { "Email already registered" }
            val id = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            Users.insert {
                it[Users.id] = id
                it[Users.email] = normalized
                it[passwordHash] = BCrypt.hashpw(password, BCrypt.gensalt(12))
                it[Users.displayName] = displayName.trim()
                it[Users.role] = UserRole.USER.name
                it[active] = true
                it[createdAt] = now
            }
            Memberships.insert {
                it[tenantId] = AuthService.PUBLIC_TENANT_ID
                it[userId] = id
                it[role] = TenantRole.TRADER.name
                it[active] = true
                it[createdAt] = now
            }
            TenantAccounts.insert {
                it[tenantId] = AuthService.PUBLIC_TENANT_ID
                it[userId] = id
                it[accountId] = id
                it[createdAt] = now
            }
            val user =
                UserAccount(id, normalized, displayName.trim(), UserRole.USER, now, true, AuthService.PUBLIC_TENANT_ID)
            AuthenticatedRegistration(user, issueTokens(id, UserRole.USER, AuthService.PUBLIC_TENANT_ID))
        }
    }

    /** Link only verified Google identities; existing email accounts retain their roles and workspaces. */
    fun loginWithGoogle(profile: GoogleProfile): FederatedLoginResult =
        transaction(db) {
            require(profile.emailVerified && profile.subject.isNotBlank()) { "Google account email must be verified" }
            val identity = GoogleIdentities.selectAll().where { GoogleIdentities.subject eq profile.subject }.firstOrNull()
            if (identity != null) {
                val user =
                    Users.selectAll().where { Users.id eq identity[GoogleIdentities.userId] }.firstOrNull()
                        ?: throw IllegalArgumentException("Google account is not linked to an active ARGENT user")
                require(user[Users.active]) { "Account disabled" }
                val tenantId = defaultTenantId(user[Users.id])
                return@transaction FederatedLoginResult(
                    issueTokens(user[Users.id], UserRole.valueOf(user[Users.role]), tenantId),
                    user[Users.id],
                    false,
                )
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
                it[Users.id] = id
                it[Users.email] = email
                it[passwordHash] = BCrypt.hashpw(UUID.randomUUID().toString(), BCrypt.gensalt(12))
                it[Users.displayName] = displayName
                it[Users.role] = UserRole.USER.name
                it[active] = true
                it[createdAt] = now
            }
            Memberships.insert {
                it[tenantId] = AuthService.PUBLIC_TENANT_ID
                it[userId] = id
                it[role] = TenantRole.TRADER.name
                it[active] = true
                it[createdAt] = now
            }
            TenantAccounts.insert {
                it[tenantId] = AuthService.PUBLIC_TENANT_ID
                it[userId] = id
                it[accountId] = id
                it[createdAt] = now
            }
            GoogleIdentities.insert {
                it[subject] = profile.subject
                it[userId] = id
                it[createdAt] = now
            }
            FederatedLoginResult(issueTokens(id, UserRole.USER, AuthService.PUBLIC_TENANT_ID), id, true)
        }

    fun login(
        email: String,
        password: String,
    ): AuthService.TokenPair {
        if (!validPasswordInput(password)) throw IllegalArgumentException("Invalid credentials")
        val normalized = email.trim().lowercase()
        return transaction(db) {
            val row =
                Users.selectAll().where { Users.email eq normalized }.firstOrNull()
                    ?: throw IllegalArgumentException("Invalid credentials")
            if (!row[Users.active]) throw IllegalArgumentException("Account disabled")
            if (!BCrypt.checkpw(password, row[Users.passwordHash])) {
                throw IllegalArgumentException("Invalid credentials")
            }
            issueTokens(row[Users.id], UserRole.valueOf(row[Users.role]), defaultTenantId(row[Users.id]))
        }
    }

    /** Re-checks the account password immediately before a user-confirmed sensitive action. */
    fun verifyPassword(
        userId: String,
        password: String,
    ): Boolean {
        if (!validPasswordInput(password)) return false
        return transaction(db) {
            val user = Users.selectAll().where { Users.id eq userId }.firstOrNull() ?: return@transaction false
            user[Users.active] && BCrypt.checkpw(password, user[Users.passwordHash])
        }
    }

    fun registerFromInvitation(
        email: String,
        password: String,
        displayName: String,
        rawToken: String,
    ): AuthService.TokenPair {
        val normalized = email.trim().lowercase()
        require(normalized.contains("@") && normalized.length in 5..255) { "Invalid email" }
        require(validNewPasswordInput(password)) {
            "New passwords must contain at least 12 characters and at most 72 UTF-8 bytes"
        }
        require(displayName.isNotBlank() && displayName.length <= 128) { "Invalid display name" }
        require(rawToken.isNotBlank() && rawToken.length <= 128) { "Invalid invitation" }
        return transaction(db) {
            require(Users.selectAll().where { Users.email eq normalized }.empty()) { "Email already registered" }
            val invite =
                Invitations.selectAll().where { Invitations.tokenHash eq hashToken(rawToken) }.firstOrNull()
                    ?: throw IllegalArgumentException("Invalid invitation")
            require(
                invite[Invitations.acceptedAt] == null && invite[Invitations.expiresAt] >= System.currentTimeMillis(),
            ) {
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
            val consumed =
                Invitations.update({
                    (Invitations.id eq invite[Invitations.id]) and (Invitations.acceptedAt.isNull())
                }) { it[acceptedAt] = now }
            require(consumed == 1) { "Invitation has already been used" }
            issueTokens(userId, UserRole.USER, invite[Invitations.tenantId])
        }
    }

    companion object {
        private const val MIN_PASSWORD_BYTES = 8
        private const val MIN_NEW_PASSWORD_CHARS = 12
        private const val MAX_PASSWORD_BYTES = 72
    }
}
