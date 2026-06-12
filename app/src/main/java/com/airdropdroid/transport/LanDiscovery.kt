package com.airdropdroid.transport

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.airdropdroid.model.DiscoverySource
import com.airdropdroid.model.NearbyDevice
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.net.InetAddress

/**
 * Aynı Wi-Fi ağındaki cihazları mDNS (NsdManager) ile keşfeder ve kendi alıcı
 * servisini yayınlar. AirDrop'taki "aynı ağdaysak doğrudan ve hızlı" yolu.
 */
class LanDiscovery(context: Context) {

    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var registration: NsdManager.RegistrationListener? = null

    /** Alıcı servisini ağda ilan eder; [port] çalışan [TransferServer] portudur. */
    fun register(displayName: String, port: Int) {
        unregister()
        val info = NsdServiceInfo().apply {
            serviceName = sanitize(displayName)
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.i(TAG, "Kayıt: ${info.serviceName}")
            }
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "Kayıt başarısız: $errorCode")
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
        }
        registration = listener
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun unregister() {
        registration?.let { runCatching { nsd.unregisterService(it) } }
        registration = null
    }

    /** Ağdaki diğer alıcıları keşfeder ve çözümleyip host/port'la birlikte yayınlar. */
    fun discover(selfName: String): Flow<NearbyDevice> = callbackFlow {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "Çözümleme başarısız: $errorCode")
            }
            override fun onServiceResolved(info: NsdServiceInfo) {
                @Suppress("DEPRECATION")
                val host: InetAddress = info.host ?: return
                trySend(
                    NearbyDevice(
                        id = info.serviceName,
                        name = info.serviceName,
                        host = host.hostAddress,
                        port = info.port,
                        source = DiscoverySource.LAN,
                    )
                )
            }
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "Keşif başlatılamadı: $errorCode")
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                if (info.serviceName == sanitize(selfName)) return // kendini atla
                @Suppress("DEPRECATION")
                nsd.resolveService(info, resolveListener)
            }
            override fun onServiceLost(info: NsdServiceInfo) {}
        }

        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        awaitClose { runCatching { nsd.stopServiceDiscovery(discoveryListener) } }
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9 _-]"), "").take(40).ifBlank { "AirDrop" }

    private companion object {
        const val TAG = "LanDiscovery"
        const val SERVICE_TYPE = "_airdropdroid._tcp."
    }
}
