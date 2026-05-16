package com.example.pumpble.app

import android.bluetooth.BluetoothDevice

data class DiscoveredPump(
    val name: String,
    val address: String,
    val rssi: Int,
    val device: BluetoothDevice,
)