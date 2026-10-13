package com.argent.gateway.auth

import com.auth0.jwt.JWT
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow
import org.jetbrains.exposed.sql.Database

class TenantAuthTest : FunSpec({
    fun service(): AuthService {
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:tenant_${System.nanoTime()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
            maximumPoolSize = 2
        })
        val auth = AuthService(Database.connect(dataSource), "tenant-test-secret-is-long-enough-123")
        auth.initSchema()
        return auth
    }

    test("registration creates an isolated owner workspace") {
        val auth = service()
        shouldThrow<IllegalArgumentException> { auth.register("short@example.com", "Short123", "Short") }
        val one = auth.register("one@example.com", "LongPassword1!", "One")
        val two = auth.register("two@example.com", "LongPassword1!", "Two")

        one.tenantId shouldBe auth.memberships(one.id).single().tenantId
        auth.memberships(one.id).single().role shouldBe TenantRole.OWNER
        (one.tenantId != two.tenantId) shouldBe true
        auth.hasMembership(one.id, two.tenantId) shouldBe false
    }

    test("tenant invitation is email-bound, single-use, and grants only its role") {
        val auth = service()
        val owner = auth.register("owner@example.com", "LongPassword1!", "Owner")
        val invited = auth.register("trader@example.com", "LongPassword1!", "Trader")
        val invitation = auth.createInvitation(owner.tenantId, owner.id, invited.email, TenantRole.TRADER)

        shouldThrow<IllegalArgumentException> { auth.acceptInvitation(invited.id, "wrong-token") }
        val accepted = auth.acceptInvitation(invited.id, invitation.token)
        accepted.tenantId shouldBe owner.tenantId
        accepted.role shouldBe TenantRole.TRADER
        val ownerAccount = auth.tenantAccess(owner.id, owner.tenantId)!!.accountId
        val invitedTenantAccount = auth.tenantAccess(invited.id, owner.tenantId)!!.accountId
        (ownerAccount != invitedTenantAccount) shouldBe true
        (invitedTenantAccount != auth.tenantAccess(invited.id, invited.tenantId)!!.accountId) shouldBe true
        auth.acceptInvitation(invited.id, invitation.token) shouldBe accepted
        auth.memberships(invited.id).count { it.tenantId == owner.tenantId } shouldBe 1
    }

    test("tenant-scoped access tokens carry only a verified membership") {
        val auth = service()
        val owner = auth.register("owner@example.com", "LongPassword1!", "Owner")
        val outsider = auth.register("outsider@example.com", "LongPassword1!", "Outsider")
        shouldThrow<IllegalArgumentException> { auth.switchTenant(owner.id, outsider.tenantId) }

        val tokens = auth.switchTenant(owner.id, owner.tenantId)
        JWT.decode(tokens.accessToken).getClaim("tenantId").asString() shouldBe owner.tenantId
        JWT.decode(tokens.accessToken).getClaim("accountId").asString() shouldBe owner.id
        JWT.decode(tokens.accessToken).getClaim("tenantRole").asString() shouldBe TenantRole.OWNER.name
    }

    test("invited user can create account directly in production tenant and gets isolated account") {
        val auth = service()
        val owner = auth.register("owner@example.com", "LongPassword1!", "Owner")
        val invite = auth.createInvitation(owner.tenantId, owner.id, "new@example.com", TenantRole.VIEWER)
        shouldThrow<IllegalArgumentException> {
            auth.registerFromInvitation("new@example.com", "Short123", "New user", invite.token)
        }
        shouldThrow<IllegalArgumentException> {
            auth.registerFromInvitation("other@example.com", "LongPassword1!", "Other", invite.token)
        }
        val tokens = auth.registerFromInvitation("new@example.com", "LongPassword1!", "New user", invite.token)
        val decoded = JWT.decode(tokens.accessToken)
        decoded.getClaim("tenantId").asString() shouldBe owner.tenantId
        decoded.getClaim("tenantRole").asString() shouldBe TenantRole.VIEWER.name
        (decoded.getClaim("accountId").asString() != owner.id) shouldBe true
        shouldThrow<IllegalArgumentException> {
            auth.registerFromInvitation("new@example.com", "LongPassword1!", "New user", invite.token)
        }
    }

    test("tenant owner can administer roles and role changes invalidate stale access claims") {
        val auth = service()
        val owner = auth.register("owner@example.com", "LongPassword1!", "Owner")
        val invited = auth.register("trader@example.com", "LongPassword1!", "Trader")
        val invite = auth.createInvitation(owner.tenantId, owner.id, invited.email, TenantRole.TRADER)
        auth.acceptInvitation(invited.id, invite.token)
        auth.tenantMembers(owner.tenantId, owner.id).size shouldBe 2
        auth.updateTenantRole(owner.tenantId, owner.id, invited.id, TenantRole.VIEWER)
        auth.tenantAccess(invited.id, owner.tenantId)!!.role shouldBe TenantRole.VIEWER
        shouldThrow<IllegalArgumentException> { auth.tenantMembers(owner.tenantId, invited.id) }
        shouldThrow<IllegalArgumentException> { auth.updateTenantRole(owner.tenantId, invited.id, owner.id, TenantRole.TRADER) }
        shouldThrow<IllegalArgumentException> { auth.updateTenantRole(owner.tenantId, owner.id, owner.id, TenantRole.TRADER) }
    }

    test("tenant admins cannot grant peer administrator access by invitation") {
        val auth = service()
        val owner = auth.register("owner@example.com", "LongPassword1!", "Owner")
        val admin = auth.register("admin@example.com", "LongPassword1!", "Admin")
        val adminInvite = auth.createInvitation(owner.tenantId, owner.id, admin.email, TenantRole.ADMIN)
        auth.acceptInvitation(admin.id, adminInvite.token)

        shouldThrow<IllegalArgumentException> {
            auth.createInvitation(owner.tenantId, admin.id, "peer@example.com", TenantRole.ADMIN)
        }
        auth.createInvitation(owner.tenantId, admin.id, "trader@example.com", TenantRole.TRADER).role shouldBe TenantRole.TRADER
    }

    test("member revocation invalidates sessions and permits controlled workspace closure") {
        val auth = service()
        val owner = auth.register("owner@example.com", "LongPassword1!", "Owner")
        val invited = auth.register("trader@example.com", "LongPassword1!", "Trader")
        val invitation = auth.createInvitation(owner.tenantId, owner.id, invited.email, TenantRole.TRADER)
        auth.acceptInvitation(invited.id, invitation.token)
        val access = auth.tenantAccess(invited.id, owner.tenantId)!!
        val tenantTokens = auth.switchTenant(invited.id, owner.tenantId)
        shouldThrow<IllegalArgumentException> { auth.deactivateWorkspace(owner.tenantId, owner.id) }
        auth.deactivateMember(owner.tenantId, owner.id, invited.id) shouldBe access.accountId
        auth.tenantAccess(invited.id, owner.tenantId) shouldBe null
        shouldThrow<IllegalArgumentException> { auth.refresh(tenantTokens.refreshToken) }
        shouldThrow<IllegalArgumentException> { auth.acceptInvitation(invited.id, invitation.token) }
        auth.memberships(invited.id).size shouldBe 1
        val reinvite = auth.createInvitation(owner.tenantId, owner.id, invited.email, TenantRole.VIEWER)
        auth.acceptInvitation(invited.id, reinvite.token)
        auth.tenantAccess(invited.id, owner.tenantId)!!.accountId shouldBe access.accountId
        auth.deactivateMember(owner.tenantId, owner.id, invited.id)
        auth.deactivateWorkspace(owner.tenantId, owner.id)
        auth.hasMembership(owner.id, owner.tenantId) shouldBe false
        auth.memberships(owner.id).isEmpty() shouldBe true
    }
})
