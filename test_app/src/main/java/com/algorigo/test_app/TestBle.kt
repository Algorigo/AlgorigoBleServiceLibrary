package com.algorigo.test_app

import android.os.ParcelUuid
import android.util.Log
import com.algorigo.algorigoble2.BleScanFilter
import com.algorigo.algorigoble2.InitializableBleDevice
import com.jakewharton.rxrelay3.BehaviorRelay
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import java.util.Date
import java.util.UUID

class TestBle : InitializableBleDevice() {

    data class State(
        val macAddress: String,
        val connectedTime: Date,
        val disconnectedTime: Date?,
        val notificationCount: Int?,
    )

    private val stateRelay = BehaviorRelay.create<State>()
    val stateObservable: Observable<State>
        get() = stateRelay

    private var macAddress: String? = null
    private lateinit var connectedTime: Date
    private var disconnectedTime: Date? = null
    private var notificationCount: Int? = null

    override fun initializeCompletable(): Completable {
        return Completable.complete()
            .doOnComplete {
                macAddress = deviceId
                connectedTime = Date()
                stateRelay.accept(State(deviceId, connectedTime, disconnectedTime, notificationCount))
                setupNotification(NotificationType.INDICATION, NOTIFY_CHARACTERISTIC_UUID)
                    .flatMap { it }
                    .subscribe({
                        notificationCount = (notificationCount ?: 0) + 1
                        stateRelay.accept(State(deviceId, connectedTime, disconnectedTime, notificationCount))
                    }, {
                        Log.e(LOG_TAG, "notification error", it)
                    })
            }
    }

    override fun onDisconnected() {
        super.onDisconnected()
        disconnectedTime = Date()
        macAddress?.also {
            stateRelay.accept(State(it, connectedTime, disconnectedTime, notificationCount))
        }
    }

    companion object {
        private val LOG_TAG = TestBle::class.java.simpleName

        val SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-0123456789AB")
        val WRITE_CHARACTERISTIC_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-0123456789AB")
        val NOTIFY_CHARACTERISTIC_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-0123456789AB")

        fun getScanFilter(): BleScanFilter {
            val parcelUuidMask = ParcelUuid.fromString("FFFFFFFF-FFFF-FFFF-FFFF-FFFFFFFFFFFF")
            return BleScanFilter.Builder()
                .setServiceUuid(ParcelUuid(SERVICE_UUID), parcelUuidMask)
//                .setDeviceName("TestBle")
                .build()
        }
    }
}