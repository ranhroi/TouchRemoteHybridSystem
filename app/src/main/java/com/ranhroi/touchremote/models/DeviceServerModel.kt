package com.ranhroi.touchremote.models

import com.ranhroi.touchremote.utils.ConstantsUtils

data class DeviceServerModel(
    val id: Int = 0,
    val name: String = "",
    val address: String = "",
    val port: Int = 0,
    val uuid: String = ConstantsUtils.DEFAULT_SERVER_UUID,
    var password: String = "",
    val rememberPass: Boolean = false,
    val bluetooth: Boolean = false,
    val os: String = "unknown",
    val selected: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)