package com.algorigo.algorigobleservice

import android.Manifest
import android.bluetooth.*
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresPermission
import com.algorigo.algorigobleservice.BleGattDelegate
import com.jakewharton.rxrelay3.BehaviorRelay
import com.jakewharton.rxrelay3.PublishRelay
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.UUID

class BleGattServiceGenerator {

    enum class NotificationType {
        Notification, Indication,
    }

    private sealed class DeviceConnection {
        object NoDevice : DeviceConnection()
        class Connection(val bluetoothDevice: BluetoothDevice) : DeviceConnection()
    }

    private sealed class State {
        object WaitForConnect : State() {
            override fun equals(other: Any?): Boolean {
                return other is WaitForConnect
            }
        }

        class Connected(val device: BluetoothDevice) : State() {
            override fun equals(other: Any?): Boolean {
                return other is Connected && device.address.equals(other.device.address)
            }
        }
    }

    sealed class BluetoothServiceEvent {
        class ClientConnected(val client: BluetoothDevice) : BluetoothServiceEvent()
        class CharacteristicReadRequest(val characteristic: BluetoothGattCharacteristic, val callback: (ByteArray) -> Boolean) : BluetoothServiceEvent()
        class CharacteristicWriteRequest(val characteristic: BluetoothGattCharacteristic, val value: ByteArray, val callback: (ByteArray) -> Boolean) : BluetoothServiceEvent()
        class NotificationStartRequest(val characteristic: BluetoothGattCharacteristic, val type: NotificationType, val onNotificationSentObservable: Observable<Any>, val callback: (Observable<ByteArray>) -> Unit) : BluetoothServiceEvent()
        class ClientDisconnected(val client: BluetoothDevice) : BluetoothServiceEvent()
    }

    private var connectedDevice: BluetoothDevice? = null
    private val gattServerRelay = BehaviorRelay.create<BluetoothGattServer>()
    private val serviceAddedRelay = BehaviorRelay.create<BluetoothGattService>()
    private val gattServer: BluetoothGattServer
        get() = gattServerRelay
            .firstOrError()
            .blockingGet()
    lateinit var address: String
        private set
    private var stateRelay = BehaviorRelay
        .create<State>()
        .apply { accept(State.WaitForConnect) }
    private var eventRelay = PublishRelay.create<BluetoothServiceEvent>()
    private var onNotificationSentRelay = PublishRelay.create<Any>()
    private var notificationMap = mutableMapOf<UUID, NotificationType>()
    private var disposableMap = mutableMapOf<UUID, Disposable>()

    private val gattCallback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            super.onServiceAdded(status, service)
            if (service != null && status == 0) {
                serviceAddedRelay.accept(service)
            }
        }

        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            if (newState == BluetoothGattServer.STATE_CONNECTED) {
                gattServer.connect(device!!, false)
                onConnected(device)
            } else if (newState == BluetoothGattServer.STATE_DISCONNECTED || newState == BluetoothGattServer.STATE_DISCONNECTING) {
                onDisconnected()
            }
        }

        override fun onCharacteristicReadRequest(device: BluetoothDevice?, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic?) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
            eventRelay.accept(BluetoothServiceEvent.CharacteristicReadRequest(characteristic!!) {
                gattServer.sendResponse(device!!, requestId, BluetoothGatt.GATT_SUCCESS, offset, it)
            })

        }

        override fun onCharacteristicWriteRequest(device: BluetoothDevice?, requestId: Int, characteristic: BluetoothGattCharacteristic?, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)
            eventRelay.accept(BluetoothServiceEvent.CharacteristicWriteRequest(characteristic!!, value!!) {
                gattServer.sendResponse(device!!, requestId, BluetoothGatt.GATT_SUCCESS, offset, it)
            })
        }

        override fun onDescriptorReadRequest(device: BluetoothDevice?, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor?) {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor)
            when (notificationMap[descriptor!!.characteristic.uuid]) {
                NotificationType.Notification -> {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                }

                NotificationType.Indication -> {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                }

                null -> {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
                }
            }
        }

        override fun onDescriptorWriteRequest(device: BluetoothDevice?, requestId: Int, descriptor: BluetoothGattDescriptor?, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value)
            var type = when {
                value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) -> {
                    NotificationType.Notification
                }

                value.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE) -> {
                    NotificationType.Indication
                }

                else -> {
                    null
                }
            }

            if (type == notificationMap[descriptor!!.characteristic.uuid]) {
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                return
            }

            disposableMap
                .remove(descriptor.characteristic.uuid)
                ?.dispose()

            if (type == null) {
                notificationMap.remove(descriptor.characteristic.uuid)
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                return
            }

            val supportTypes = (if (descriptor.characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                listOf(NotificationType.Notification)
            } else listOf()) + (if (descriptor.characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) {
                listOf(NotificationType.Indication)
            } else listOf())

            if (!supportTypes.contains(type)) {
                if (supportTypes.isEmpty()) {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, value)
                    return
                } else {
                    type = supportTypes.first()
                }
            }

            val confirm = when (type) {
                NotificationType.Notification -> {
                    false
                }

                NotificationType.Indication -> {
                    true
                }
            }

            val notifyData: (ByteArray) -> Unit = {
                if (Build.VERSION.SDK_INT >= 33) {
                    gattServer.notifyCharacteristicChanged(device!!, descriptor.characteristic, confirm, it)
                } else {
                    descriptor.characteristic.value = it
                    gattServer.notifyCharacteristicChanged(device!!, descriptor.characteristic, confirm)
                }
            }

            notificationMap[descriptor.characteristic.uuid] = type

            eventRelay.accept(BluetoothServiceEvent.NotificationStartRequest(descriptor.characteristic, type, onNotificationSentRelay) {
                disposableMap[descriptor.characteristic.uuid] = it
                    .onErrorComplete()
                    .doFinally {
                        notificationMap.remove(descriptor.characteristic.uuid)
                        disposableMap.remove(descriptor.characteristic.uuid)
                    }
                    .subscribe(notifyData)
            })

            gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
        }

        override fun onNotificationSent(device: BluetoothDevice?, status: Int) {
            super.onNotificationSent(device, status)
            onNotificationSentRelay.accept(1)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    internal fun initialize(context: Context, services: List<BluetoothGattService>, option: BleAdvertiseOption): Observable<BluetoothServiceEvent> {
        return eventRelay
            .mergeWith(Completable
                .fromCallable {
                    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                    address = bluetoothManager.adapter.address
                    bluetoothManager
                        .openGattServer(context, gattCallback)
                        .let {
                            gattServerRelay.accept(it)
                        }
                }
                .andThen(Completable.concat(services.map { addService(it) }))
                .andThen(stateRelay)
                .distinctUntilChanged()
                .switchMap {
                    when (it) {
                        is State.WaitForConnect -> {
                            Observable
                                .just(DeviceConnection.NoDevice)
                                .mergeWith(
                                    BleAdvertiser
                                        .startAdvertising(context, option)
                                        .ignoreElements()
                                )
                        }

                        is State.Connected -> {
                            Observable.just(DeviceConnection.Connection(it.device))
                        }
                    }
                }
                .buffer(2, 1)
                .flatMapMaybe {
                    val prev = it[0]
                    val next = it[1]
                    if (prev is DeviceConnection.Connection && next is DeviceConnection.NoDevice) {
                        Maybe.just(BluetoothServiceEvent.ClientDisconnected(prev.bluetoothDevice))
                    } else if (prev is DeviceConnection.NoDevice && next is DeviceConnection.Connection) {
                        Maybe.just(BluetoothServiceEvent.ClientConnected(next.bluetoothDevice))
                    } else {
                        Maybe.empty()
                    }
                })
            .doFinally {
                disconnectDevice()
                gattServer.close()
            }
    }

    private fun addService(service: BluetoothGattService): Completable {
        return serviceAddedRelay
            .doOnSubscribe {
                gattServer.addService(service)
            }
            .filter { service.uuid == it.uuid }
            .firstOrError()
            .ignoreElement()
    }

    private fun onConnected(bluetoothDevice: BluetoothDevice) {
        connectedDevice = bluetoothDevice
        stateRelay.accept(State.Connected(bluetoothDevice))
    }

    private fun onDisconnected() {
        connectedDevice = null
        stateRelay.accept(State.WaitForConnect)
    }

    private fun disconnectDevice() {
        connectedDevice?.let {
            gattServer.cancelConnection(it)
        }
        connectedDevice = null
    }

    companion object {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        fun startServer(context: Context, services: List<BluetoothGattService>, option: BleAdvertiseOption): Observable<BluetoothServiceEvent> {
            return BleGattServiceGenerator()
                .initialize(context, services, option)
                .subscribeOn(Schedulers.io())
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        fun startServer(context: Context, bleGattDelgate: BleGattDelegate): Observable<BluetoothServiceEvent> {
            return startServer(context, bleGattDelgate.getServices(), bleGattDelgate.getAdvertiseOption())
        }

        fun initWritableCharacteristic(uuid: UUID): BluetoothGattCharacteristic {
            return BluetoothGattCharacteristic(
                uuid, BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE
            )
        }

        fun initReadableCharacteristic(uuid: UUID): BluetoothGattCharacteristic {
            return BluetoothGattCharacteristic(
                uuid, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ
            )
        }

        fun initIndicateCharacteristic(uuid: UUID): BluetoothGattCharacteristic {
            return BluetoothGattCharacteristic(
                uuid, BluetoothGattCharacteristic.PROPERTY_INDICATE, BluetoothGattCharacteristic.PERMISSION_WRITE
            ).apply {
                addDescriptor(BluetoothGattDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"), BluetoothGattCharacteristic.PERMISSION_WRITE))
            }
        }

        fun initNotifyCharacteristic(uuid: UUID): BluetoothGattCharacteristic {
            return BluetoothGattCharacteristic(
                uuid, BluetoothGattCharacteristic.PROPERTY_NOTIFY, BluetoothGattCharacteristic.PERMISSION_WRITE
            ).apply {
                addDescriptor(BluetoothGattDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"), BluetoothGattCharacteristic.PERMISSION_WRITE))
            }
        }

        fun initBatteryService(): BluetoothGattService {
            return BluetoothGattService(
                UUID_BATTERY_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY
            ).apply {
                addCharacteristic(initReadableCharacteristic(UUID_BATTERY_LEVEL))
            }
        }

        val UUID_BATTERY_SERVICE = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")
        val UUID_BATTERY_LEVEL = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")
    }
}