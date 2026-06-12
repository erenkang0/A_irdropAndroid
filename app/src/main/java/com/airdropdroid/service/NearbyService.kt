package com.airdropdroid.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.airdropdroid.MainActivity
import com.airdropdroid.R
import com.airdropdroid.App
import com.airdropdroid.data.NearbyRepository
import com.airdropdroid.model.IncomingRequest
import com.airdropdroid.model.TransferState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Arka planda alım: alıcı soketi, BLE görünürlüğü ve mDNS kaydını uygulama
 * kapalıyken de canlı tutar. Gelen istekler bildirimden Kabul/Reddet ile
 * yanıtlanır; ilerleme ve "alındı" bildirimleri de buradan gösterilir.
 */
class NearbyService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var repo: NearbyRepository
    private var lastProgressUpdate = 0L

    override fun onCreate() {
        super.onCreate()
        repo = App.repository(this)
        ensureChannels()
        startForeground(
            ONGOING_ID,
            ongoingNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
        repo.startReceiving()

        scope.launch {
            repo.incoming.collect { request ->
                if (request != null) notifyIncoming(request) else cancelNotification(INCOMING_ID)
            }
        }
        scope.launch {
            repo.transferState.collect { state -> onTransferState(state) }
        }
        scope.launch {
            repo.receivedFiles.collect { meta ->
                notifyReceived(meta.name)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ACCEPT -> {
                repo.respondToIncoming(true)
                cancelNotification(INCOMING_ID)
            }
            ACTION_REJECT -> {
                repo.respondToIncoming(false)
                cancelNotification(INCOMING_ID)
            }
            ACTION_STOP -> {
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        repo.stopReceiving()
        scope.cancel()
        cancelNotification(INCOMING_ID)
        cancelNotification(PROGRESS_ID)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // --- Bildirimler ---

    private fun onTransferState(state: TransferState) {
        when (state) {
            is TransferState.InProgress -> {
                // Bildirim panelini boğmamak için saniyede ~3 güncelleme.
                val now = System.currentTimeMillis()
                if (now - lastProgressUpdate < 300) return
                lastProgressUpdate = now
                val pct = if (state.totalBytes > 0)
                    ((state.bytesTransferred * 100) / state.totalBytes).toInt() else 0
                notify(
                    PROGRESS_ID,
                    builder(CHANNEL_TRANSFER)
                        .setContentTitle(state.fileName)
                        .setContentText("${state.deviceName} • %$pct")
                        .setProgress(100, pct, false)
                        .setOnlyAlertOnce(true)
                        .setOngoing(true)
                        .build(),
                )
            }
            is TransferState.Completed,
            is TransferState.Failed,
            is TransferState.Rejected,
            TransferState.Idle -> cancelNotification(PROGRESS_ID)
            else -> {}
        }
    }

    private fun notifyIncoming(request: IncomingRequest) {
        val accept = servicePendingIntent(ACTION_ACCEPT, 1)
        val reject = servicePendingIntent(ACTION_REJECT, 2)
        val open = PendingIntent.getActivity(
            this, 3,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = if (request.files.size == 1) {
            request.files.first().name
        } else {
            "${request.files.size} dosya"
        }
        notify(
            INCOMING_ID,
            builder(CHANNEL_INCOMING)
                .setContentTitle("${request.senderName} dosya göndermek istiyor")
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(false)
                .setOngoing(true)
                .addAction(Notification.Action.Builder(null, "Kabul et", accept).build())
                .addAction(Notification.Action.Builder(null, "Reddet", reject).build())
                .build(),
        )
    }

    private fun notifyReceived(fileName: String) {
        notify(
            RECEIVED_ID,
            builder(CHANNEL_TRANSFER)
                .setContentTitle("Dosya alındı")
                .setContentText("$fileName → İndirilenler/AirDrop")
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun ongoingNotification(): Notification {
        val stop = servicePendingIntent(ACTION_STOP, 0)
        return builder(CHANNEL_ONGOING)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Görünür — yakındaki cihazlardan dosya alınabilir")
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Durdur", stop).build())
            .build()
    }

    private fun builder(channel: String): Notification.Builder =
        Notification.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_launcher_foreground)

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this, requestCode,
            Intent(this, NearbyService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun notify(id: Int, notification: Notification) {
        notificationManager().notify(id, notification)
    }

    private fun cancelNotification(id: Int) {
        notificationManager().cancel(id)
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun ensureChannels() {
        val manager = notificationManager()
        if (manager.getNotificationChannel(CHANNEL_ONGOING) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ONGOING, "Arka planda alım", NotificationManager.IMPORTANCE_MIN,
                )
            )
        }
        if (manager.getNotificationChannel(CHANNEL_TRANSFER) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_TRANSFER,
                    getString(R.string.channel_transfer),
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }
        if (manager.getNotificationChannel(CHANNEL_INCOMING) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_INCOMING, "Gelen istekler", NotificationManager.IMPORTANCE_HIGH,
                )
            )
        }
    }

    companion object {
        private const val CHANNEL_ONGOING = "ongoing"
        private const val CHANNEL_TRANSFER = "transfer"
        private const val CHANNEL_INCOMING = "incoming"
        private const val ONGOING_ID = 1001
        private const val INCOMING_ID = 1002
        private const val PROGRESS_ID = 1003
        private const val RECEIVED_ID = 1004

        private const val ACTION_ACCEPT = "com.airdropdroid.action.ACCEPT"
        private const val ACTION_REJECT = "com.airdropdroid.action.REJECT"
        private const val ACTION_STOP = "com.airdropdroid.action.STOP"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, NearbyService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NearbyService::class.java))
        }
    }
}
