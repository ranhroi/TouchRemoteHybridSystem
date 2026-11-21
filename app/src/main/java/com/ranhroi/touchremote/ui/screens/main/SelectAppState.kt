package com.ranhroi.touchremote.ui.screens.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class SelectAppState {
    var bottomIndex by mutableIntStateOf(-1)
    var tabIndex by mutableIntStateOf(0)
    var isCommandKey by mutableStateOf(false)

    // Method để reset state
    fun resetState() {
        bottomIndex = -1
        isCommandKey = false
    }

}