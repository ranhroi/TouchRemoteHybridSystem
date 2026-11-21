package com.ranhroi.touchremote.ui.screens.server

import com.ranhroi.touchremote.models.DeviceServerModel
import java.io.InputStream
import java.io.OutputStream

data class HybridConnectionModel(
    val role: Byte = 0,
    var socket: Any? = null,
    var input: InputStream? = null,
    var output: OutputStream? = null,
    val isConnected: Boolean = false,
    val isBluetooth: Boolean = false,
    val server: DeviceServerModel = DeviceServerModel(),
    var os: String = "unknown",
    var isNeedPassword: Boolean = false,
    var isPay: Boolean = false,
)