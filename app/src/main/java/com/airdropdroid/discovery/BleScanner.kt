package com.airdropdroid.discovery

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import com.airdropdroid.model.DiscoverySource
import com.airdropdroid.model.NearbyDevice
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Yakındaki AirDrop-Android cihazlarını BLE üzerinden tarar. Bulunan her cihazı
 * [NearbyDevice] olarak akış halinde verir (varlık bilgisi; host/port LAN katmanından
 * gelir).
 */
@SuppressLint("MissingPermission")
class BleScanner(context: Context) {

    private val scanner: BluetoothLeScanner? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .adapter?.bluetoothLeScanner

    fun scan(): Flow<NearbyDevice> = callbackFlow {
        val scan = scanner ?: run {
            close()
            return@callbackFlow
        }

        val filter = ScanFilter.Builder()
            .setServiceUuid(BleConstants.SERVICE_UUID)
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val record = result.scanRecord ?: return
                val nameBytes = record.serviceData?.get(BleConstants.SERVICE_UUID)
                val name = nameBytes?.toString(Charsets.UTF_8)
                    ?: record.deviceName
                    ?: result.device.address
                trySend(
                    NearbyDevice(
                        id = result.device.address,
                        name = name,
                        source = DiscoverySource.BLE,
                    )
                )
            }

            override fun onScanFailed(errorCode: Int) {
                Log.w(TAG, "Tarama başarısız: $errorCode")
            }
        }

        scan.startScan(listOf(filter), settings, callback)
        awaitClose { scan.stopScan(callback) }
    }

    private companion object {
        const val TAG = "BleScanner"
    }
}
