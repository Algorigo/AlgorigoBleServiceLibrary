package com.algorigo.algorigobleservice

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattService
import com.jakewharton.rxrelay3.PublishRelay
import io.reactivex.rxjava3.core.Observable

abstract class BleGattDelegate<T> {

    sealed class DelegateEvent<T> {
        class ClientConnected<T>(val client: BluetoothDevice) : DelegateEvent<T>()
        class Event<T>(val event: T) : DelegateEvent<T>()
        class ClientDisconnected<T>(val client: BluetoothDevice) : DelegateEvent<T>()
    }

    private val eventRelay = PublishRelay.create<DelegateEvent<T>>()

    abstract fun getServices(): List<BluetoothGattService>
    abstract fun getAdvertiseOption(): BleAdvertiseOption

    final fun handleEventOuter(event: BleGattServiceGenerator.BluetoothServiceEvent) {
        handleEvent(event)
        when (event) {
            is BleGattServiceGenerator.BluetoothServiceEvent.ClientConnected -> {
                eventRelay.accept(DelegateEvent.ClientConnected(event.client))
            }
            is BleGattServiceGenerator.BluetoothServiceEvent.ClientDisconnected -> {
                eventRelay.accept(DelegateEvent.ClientDisconnected(event.client))
            }
            else -> {
                // do nothing
            }
        }
    }

    abstract fun handleEvent(event: BleGattServiceGenerator.BluetoothServiceEvent)

    fun getEventObservable(): Observable<DelegateEvent<T>> {
        return eventRelay
    }

    protected fun emitEvent(event: T) {
        eventRelay.accept(DelegateEvent.Event(event))
    }
}
