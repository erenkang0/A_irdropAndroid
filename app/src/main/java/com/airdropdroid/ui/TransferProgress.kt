package com.airdropdroid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.airdropdroid.model.TransferState

@Composable
fun TransferProgressCard(state: TransferState, onDismiss: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (state) {
                is TransferState.Connecting ->
                    Text("${state.deviceName} cihazına bağlanılıyor…", style = MaterialTheme.typography.bodyLarge)

                is TransferState.AwaitingAcceptance ->
                    Text("${state.deviceName} onayı bekleniyor…", style = MaterialTheme.typography.bodyLarge)

                is TransferState.InProgress -> {
                    val pct = if (state.totalBytes > 0)
                        state.bytesTransferred.toFloat() / state.totalBytes else 0f
                    Text(state.fileName, style = MaterialTheme.typography.bodyLarge)
                    LinearProgressIndicator(
                        progress = { pct.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "${humanBytes(state.bytesTransferred)} / ${humanBytes(state.totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                is TransferState.Completed -> {
                    Text("Tamamlandı: ${state.fileCount} dosya ✓", style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = onDismiss) { Text("Kapat") }
                }

                is TransferState.Rejected -> {
                    Text("${state.deviceName} isteği reddetti", style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = onDismiss) { Text("Kapat") }
                }

                is TransferState.Failed -> {
                    Text("Hata: ${state.reason}", style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = onDismiss) { Text("Kapat") }
                }

                TransferState.Idle -> {}
            }
        }
    }
}

fun humanBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}
