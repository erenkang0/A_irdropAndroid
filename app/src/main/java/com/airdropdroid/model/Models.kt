package com.airdropdroid.model

import android.net.Uri

/** Radarda görünen yakındaki bir cihaz. */
data class NearbyDevice(
    val id: String,
    val name: String,
    /** mDNS/LAN ile çözümlenince doldurulur; Wi-Fi Direct yolunda boş kalabilir. */
    val host: String? = null,
    val port: Int = 0,
    val source: DiscoverySource = DiscoverySource.BLE,
)

enum class DiscoverySource { BLE, LAN, WIFI_DIRECT }

/** Gönderilecek/alınan tek bir dosyanın meta bilgisi. */
data class FileMeta(
    val name: String,
    val size: Long,
    val mime: String,
)

/** Bir transferin uçtan uca durumu. */
sealed interface TransferState {
    data object Idle : TransferState
    data class Connecting(val deviceName: String) : TransferState
    data class AwaitingAcceptance(val deviceName: String) : TransferState
    data class InProgress(
        val deviceName: String,
        val fileName: String,
        val bytesTransferred: Long,
        val totalBytes: Long,
        val bytesPerSecond: Long,
    ) : TransferState
    data class Completed(val deviceName: String, val fileCount: Int) : TransferState
    data class Failed(val reason: String) : TransferState
    data class Rejected(val deviceName: String) : TransferState
}

/** Karşı taraftan gelen, kullanıcı onayı bekleyen istek. */
data class IncomingRequest(
    val senderName: String,
    val files: List<FileMeta>,
) {
    val totalBytes: Long get() = files.sumOf { it.size }
}

/** Paylaşım için seçilen kaynak (content Uri + çözümlenmiş meta). */
data class OutgoingFile(
    val uri: Uri,
    val meta: FileMeta,
)
