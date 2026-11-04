package com.argent.terminal

import com.argent.client.core.*

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.argent.terminal.ui.ArgentRed

@Composable
internal fun WorkspaceCloseConfirmationDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Çalışma alanı kapatılsın mı?") },
        text = {
            Text(
                "Bu işlem oturumları sonlandırır. Diğer üyeler önce çıkarılmalı; açık emirler kapatma sırasında iptal edilir.",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Kapat", color = ArgentRed) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}
