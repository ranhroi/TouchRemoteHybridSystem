package com.ranhroi.touchremote.ui.screens.remote

data class RemoteState(
    val isOn: Boolean = false,
    val temp: Int = 24,
    val mode: String = "COOL",      // COOL, HEAT, DRY, FAN, AUTO
    val fanSpeed: String = "AUTO",  // AUTO, HIGH, MED, LOW, QUIET
    val isSwing: Boolean = false,
    val isCoilDry: Boolean = false,
    val isAirClean: Boolean = false,
    val isSleep: Boolean = false,
    val timerMode: String = "NONE", // NONE, ON, OFF
    val timerHour: Int = 0,
    val isQuiet: Boolean = false,
    val isPowerful: Boolean = false,
)