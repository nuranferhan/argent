package com.argent.gateway.auth

import org.jetbrains.exposed.sql.Table

internal object Users : Table("users") {
    val id = varchar("id", 64)
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 128)
    val displayName = varchar("display_name", 128)
    val role = varchar("role", 16)
    val active = bool("active")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

internal object RefreshTokens : Table("refresh_tokens") {
    val id = varchar("id", 64)
    val userId = varchar("user_id", 64)
    val tenantId = varchar("tenant_id", 64).nullable()
    val tokenHash = varchar("token_hash", 128)
    val expiresAt = long("expires_at")
    val revoked = bool("revoked")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

internal object Tenants : Table("tenants") {
    val id = varchar("id", 64)
    val name = varchar("name", 128)
    val active = bool("active")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

internal object Memberships : Table("tenant_memberships") {
    val tenantId = varchar("tenant_id", 64)
    val userId = varchar("user_id", 64)
    val role = varchar("role", 16)
    val active = bool("active").default(true)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(tenantId, userId)
}

internal object TenantAccounts : Table("tenant_accounts") {
    val tenantId = varchar("tenant_id", 64)
    val userId = varchar("user_id", 64)
    val accountId = varchar("account_id", 64).uniqueIndex()
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(tenantId, userId)
}

internal object GoogleIdentities : Table("google_identities") {
    val subject = varchar("subject", 255)
    val userId = varchar("user_id", 64).uniqueIndex()
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(subject)
}

internal object Invitations : Table("tenant_invitations") {
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
