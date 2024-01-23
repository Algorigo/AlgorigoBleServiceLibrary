package com.algorigo.algorigobleservicelibrary.ble_service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.content.Context.BATTERY_SERVICE
import android.os.BatteryManager
import com.algorigo.algorigobleservice.BleGattDelegate
import com.algorigo.algorigobleservicelibrary.util.throttle
import com.algorigo.algorigobleservicelibrary.util.toByteArray
import com.jakewharton.rxrelay3.PublishRelay
import com.rouddy.twophonesupporter.BleAdvertiseOption
import com.rouddy.twophonesupporter.BleGattServiceGenerator
import io.reactivex.rxjava3.core.Observable
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit

@SuppressLint("MissingPermission")
class MyGattDelegate(context: Context) : BleGattDelegate {

    private val batteryManager: BatteryManager

    init {
        batteryManager = context.getSystemService(BATTERY_SERVICE) as BatteryManager
    }

    override fun getServices(): List<BluetoothGattService> {
        return listOf(
            BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY).apply {
                addCharacteristic(BleGattServiceGenerator.initWritableCharacteristic(WRITE_CHARACTERISTIC_UUID))
                addCharacteristic(BleGattServiceGenerator.initNotifyCharacteristic(NOTIFY_CHARACTERISTIC_UUID))
            },
            BleGattServiceGenerator.initBatteryService()
        )
    }

    override fun getAdvertiseOption(): BleAdvertiseOption {
        return BleAdvertiseOption
            .Builder()
            .setName("TestBle")
            .setUuid(SERVICE_UUID)
            .build()
    }

    override fun handleEvent(event: BleGattServiceGenerator.BluetoothServiceEvent) {
        when (event) {
            is BleGattServiceGenerator.BluetoothServiceEvent.CharacteristicReadRequest -> {
                if (event.characteristic.uuid == BleGattServiceGenerator.UUID_BATTERY_LEVEL) {
                    handleReadEvent(event.callback)
                }
            }

            is BleGattServiceGenerator.BluetoothServiceEvent.CharacteristicWriteRequest -> {
                event.callback(event.value)
            }

            is BleGattServiceGenerator.BluetoothServiceEvent.NotificationStartRequest -> {
                if (event.characteristic.uuid == NOTIFY_CHARACTERISTIC_UUID) {
                    event.callback(Observable
                        .interval(1, TimeUnit.SECONDS)
                        .map { it.toByteArray() }
                        .throttle(event.onNotificationSentObservable))
                }
            }

            else -> {}
        }
    }

    private fun handleReadEvent(callback: (ByteArray) -> Boolean) {
        callback(
            byteArrayOf(
                batteryManager
                    .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    .toByte()
            )
        )
    }

    companion object {
        private val LOG_TAG = MyGattDelegate::class.java.simpleName

        val SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-0123456789AB")
        val WRITE_CHARACTERISTIC_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-0123456789AB")
        val NOTIFY_CHARACTERISTIC_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-0123456789AB")
    }
}
