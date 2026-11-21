package com.ranhroi.touchremote.ui.pager.terminal

data class RunCommandModel(
    val commandOutput: String = "Output:",
    val suggestions: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val isConnected: Boolean = true
)