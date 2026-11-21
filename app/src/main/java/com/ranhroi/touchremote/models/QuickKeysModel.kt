package com.ranhroi.touchremote.models

import java.util.Locale.getDefault


data class QuickKeysModel(
    val id: Int = 0,
    val name: String = "",
    val openApp: Boolean = false,
    val keys: List<List<String>> = listOf(emptyList(), emptyList(), emptyList()),
    val icon: String = "",
    val preset: String = "",
    val presetId: String = preset.replace(" ", "").trim().lowercase(getDefault())
)