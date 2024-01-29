package com.algorigo.test_app

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.app.NotificationManagerCompat
import com.algorigo.algorigoble2.BleDevice
import com.algorigo.algorigoble2.BleManager
import com.algorigo.algorigoble2.BleScanSettings
import com.algorigo.common_library.AbsForegroundService
import com.algorigo.library.rx.Rx2ServiceBindingFactory
import com.jakewharton.rxrelay3.BehaviorRelay
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import java.util.Optional

class TestService : AbsForegroundService() {
    inner class ServiceBinder : Binder() {
        fun getService(): TestService {
            return this@TestService
        }
    }

    private val binder = ServiceBinder()
    private var disposable: Disposable? = null
    private val _scanningRelay = BehaviorRelay.create<Boolean>().apply { accept(false) }
    val scanningObservable: Observable<Boolean>
        get() = _scanningRelay
    val statesRelay = BehaviorRelay.create<List<TestBle.State>>()

    private lateinit var manager: BleManager
    private val delegate = object : BleManager.BleDeviceDelegate() {
        override fun createBleDevice(bluetoothDevice: BluetoothDevice): BleDevice? {
            return TestBle()
        }
    }

    override fun onCreate() {
        super.onCreate()
        manager = BleManager(this, delegate)
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun getChannelId(): Int = NotificationType.LOCAL_DEVICE_SERVICE.channelId

    override fun getChannelName(): String = getString(NotificationType.LOCAL_DEVICE_SERVICE.channelName)

    override fun getIconRes(): Int = R.drawable.ic_launcher_foreground

    fun startScan() {
        if (disposable != null) {
            return
        }

        _scanningRelay.accept(true)
        disposable = manager.scanObservable(
            BleScanSettings
                .Builder()
                .build(),
            TestBle.getScanFilter(),
        )
            .flatMap { Observable.fromIterable(it) }
            .mapOptional {
                (it as? TestBle)
                    ?.let { Optional.of(it) }
                    ?: Optional.empty()
            }
            .distinct { it.deviceId }
            .flatMap {
                it
                    .connectCompletable()
                    .andThen(it.stateObservable)
                    .doFinally {
                        it.disconnect()
                    }
            }
            .scan(mapOf<String, TestBle.State>()) { acc, state ->
                acc
                    .toMutableMap()
                    .apply { put(state.macAddress, state) }
            }
            .map { it.values.sortedBy { it.connectedTime } }
            .doFinally {
                disposable = null
                _scanningRelay.accept(false)
            }
            .retryWhen {
                it.flatMap {
                    if (it is BleManager.DisconnectedException) {
                        Observable.just(it)
                    } else {
                        Observable.error(it)
                    }
                }
            }
            .subscribe({
                statesRelay.accept(it)
            }, {
                Log.e(LOG_TAG, "Service error", it)
            })
    }

    fun stopScan() {
        disposable?.dispose()
    }

    companion object {
        private val LOG_TAG = TestService::class.java.simpleName

        fun bindServiceObservble(context: Context) = Rx2ServiceBindingFactory
            .bind<TestService.ServiceBinder>(
                context, Intent(context, TestService::class.java)
            )
            .map { it.getService() }
    }
}