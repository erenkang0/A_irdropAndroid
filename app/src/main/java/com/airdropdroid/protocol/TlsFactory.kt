package com.airdropdroid.protocol

import java.net.InetAddress
import java.security.KeyStore
import java.security.SecureRandom
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLServerSocketFactory
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Transferi şifrelemek için TLS soketleri üretir.
 *
 * İlk sürüm hedefi gizlilik (eavesdropping önleme), kimlik doğrulama değil; bu yüzden
 * geçici bir self-signed sertifika ve "tümüne güven" trust manager kullanılır. Cihaz
 * kimliği ayrı katmanda (BLE eşleştirme + kullanıcının kabul ekranı) sağlanır. Sertifika
 * pinning bilinçli olarak sonraki sürüme bırakılmıştır (plana bkz).
 */
object TlsFactory {

    private val trustAll: Array<TrustManager> = arrayOf(
        object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
        }
    )

    private val context: SSLContext by lazy { buildContext() }

    private fun buildContext(): SSLContext {
        val keyPair = SelfSignedCert.generate()
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setKeyEntry(
                "airdrop",
                keyPair.privateKey,
                CHAR_PASSWORD,
                arrayOf(keyPair.certificate),
            )
        }
        val kmf = javax.net.ssl.KeyManagerFactory.getInstance(
            javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm()
        )
        kmf.init(keyStore, CHAR_PASSWORD)
        return SSLContext.getInstance("TLS").apply {
            init(kmf.keyManagers, trustAll, SecureRandom())
        }
    }

    fun serverSocket(port: Int): SSLServerSocket {
        val factory = context.serverSocketFactory as SSLServerSocketFactory
        return (factory.createServerSocket(port) as SSLServerSocket).apply {
            // Modern, anonim olmayan cipher'lar; varsayılan etkin set yeterli.
        }
    }

    fun clientSocket(host: String, port: Int, timeoutMs: Int): SSLSocket {
        val factory = context.socketFactory as SSLSocketFactory
        val socket = factory.createSocket() as SSLSocket
        socket.connect(java.net.InetSocketAddress(InetAddress.getByName(host), port), timeoutMs)
        socket.startHandshake()
        return socket
    }

    private val CHAR_PASSWORD = "airdrop".toCharArray()
}
