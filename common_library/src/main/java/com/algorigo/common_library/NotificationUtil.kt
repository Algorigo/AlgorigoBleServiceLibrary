package com.algorigo.common_library

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

object NotificationUtil {

    enum class Importance(val value: Int) {
        Unspecified(NotificationManager.IMPORTANCE_UNSPECIFIED),
        None(NotificationManager.IMPORTANCE_NONE),
        Min(NotificationManager.IMPORTANCE_MIN),
        Low(NotificationManager.IMPORTANCE_LOW),
        Default(NotificationManager.IMPORTANCE_DEFAULT),
        High(NotificationManager.IMPORTANCE_HIGH),
    }

    @JvmStatic
    fun createNotificationChannel(context: Context, channelId: Int, channelName: String, importance: Importance = Importance.Default, showBadge: Boolean = false) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService<NotificationManager>()
            NotificationChannel(channelId.toString(), channelName, importance.value)
                .apply {
                    setShowBadge(showBadge)
                }
                .let {
                    manager?.createNotificationChannel(it)
                }
        }
    }

    @JvmStatic
    fun getNotification(context: Context, channelId: Int, title: String? = null, contentText: String? = null, @DrawableRes iconRes: Int? = null, action: NotificationCompat.Action? = null, pendingIntent: PendingIntent? = null): Notification {
        return NotificationCompat
            .Builder(
                context,
                channelId.toString()
            )
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(iconRes ?: context.applicationInfo.icon)
            .setContentIntent(pendingIntent)
            .addAction(action)
            .build()
    }
}