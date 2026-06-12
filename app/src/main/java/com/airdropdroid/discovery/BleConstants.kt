package com.airdropdroid.discovery

import android.content.Context
import android.os.ParcelUuid
import java.util.UUID

object BleConstants {
    /** AirDrop-Android servis UUID'i (uygulamaya özel sabit). */
    val SERVICE_UUID: ParcelUuid =
        ParcelUuid(UUID.fromString("a1d0c0de-0000-1000-8000-00805f9b34fb"))

    private const val PREFS = "airdrop_identity"
    private const val KEY_ID = "device_id"

    /** Cihaz için kalıcı, kısa (4 hex) bir kimlik üretir/okur. */
    fun deviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_ID, null)?.let { return it }
        val id = UUID.randomUUID().toString().substring(0, 8)
        prefs.edit().putString(KEY_ID, id).apply()
        return id
    }
}
