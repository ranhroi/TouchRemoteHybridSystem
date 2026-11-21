package com.ranhroi.touchremote.ui.screens.server

import com.ranhroi.touchremote.models.DeviceServerModel

// ================= UI STATE =================
data class ConnectionUiModel(
    val isConnected: Boolean = false,
    val isLoading: Boolean = false,
    var password: String = "",
    val showPasswordDialog: Boolean = false,
    val servers: List<DeviceServerModel> = emptyList(),
    val state: HybridConnectionModel = HybridConnectionModel(),
    val statusMessage: String = "",
    val errorMessage: String? = null
)

