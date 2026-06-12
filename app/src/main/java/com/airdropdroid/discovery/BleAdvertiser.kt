package com.airdropdroid.discovery

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.util.Log

/**
 * Cihazı yakındakilere "görünür" yapar (AirDrop'taki keşfedilebilirlik fazı).
 * Servis UUID'ini ve scan-response içinde kısa cihaz adını yayınlar.
 *
 * BLE yalnızca varlık duyurusu içindir; asıl dosya transferi Wi-Fi üstünden gider.
 */
@SuppressLint("MissingPermission")
class BleAdvertiser(context: Context) {

    private val advertiser: BluetoothLeAdvertiser? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .adapter?.bluetoothLeAdvertiser

    private var callback: AdvertiseCallback? = null

    fun start(displayName: String) {
        val adv = advertiser ?: run {
            Log.w(TAG, "BLE advertiser yok (BT kapalı olabilir)")
            return
        }
        stop()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(false)
            .build()

        val data = AdvertiseData.Builder()
            .addServiceUuid(BleConstants.SERVICE_UUID)
            .build()

        // Scan-response payload'ı ayrı: 31 baytlık ana paketi şişirmemek için ad burada.
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceData(
                BleConstants.SERVICE_UUID,
                displayName.take(20).toByteArray(Charsets.UTF_8),
            )
            .build()

        val cb = object : AdvertiseCallback() {
            override fun onStartFailure(errorCode: Int) {
                Log.w(TAG, "Advertise başlamadı: $errorCode")
            }
        }
        callback = cb
        adv.startAdvertising(settings, data, scanResponse, cb)
    }

    fun stop() {
        callback?.let { advertiser?.stopAdvertising(it) }
        callback = null
    }

    private companion object {
        const val TAG = "BleAdvertiser"
    }
}
