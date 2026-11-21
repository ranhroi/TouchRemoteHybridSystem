package com.ranhroi.touchremote.models

data class KeyboardModel(
    val label: String,
    val code: String,
    val icon: String = "",
    val isCommand: Boolean = false,
    val os: String = "unknown",
)
