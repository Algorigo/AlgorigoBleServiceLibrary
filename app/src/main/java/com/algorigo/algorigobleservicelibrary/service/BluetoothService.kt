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
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.algorigo.algorigobleservicelibrary.R
import com.algorigo.algorigobleservicelibrary.ble_service.MyGattDelegate
import com.algorigo.common_library.AbsForegroundService
import com.algorigo.common_library.NotificationUtil
import com.algorigo.library.rx.Rx2ServiceBindingFactory
import com.jakewharton.rxrelay3.BehaviorRelay
import com.rouddy.twophonesupporter.BleGattServiceGenerator
import com.rouddy.twophonesupporter.BleGattServiceGenerator.Companion.startServer
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import java.util.Date
import kotlin.system.exitProcess

class BluetoothService : AbsForegroundService() {

    enum class NotificationType(
        val channelId: Int,
        @StringRes val channelName: Int,
        val importance: Int,
        val hasBadge: Boolean = false
    ) {
        LOCAL_DEVICE_SERVICE(1001, R.string.app_name, NotificationManagerCompat.IMPORTANCE_LOW)
        ;
    }

    inner class ServiceBinder : Binder() {
        fun getService(): BluetoothService {
            return this@BluetoothService
        }
    }

    private val binder = ServiceBinder()
    private lateinit var myGattDelegate: MyGattDelegate
    private var advertisingDisposable: Disposable? = null
    private var connectionHistoryRelay = BehaviorRelay.create<List<Triple<String, Date, Date?>>>()
    val connectionHistoryObservable: Observable<List<Triple<String, Date, Date?>>>
        get() = connectionHistoryRelay

    override fun onCreate() {
        super.onCreate()
        myGattDelegate = MyGattDelegate(this)
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun getChannelId(): Int = NotificationType.LOCAL_DEVICE_SERVICE.channelId

    override fun getChannelName(): String = getString(NotificationType.LOCAL_DEVICE_SERVICE.channelName)

    override fun getIconRes(): Int = R.drawable.ic_launcher_foreground

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
                .scan(listOf<Triple<String, Date, Date?>>()) { acc, event ->
                    when (event) {
                        is BleGattServiceGenerator.BluetoothServiceEvent.ClientConnected -> {
                            acc
                                .toMutableList()
                                .also { it.add(Triple(event.client.address, Date(), null)) }
                        }

                        is BleGattServiceGenerator.BluetoothServiceEvent.ClientDisconnected -> {
                            val index = acc.indexOfFirst { it.first == event.client.address && it.third == null }
                            acc
                                .toMutableList()
                                .also { it[index] = Triple(it[index]. first, it[index].second, Date()) }
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