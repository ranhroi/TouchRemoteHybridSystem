package com.ranhroi.touchremote.scanner

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import com.ranhroi.touchremote.utils.BluetoothPermissionHandler

class BluetoothScanner(private val context: Context) {
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    fun getPairedDevices(): Set<BluetoothDevice> {
        return if (adapter?.isEnabled == true || BluetoothPermissionHandler.checkSelfPermission(
                context
            )
        ) adapter?.bondedDevices
            ?: emptySet() else emptySet()
    }

    fun startScan() {
        if (BluetoothPermissionHandler.checkSelfPermission(context)) adapter?.startDiscovery()
    }

    fun stopScan() {
        if (BluetoothPermissionHandler.checkSelfPermission(context)) adapter?.cancelDiscovery()
    }
}
