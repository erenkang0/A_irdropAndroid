package com.airdropdroid.data

import android.content.Context
import android.util.Log
import com.airdropdroid.discovery.BleAdvertiser
import com.airdropdroid.discovery.BleScanner
import com.airdropdroid.model.DiscoverySource
import com.airdropdroid.model.FileMeta
import com.airdropdroid.model.IncomingRequest
import com.airdropdroid.model.NearbyDevice
import com.airdropdroid.model.TransferState
import com.airdropdroid.transport.LanDiscovery
import com.airdropdroid.transport.ProgressListener
import com.airdropdroid.transport.ReceiveHandler
import com.airdropdroid.transport.SendItem
import com.airdropdroid.transport.TRANSFER_PORT
import com.airdropdroid.transport.TransferClient
import com.airdropdroid.transport.TransferServer
import com.airdropdroid.transport.WifiDirectTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.OutputStream

/**
 * Keşif (BLE + LAN + Wi-Fi Direct) ve transferi tek noktada birleştirir. UI yalnızca
 * bu sınıfın akışlarını dinler ve eylemlerini çağırır.
 */
class NearbyRepository(
    private val context: Context,
    private val displayName: String,
) {
    private val bleAdvertiser = BleAdvertiser(context)
    private val bleScanner = BleScanner(context)
    private val lan = LanDiscovery(context)
    private val wifiDirect = WifiDirectTransport(context)
    private val fileStore = IncomingFileStore(context)

    private val _devices = MutableStateFlow<Map<String, NearbyDevice>>(emptyMap())

    private val _deviceList = MutableStateFlow<List<NearbyDevice>>(emptyList())
    val deviceList: StateFlow<List<NearbyDevice>> = _deviceList.asStateFlow()

    private val _transferState = MutableStateFlow<TransferState>(TransferState.Idle)
    val transferState: StateFlow<TransferState> = _transferState.asStateFlow()

    private val _incoming = MutableStateFlow<IncomingRequest?>(null)
    val incoming: StateFlow<IncomingRequest?> = _incoming.asStateFlow()

    private var pendingDecision: CompletableDeferred<Boolean>? = null

    private var server: TransferServer? = null
    private val jobs = mutableListOf<Job>()

    /** Alıcı sunucuyu başlatır ve cihazı görünür yapar (keşif yayını + mDNS kaydı). */
    fun startReceiving(scope: CoroutineScope) {
        if (server != null) return
        val handler = object : ReceiveHandler {
            override suspend fun decide(request: IncomingRequest): Boolean {
                _incoming.value = request
                val deferred = CompletableDeferred<Boolean>()
                pendingDecision = deferred
                _transferState.value = TransferState.AwaitingAcceptance(request.senderName)
                val accepted = deferred.await()
                _incoming.value = null
                if (!accepted) _transferState.value = TransferState.Idle
                return accepted
            }

            override fun openOutput(meta: FileMeta): OutputStream = fileStore.openOutput(meta)

            override fun onProgress(fileName: String, transferred: Long, total: Long) {
                _transferState.value = TransferState.InProgress(
                    deviceName = "Gönderen",
                    fileName = fileName,
                    bytesTransferred = transferred,
                    totalBytes = total,
                    bytesPerSecond = 0,
                )
            }

            override fun onFileDone(meta: FileMeta) {
                _transferState.value = TransferState.Completed("Gönderen", 1)
            }
        }
        val srv = TransferServer(handler)
        srv.start(scope, TRANSFER_PORT)
        server = srv

        bleAdvertiser.start(displayName)
        lan.register(displayName, TRANSFER_PORT)
    }

    /** Kullanıcının gelen istek için verdiği kararı iletir. */
    fun respondToIncoming(accept: Boolean) {
        pendingDecision?.complete(accept)
        pendingDecision = null
    }

    /** Tüm keşif kaynaklarını dinlemeye başlar. */
    fun startDiscovery(scope: CoroutineScope) {
        stopDiscovery()
        jobs += scope.launch { bleScanner.scan().collect { upsert(it) } }
        jobs += scope.launch { lan.discover(displayName).collect { upsert(it) } }
        jobs += scope.launch { wifiDirect.discover().collect { upsert(it) } }
    }

    fun stopDiscovery() {
        jobs.forEach { it.cancel() }
        jobs.clear()
    }

    /**
     * Aynı id'li cihaz birden çok kaynaktan gelebilir; LAN bilgisi (host/port) en
     * değerli olduğundan onu koruyacak şekilde birleştirir.
     */
    private fun upsert(device: NearbyDevice) {
        _devices.update { current ->
            val existing = current[device.id]
            val merged = when {
                device.host != null -> device // LAN/çözümlenmiş kayıt önceliklidir
                existing?.host != null -> existing
                else -> device
            }
            current + (device.id to merged)
        }
        _deviceList.value = _devices.value.values.sortedBy { it.name.lowercase() }
    }

    /** Seçilen cihaza dosyaları gönderir; gerekirse Wi-Fi Direct'e düşer. */
    suspend fun send(device: NearbyDevice, items: List<SendItem>) {
        _transferState.value = TransferState.Connecting(device.name)

        val target: Pair<String, Int>? = when {
            device.host != null -> device.host to device.port
            device.source == DiscoverySource.WIFI_DIRECT -> {
                val host = wifiDirect.connect(device.id)
                host?.let { it to TRANSFER_PORT }
            }
            else -> null
        }

        if (target == null) {
            _transferState.value = TransferState.Failed("Cihaza bağlanılamadı")
            return
        }

        val client = TransferClient(displayName)
        val listener = ProgressListener { name, sent, total ->
            _transferState.value = TransferState.InProgress(
                deviceName = device.name,
                fileName = name,
                bytesTransferred = sent,
                totalBytes = total,
                bytesPerSecond = 0,
            )
        }
        when (val result = client.send(target.first, target.second, items, listener)) {
            is TransferClient.Result.Success ->
                _transferState.value = TransferState.Completed(device.name, items.size)
            is TransferClient.Result.Rejected ->
                _transferState.value = TransferState.Rejected(device.name)
            is TransferClient.Result.Error -> {
                Log.w(TAG, "Gönderim hatası: ${result.message}")
                _transferState.value = TransferState.Failed(result.message)
            }
        }
    }

    fun resetTransferState() {
        _transferState.value = TransferState.Idle
    }

    fun shutdown() {
        stopDiscovery()
        server?.stop()
        server = null
        bleAdvertiser.stop()
        lan.unregister()
        wifiDirect.disconnect()
    }

    private companion object {
        const val TAG = "NearbyRepository"
    }
}
