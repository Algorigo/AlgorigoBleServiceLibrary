package com.rouddy.twophonesupporter

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import java.util.UUID

class BleAdvertiseOption private constructor() {
    internal var name: String? = null
    internal var uuid: UUID? = null

    class Builder {
        private val option = BleAdvertiseOption()

        fun setName(name: String): Builder {
            option.name = name
            return this
        }

        fun setUuid(uuid: UUID): Builder {
            option.uuid = uuid
            return this
        }

        fun build(): BleAdvertiseOption {
            return option
        }
    }
}

internal object BleAdvertiser {

    fun startAdvertising(context: Context, option: BleAdvertiseOption): Observable<BluetoothLeAdvertiser> {
        val connectedSubject = BehaviorSubject.create<BluetoothLeAdvertiser>()
        var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null

        val advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                super.onStartSuccess(settingsInEffect)
                connectedSubject.onNext(bluetoothLeAdvertiser!!)
            }

            override fun onStartFailure(errorCode: Int) {
                super.onStartFailure(errorCode)
                connectedSubject.onError(RuntimeException("Failure:$errorCode"))
            }
        }

        return Completable
            .fromCallable {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                    throw RuntimeException("Permission")
                }

                val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val bluetoothAdapter = bluetoothManager.adapter

                if (option.name != null) {
                    val nameChanged = bluetoothAdapter.setName(option.name)
                }
                Thread.sleep(500L)

                bluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
                val settings = AdvertiseSettings
                    .Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
                    .setConnectable(true)
                    .setTimeout(0)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                    .build()

                val data = AdvertiseData
                    .Builder()
                    .setIncludeDeviceName(true)
                    .setIncludeTxPowerLevel(false)
                    .let {
                        if (option.uuid != null) {
                            it.addServiceUuid(ParcelUuid(option.uuid))
                        } else {
                            it
                        }
                    }
                    .build()

                bluetoothLeAdvertiser!!.startAdvertising(settings, data, advertiseCallback)
            }
            .subscribeOn(Schedulers.io())
            .andThen(connectedSubject)
            .doFinally {
                bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
            }
    }
}