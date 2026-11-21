package com.ranhroi.touchremote.models

import androidx.annotation.StringRes

data class KeyGroup(
    @StringRes val titleRes: Int,
    val items: List<KeyboardModel>
)