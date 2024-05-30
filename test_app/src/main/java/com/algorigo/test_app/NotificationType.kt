package com.algorigo.test_app

import androidx.annotation.StringRes
import androidx.core.app.NotificationManagerCompat

enum class NotificationType(
    val channelId: Int,
    @StringRes val channelName: Int,
    val importance: Int,
    val hasBadge: Boolean = false
) {
    LOCAL_DEVICE_SERVICE(1001, R.string.app_name, NotificationManagerCompat.IMPORTANCE_LOW)
    ;
}