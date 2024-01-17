package com.algorigo.algorigobleservicelibrary.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.algorigo.algorigobleservicelibrary.R

object NotificationUtil {

    private const val LOCAL_DEVICE_SERVICE_CHANNEL_ID = 1001

    enum class NotificationType(
        val channelId: Int,
        @StringRes val channelName: Int,
        val importance: Int,
        val hasBadge: Boolean = false
    ) {
        LOCAL_DEVICE_SERVICE(LOCAL_DEVICE_SERVICE_CHANNEL_ID, R.string.app_name, NotificationManagerCompat.IMPORTANCE_LOW),
        ;

        companion object {
            @RequiresApi(Build.VERSION_CODES.O)
            fun getNotificationChannel(context: Context, notificationType: NotificationType): NotificationChannel {
                return values()
                    .firstOrNull { notificationType == it }
                    ?.let {
                        NotificationChannel(it.channelId.toString(), context.getString(it.channelName), it.importance)
                            .apply {
                                setShowBadge(it.hasBadge)
                            }
                    } ?: throw IllegalArgumentException("notification not found")
            }
        }
    }

    @JvmStatic
    fun createNotificationChannel(context: Context, notificationType: NotificationType) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService<NotificationManager>()

            NotificationType
                .getNotificationChannel(context, notificationType)
                .let {
                    manager?.createNotificationChannel(it)
                }
        }
    }

    @JvmStatic
    fun getNotification(context: Context, type: NotificationType, title: String? = null, contentText: String? = null, @DrawableRes iconRes: Int? = null, action: NotificationCompat.Action? = null, pendingIntent: PendingIntent? = null): Notification {
        return NotificationCompat
            .Builder(
                context,
                type.channelId.toString()
            )
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(iconRes ?: context.applicationInfo.icon)
            .setContentIntent(pendingIntent)
            .addAction(action)
            .build()
    }
}