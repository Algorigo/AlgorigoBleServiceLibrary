package com.algorigo.algorigobleservicelibrary.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.algorigo.algorigobleservice.BleGattDelegate
import com.algorigo.algorigobleservice.BleGattServiceGenerator.Companion.startServer
import com.algorigo.algorigobleservicelibrary.R
import com.algorigo.algorigobleservicelibrary.ble_service.MyGattDelegate
import com.algorigo.common_library.AbsForegroundService
import com.algorigo.library.rx.Rx2ServiceBindingFactory
import com.jakewharton.rxrelay3.BehaviorRelay
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import java.util.Date

class BluetoothService : AbsForegroundService() {
    inner class ServiceBinder : Binder() {
        fun getService(): BluetoothService {
            return this@BluetoothService
        }
    }

    private val binder = ServiceBinder()
    private lateinit var myGattDelegate: MyGattDelegate
    private var advertisingDisposable: Disposable? = null
    private var _advertisingRelay = BehaviorRelay.create<Boolean>()
    val advertisingObservable: Observable<Boolean>
        get() = _advertisingRelay

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
            _advertisingRelay.accept(false)
            advertisingDisposable?.dispose()
        } else {
            _advertisingRelay.accept(true)
            advertisingDisposable = startServer(
                this, myGattDelegate
            )
                .doFinally {
                    advertisingDisposable = null
                }
                .scan(listOf<Triple<String, Date, Date?>>()) { acc, event ->
                    when (event) {
                        is BleGattDelegate.DelegateEvent.ClientConnected -> {
                            acc
                                .toMutableList()
                                .also { it.add(Triple(event.client.address, Date(), null)) }
                        }

                        is BleGattDelegate.DelegateEvent.ClientDisconnected -> {
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