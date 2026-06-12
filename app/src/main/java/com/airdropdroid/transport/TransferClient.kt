package com.airdropdroid.transport

import com.airdropdroid.protocol.AirProtocol
import com.airdropdroid.protocol.TlsFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream

/** Gönderen taraf: hedefe TLS ile bağlanır, HELLO yollar, onay bekler, dosyaları atar. */
class TransferClient(
    private val senderName: String,
) {
    sealed interface Result {
        data object Success : Result
        data object Rejected : Result
        data class Error(val message: String) : Result
    }

    suspend fun send(
        host: String,
        port: Int,
        items: List<SendItem>,
        progress: ProgressListener,
        connectTimeoutMs: Int = 10_000,
    ): Result = withContext(Dispatchers.IO) {
        try {
            TlsFactory.clientSocket(host, port, connectTimeoutMs).use { socket ->
                val output = DataOutputStream(socket.outputStream.buffered())
                val input = DataInputStream(socket.inputStream.buffered())

                AirProtocol.writeHello(output, senderName, items.map { it.meta })
                if (!AirProtocol.readDecision(input)) {
                    return@withContext Result.Rejected
                }

                for (item in items) {
                    item.openStream().use { source ->
                        AirProtocol.writeFile(output, source, item.meta.size) { sent ->
                            progress.onProgress(item.meta.name, sent, item.meta.size)
                        }
                    }
                }
                Result.Success
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Bilinmeyen hata")
        }
    }
}
