package com.ranhroi.touchremote.ui.pager.application

import android.graphics.Bitmap

data class DockInfoModel(
    val name: String = "",
    val bundleID: String = "",
    val running: Boolean = false,
    val icon: Bitmap? = null // nên nullable để khởi tạo trước khi có icon
)