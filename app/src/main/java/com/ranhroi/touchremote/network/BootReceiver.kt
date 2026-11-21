package com.ranhroi.touchremote.network

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager

class BootReceiver(
    private val onWifiChanged: (Boolean) -> Unit,
    private val onBluetoothChanged: (Boolean) -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {

            WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                val state = intent.getIntExtra(
                    WifiManager.EXTRA_WIFI_STATE,
                    WifiManager.WIFI_STATE_UNKNOWN
                )

                when (state) {
                    WifiManager.WIFI_STATE_ENABLED -> {
                        onWifiChanged(true)
                    }

                    WifiManager.WIFI_STATE_DISABLED -> {
                        onWifiChanged(false)
                    }
                }
            }

            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(
                    BluetoothAdapter.EXTRA_STATE,
                    BluetoothAdapter.ERROR
                )

                when (state) {
                    BluetoothAdapter.STATE_ON -> onBluetoothChanged(true)
                    BluetoothAdapter.STATE_OFF -> onBluetoothChanged(false)
                }
            }
        }
    }
}

