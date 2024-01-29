package com.algorigo.algorigobleservice

import android.bluetooth.BluetoothGattService

interface BleGattDelegate {
    fun getServices(): List<BluetoothGattService>
    fun getAdvertiseOption(): BleAdvertiseOption
    fun handleEvent(event: BleGattServiceGenerator.BluetoothServiceEvent)
}
