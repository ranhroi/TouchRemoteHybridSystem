package com.ranhroi.touchremote.ui.screens.remote

data class RemoteModel(
    val id: String,
    val name: String,
    val description: String,
    val encoderType: String = "DEFAULT",  // RY, RAH, ASTA, DEFAULT
    val deviceFamilyByte: Byte = 0x14,
    val hasAirClean: Boolean = false,
    val hasCoilDry: Boolean = false,
    val minTemp: Int = 16,
    val maxTemp: Int = 30,
    val protocol: String = "FUJITSU",
    val hasTimer: Boolean = true,
    val hasQuiet: Boolean = false,
    val hasPowerful: Boolean = false,
    val hasSwing: Boolean = true,
    val hasSleep: Boolean = true,
    val airCleanModes: List<String> = listOf("FAN", "COOL", "DRY"),
    val coilDryModes: List<String> = listOf("COOL", "DRY")
)