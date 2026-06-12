package com.airdropdroid.transport

import android.util.Log
import com.airdropdroid.protocol.AirProtocol
import com.airdropdroid.protocol.TlsFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

/**
 * Alıcı taraf: TLS sunucu soketini açar, gelen bağlantıyı kabul eder, HELLO okur,
 * kullanıcıya sorar ve onaylanırsa dosyaları indirir. Hem LAN hem Wi-Fi Direct
 * yolunda aynı sunucu kullanılır (fark sadece keşif katmanındadır).
 */
class TransferServer(
    private val handler: ReceiveHandler,
) {
    private var serverSocket: SSLServerSocket? = null
    private var acceptJob: Job? = null

    var boundPort: Int = 0
        private set

    /** Boş bir port seçip dinlemeye başlar; bağlanılan portu döner. */
    fun start(scope: CoroutineScope, preferredPort: Int = 0): Int {
        val socket = TlsFactory.serverSocket(preferredPort)
        serverSocket = socket
        boundPort = socket.localPort
        acceptJob = scope.launch(Dispatchers.IO) {
            while (isActive && !socket.isClosed) {
                val client = try {
                    socket.accept() as SSLSocket
                } catch (e: Exception) {
                    if (isActive) Log.w(TAG, "accept hatası", e)
                    break
                }
                launch(Dispatchers.IO) { handleClient(client) }
            }
        }
        return boundPort
    }

    private suspend fun handleClient(socket: SSLSocket) = coroutineScope {
        socket.use { sock ->
            try {
                sock.startHandshake()
                val input = DataInputStream(sock.inputStream.buffered())
                val output = DataOutputStream(sock.outputStream.buffered())

                val request = AirProtocol.readHello(input)
                val accepted = handler.decide(request)
                AirProtocol.writeDecision(output, accepted)
                if (!accepted) return@coroutineScope

                for (meta in request.files) {
                    BufferedOutputStream(handler.openOutput(meta)).use { dest ->
                        withContext(Dispatchers.IO) {
                            AirProtocol.readFile(input, dest, meta.size) { received ->
                                handler.onProgress(meta.name, received, meta.size)
                            }
                        }
                    }
                    handler.onFileDone(meta)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Alım hatası", e)
            }
        }
    }

    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private companion object {
        const val TAG = "TransferServer"
    }
}
