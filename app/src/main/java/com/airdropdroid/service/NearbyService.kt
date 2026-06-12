package com.airdropdroid.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.airdropdroid.R

/**
 * Keşif ve alıcı soketini uygulama ön planda değilken de kısa süre canlı tutan
 * foreground servis. (İskelet: ileride NearbyRepository örneğini buraya taşıyıp
 * uygulama kapalıyken de dosya almayı sürdürmek için kullanılabilir.)
 */
class NearbyService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        val notification: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Yakındaki cihazlar için dinleniyor")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()

        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.channel_transfer),
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "transfer"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            context.startForegroundService(Intent(context, NearbyService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NearbyService::class.java))
        }
    }
}
