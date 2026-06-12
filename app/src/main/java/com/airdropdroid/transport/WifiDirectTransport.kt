package com.airdropdroid.transport

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log
import com.airdropdroid.model.DiscoverySource
import com.airdropdroid.model.NearbyDevice
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Wi-Fi ağı yokken devreye giren AirDrop tarzı doğrudan bağlantı yolu.
 *
 * Gönderen bağlanırken kendi grup-sahibi niyetini 0 yapar; böylece karşı taraf (alıcı)
 * grup sahibi (GO) olur ve gönderen, [TRANSFER_PORT] üzerinden GO adresine bağlanır.
 */
@SuppressLint("MissingPermission")
class WifiDirectTransport(private val context: Context) {

    private val manager =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private val p2pChannel = manager.initialize(context, context.mainLooper, null)

    /** Yakındaki Wi-Fi Direct cihazlarını keşfeder. */
    fun discover(): Flow<NearbyDevice> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action == WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION) {
                    manager.requestPeers(p2pChannel) { peers ->
                        peers.deviceList.forEach { device ->
                            trySend(
                                NearbyDevice(
                                    id = device.deviceAddress,
                                    name = device.deviceName.ifBlank { device.deviceAddress },
                                    source = DiscoverySource.WIFI_DIRECT,
                                )
                            )
                        }
                    }
                }
            }
        }
        val filter = IntentFilter(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        context.registerReceiver(receiver, filter)
        manager.discoverPeers(p2pChannel, loggingAction("discoverPeers"))

        awaitClose {
            runCatching { context.unregisterReceiver(receiver) }
            manager.stopPeerDiscovery(p2pChannel, null)
        }
    }

    /**
     * [deviceAddress]'e bağlanır ve grup sahibi (alıcı) IP adresini döner; başarısızsa null.
     */
    suspend fun connect(deviceAddress: String): String? =
        suspendCancellableCoroutine { cont ->
            val config = WifiP2pConfig().apply {
                this.deviceAddress = deviceAddress
                groupOwnerIntent = 0 // karşı taraf GO olsun, biz client kalalım
            }

            val connReceiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    if (intent.action == WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION) {
                        manager.requestConnectionInfo(p2pChannel) { info: WifiP2pInfo ->
                            if (info.groupFormed && cont.isActive) {
                                runCatching { context.unregisterReceiver(this) }
                                cont.resume(info.groupOwnerAddress?.hostAddress)
                            }
                        }
                    }
                }
            }
            context.registerReceiver(
                connReceiver,
                IntentFilter(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION),
            )
            cont.invokeOnCancellation {
                runCatching { context.unregisterReceiver(connReceiver) }
            }

            manager.connect(p2pChannel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {}
                override fun onFailure(reason: Int) {
                    runCatching { context.unregisterReceiver(connReceiver) }
                    if (cont.isActive) cont.resume(null)
                }
            })
        }

    fun disconnect() {
        manager.removeGroup(p2pChannel, null)
    }

    private fun loggingAction(tag: String) = object : WifiP2pManager.ActionListener {
        override fun onSuccess() {}
        override fun onFailure(reason: Int) { Log.w(TAG, "$tag başarısız: $reason") }
    }

    private companion object {
        const val TAG = "WifiDirectTransport"
    }
}
