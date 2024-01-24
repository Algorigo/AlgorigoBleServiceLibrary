package com.algorigo.common_library

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import kotlin.system.exitProcess

abstract class AbsForegroundService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                exitProcess(0)
            }

            null -> {
                startForeground()
            }
        }
        return START_STICKY
    }

    protected abstract fun getChannelId(): Int
    protected abstract fun getChannelName(): String

    @DrawableRes
    protected abstract fun getIconRes(): Int

    private fun startForeground() {
        NotificationUtil.createNotificationChannel(this, getChannelId(), getChannelName(), NotificationUtil.Importance.Low)

        val stopSelf = Intent(this, javaClass).apply { action = ACTION_STOP_SERVICE }

        val pStopSelf = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getService(this, 0, stopSelf, PendingIntent.FLAG_IMMUTABLE)
        } else {
            PendingIntent.getService(this, 0, stopSelf, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        val action = NotificationCompat.Action
            .Builder(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pStopSelf)
            .build()

        val notification: Notification = NotificationUtil.getNotification(
            this, iconRes = getIconRes(), channelId = getChannelId(), action = action
        )

        startForeground(getChannelId(), notification)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
    }
}