package com.airdropdroid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.airdropdroid.model.IncomingRequest

/** AirDrop'taki "X sana dosya göndermek istiyor — Kabul/Ret" onay penceresi. */
@Composable
fun IncomingRequestDialog(
    request: IncomingRequest,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onReject,
        title = { Text("${request.senderName} dosya göndermek istiyor") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                request.files.take(5).forEach { f ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(f.name, modifier = Modifier.weight(1f))
                        Text(humanBytes(f.size))
                    }
                }
                if (request.files.size > 5) {
                    Text("+${request.files.size - 5} dosya daha")
                }
                Text("Toplam: ${humanBytes(request.totalBytes)}")
            }
        },
        confirmButton = { TextButton(onClick = onAccept) { Text("Kabul et") } },
        dismissButton = { TextButton(onClick = onReject) { Text("Reddet") } },
    )
}
