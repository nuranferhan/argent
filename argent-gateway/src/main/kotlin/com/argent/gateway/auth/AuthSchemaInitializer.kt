package com.argent.gateway.auth

import com.argent.gateway.auth.AuthService.Companion.PUBLIC_TENANT_ID
import com.argent.gateway.auth.AuthService.Companion.PUBLIC_TENANT_NAME
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

internal class AuthSchemaInitializer(private val db: Database) {
    fun initSchema() {
        transaction(db) {
            SchemaUtils.createMissingTablesAndColumns(
                Users,
                RefreshTokens,
                Tenants,
                Memberships,
                TenantAccounts,
                Invitations,
                GoogleIdentities,
            )
            if (Tenants.selectAll().where { Tenants.id eq PUBLIC_TENANT_ID }.empty()) {
                Tenants.insert {
                    it[id] = PUBLIC_TENANT_ID
                    it[name] = PUBLIC_TENANT_NAME
                    it[active] = true
                    it[createdAt] = System.currentTimeMillis()
                }
            }
            // Additive migration: old refresh tokens have no tenant binding until next rotation.
            if (db.vendor.contains("postgres", ignoreCase = true)) {
                exec("ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64)")
            }
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
                    }.empty()
                ) {
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
}
