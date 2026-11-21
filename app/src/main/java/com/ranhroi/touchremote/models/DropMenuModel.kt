package com.ranhroi.touchremote.models

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

@Immutable
data class DropMenuModel(
    val text: String,
    val icon: String,
    val isDestructive: Boolean = false,
    val isVisible: () -> Boolean = { true },
    val enabled: Boolean = true,
    val onClick: () -> Unit,
    val content: @Composable () -> Unit = {}
)
