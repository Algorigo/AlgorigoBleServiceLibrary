package com.algorigo.algorigobleservice

import android.bluetooth.BluetoothGattService
import com.rouddy.twophonesupporter.BleAdvertiseOption
import com.rouddy.twophonesupporter.BleGattServiceGenerator

interface BleGattDelegate {
    fun getServices(): List<BluetoothGattService>
    fun getAdvertiseOption(): BleAdvertiseOption
    fun handleEvent(event: BleGattServiceGenerator.BluetoothServiceEvent)
}
