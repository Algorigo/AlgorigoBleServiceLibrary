package com.algorigo.test_app

import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.algorigo.algorigoble2.BleDevice
import com.algorigo.algorigoble2.BleManager
import com.algorigo.algorigoble2.BleScanSettings
import com.algorigo.library.rx.Rx2ServiceBindingFactory
import com.jakewharton.rxrelay3.BehaviorRelay
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import java.util.Optional

class TestService : Service() {

    inner class ServiceBinder : Binder() {
        fun getService(): TestService {
            return this@TestService
        }
    }

    private val binder = ServiceBinder()
    private var disposable: Disposable? = null
    val scanningRelay = BehaviorRelay.create<Boolean>().apply { accept(false) }
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

    fun startScan() {
        if (disposable != null) {
            return
        }

        scanningRelay.accept(true)
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
                scanningRelay.accept(false)
            }
            .subscribe({
                statesRelay.accept(it)
            }, {

            })
    }

    fun stopScan() {
        disposable?.dispose()
    }

    companion object {
        fun bindServiceObservble(context: Context) = Rx2ServiceBindingFactory
            .bind<TestService.ServiceBinder>(
                context, Intent(context, TestService::class.java)
            )
            .map { it.getService() }
    }
}