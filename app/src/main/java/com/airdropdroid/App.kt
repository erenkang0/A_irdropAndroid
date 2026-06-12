package com.airdropdroid

import android.app.Application
import android.content.Context
import android.os.Build
import com.airdropdroid.data.NearbyRepository

/**
 * Uygulama geneli tek [NearbyRepository] örneğini tutar; böylece arka plandaki
 * servis ile UI aynı keşif/transfer durumunu paylaşır.
 */
class App : Application() {

    val repository: NearbyRepository by lazy {
        NearbyRepository(this, deviceDisplayName())
    }

    companion object {
        fun repository(context: Context): NearbyRepository =
            (context.applicationContext as App).repository

        fun deviceDisplayName(): String =
            listOfNotNull(Build.MANUFACTURER, Build.MODEL)
                .joinToString(" ")
                .ifBlank { "Android" }
    }
}
