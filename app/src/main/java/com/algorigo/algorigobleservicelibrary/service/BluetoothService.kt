package com.algorigo.algorigobleservicelibrary.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.algorigo.algorigobleservicelibrary.R
import com.algorigo.algorigobleservicelibrary.ble_service.MyGattDelegate
import com.algorigo.algorigobleservicelibrary.util.NotificationUtil
import com.algorigo.library.rx.Rx2ServiceBindingFactory
import com.jakewharton.rxrelay3.BehaviorRelay
import com.rouddy.twophonesupporter.BleGattServiceGenerator
import com.rouddy.twophonesupporter.BleGattServiceGenerator.Companion.startServer
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import java.util.Date
import kotlin.system.exitProcess

class BluetoothService : Service() {

    inner class ServiceBinder : Binder() {
        fun getService(): BluetoothService {
            return this@BluetoothService
        }
    }

    private val binder = ServiceBinder()
    private lateinit var myGattDelegate: MyGattDelegate
    private var advertisingDisposable: Disposable? = null
    private var connectionHistoryRelay = BehaviorRelay.create<List<Pair<Date, Date?>>>()
    val connectionHistoryObservable: Observable<List<Pair<Date, Date?>>>
        get() = connectionHistoryRelay

    override fun onCreate() {
        super.onCreate()
        myGattDelegate = MyGattDelegate(this)
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

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

    private fun startForeground() {
        val notificationType = NotificationUtil.NotificationType.LOCAL_DEVICE_SERVICE

        NotificationUtil.createNotificationChannel(this, notificationType)

        val stopSelf = Intent(this, BluetoothService::class.java).apply { action = ACTION_STOP_SERVICE }

        val pStopSelf = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getService(this, 0, stopSelf, PendingIntent.FLAG_IMMUTABLE)
        } else {
            PendingIntent.getService(this, 0, stopSelf, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        val action = NotificationCompat.Action
            .Builder(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pStopSelf)
            .build()

        val notification: Notification = NotificationUtil.getNotification(
            this, iconRes = R.drawable.ic_launcher_foreground, type = notificationType, action = action
        )

        startForeground(notificationType.channelId, notification)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    @SuppressLint("MissingPermission")
    fun toggleAdvertising() {
        if (advertisingDisposable != null) {
            advertisingDisposable?.dispose()
        } else {
            advertisingDisposable = startServer(
                this, myGattDelegate
            )
                .doFinally {
                    advertisingDisposable = null
                }
                .doOnNext {
                    myGattDelegate.handleEvent(it)
                }
                .scan(listOf<Pair<Date, Date?>>()) { acc, event ->
                    when (event) {
                        is BleGattServiceGenerator.BluetoothServiceEvent.ClientConnected -> {
                            acc
                                .toMutableList()
                                .also { it.add(Pair(Date(), null)) }
                        }

                        is BleGattServiceGenerator.BluetoothServiceEvent.ClientDisconnected -> {
                            val index = acc.indexOfFirst { it.second == null }
                            acc
                                .toMutableList()
                                .also { it[index] = Pair(it[index].first, Date()) }
                        }

                        else -> acc
                    }
                }
                .subscribe({
                    Log.e(LOG_TAG, "event:$it")
                    connectionHistoryRelay.accept(it)
                }, {
                    Log.e(LOG_TAG, "error", it)
                })
        }
    }

    companion object {
        private val LOG_TAG = BluetoothService::class.java.simpleName

        private const val ACTION_STOP_SERVICE = "BluetoothService::ACTION_STOP_SERVICE"

        fun bindServiceObservble(context: Context) = Rx2ServiceBindingFactory
            .bind<BluetoothService.ServiceBinder>(
                context, Intent(context, BluetoothService::class.java)
            )
            .map { it.getService() }
    }
}