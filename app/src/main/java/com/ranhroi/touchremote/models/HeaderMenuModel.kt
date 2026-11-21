package com.ranhroi.touchremote.models

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Immutable
data class HeaderMenuModel(
    val text: String? = null,
    val icon: String? = null,
    val textFontSize: Int = 12,
    val textColor: Color? = null,
    val iconTint: Color? = null,
    val isDestructive: Boolean = false,
    val isStatusDot: Boolean = false,
    val isConnected: Boolean = false,
    val isVisible: () -> Boolean = { true },
    val enabled: Boolean = true,
    val onClick: (() -> Unit) = {},
    val modifier: Modifier = Modifier,
    val content: @Composable () -> Unit = {}
)