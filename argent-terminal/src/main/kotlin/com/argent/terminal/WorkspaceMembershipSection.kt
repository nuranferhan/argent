package com.argent.terminal

import com.argent.client.core.*

import com.argent.client.core.acceptTenantInvitation
import com.argent.client.core.inviteTenantMember
import com.argent.client.core.me
import com.argent.client.core.removeTenantMember
import com.argent.client.core.switchTenant
import com.argent.client.core.tenantMembers
import com.argent.client.core.tenants
import com.argent.client.core.updateTenantMemberRole

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.argent.terminal.ui.ArgentBlue
import com.argent.terminal.ui.ArgentGold
import com.argent.terminal.ui.ArgentRed
import com.argent.terminal.ui.ArgentSilver

@Composable
internal fun WorkspaceMembershipSection(
    api: ArgentApi,
    me: MeResponse,
    tenants: List<TenantDto>,
    members: List<TenantMemberDto>,
    canManage: Boolean,
    inviteEmail: String,
    onInviteEmailChange: (String) -> Unit,
    inviteRole: String,
    onInviteRoleChange: (String) -> Unit,
    inviteToken: String,
    acceptToken: String,
    onAcceptTokenChange: (String) -> Unit,
    acceptingInvitation: Boolean,
    runAction: ((suspend () -> Unit) -> Unit),
    onMeChanged: (MeResponse) -> Unit,
    onFlash: (String) -> Unit,
    onMembersChanged: (List<TenantMemberDto>) -> Unit,
    onTenantsChanged: (List<TenantDto>) -> Unit,
    onInviteCreated: (String) -> Unit,
    onAcceptingChanged: (Boolean) -> Unit,
) {
    tenants.forEach { tenant ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${tenant.tenantName} · ${tenant.role}", Modifier.weight(1f), color = ArgentSilver)
            if (tenant.tenantId != me.tenantId) {
                TextButton(onClick = {
                    runAction {
                        api.switchTenant(tenant.tenantId)
                        onMeChanged(api.me())
                    }
                }) { Text("Geçiş yap", color = ArgentBlue) }
            }
        }
    }
    if (canManage) {
        Text("Üyeler", color = ArgentGold)
        members.forEach { member ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${member.displayName} · ${member.email} · ${member.role}",
                    Modifier.weight(1f),
                    color = ArgentSilver,
                    fontSize = 11.sp,
                )
                val mayManageMember = member.role != "OWNER" && member.userId != me.userId &&
                    (me.tenantRole == "OWNER" || member.role != "ADMIN")
                if (mayManageMember) {
                    TextButton(onClick = {
                        val nextRole = when (member.role) {
                            "VIEWER" -> "TRADER"
                            "TRADER" -> if (me.tenantRole == "OWNER") "ADMIN" else "VIEWER"
                            else -> "VIEWER"
                        }
                        runAction {
                            api.updateTenantMemberRole(member.userId, nextRole)
                            onMembersChanged(api.tenantMembers())
                        }
                    }) { Text("Rolü değiştir", color = ArgentBlue) }
                    TextButton(onClick = {
                        runAction {
                            api.removeTenantMember(member.userId)
                            onMembersChanged(api.tenantMembers())
                        }
                    }) { Text("Çıkar", color = ArgentRed) }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                inviteEmail,
                onInviteEmailChange,
                label = { Text("Davet e-postası") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                colors = fieldColors(),
            )
            TextButton(onClick = {
                onInviteRoleChange(
                    when (inviteRole) {
                        "TRADER" -> if (me.tenantRole == "OWNER") "ADMIN" else "VIEWER"
                        "VIEWER" -> "TRADER"
                        else -> "VIEWER"
                    },
                )
            }) { Text(inviteRole, color = ArgentGold) }
            Button(onClick = {
                runAction {
                    onInviteCreated(api.inviteTenantMember(inviteEmail, inviteRole).token)
                    onInviteEmailChange("")
                }
            }) { Text("Davet oluştur") }
        }
        if (inviteToken.isNotBlank()) {
            Text("Davet kodu (tek sefer gösterilir): $inviteToken", color = ArgentGold, fontSize = 11.sp)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            acceptToken,
            onAcceptTokenChange,
            label = { Text("Mevcut kullanıcı için davet kodu") },
            modifier = Modifier.weight(1f),
            singleLine = true,
            colors = fieldColors(),
        )
        Button(enabled = !acceptingInvitation, onClick = {
            onAcceptingChanged(true)
            runAction {
                try {
                    val membership = api.acceptTenantInvitation(acceptToken.trim())
                    onAcceptTokenChange("")
                    onTenantsChanged(api.tenants())
                    onFlash("Çalışma alanına katıldın · ${membership.role}")
                } finally {
                    onAcceptingChanged(false)
                }
            }
        }) { Text(if (acceptingInvitation) "Kabul ediliyor…" else "Kabul et") }
    }
}
