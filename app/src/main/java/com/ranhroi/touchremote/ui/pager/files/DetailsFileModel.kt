package com.ranhroi.touchremote.ui.pager.files

data class DetailsFileModel(
    val isShow: Boolean = false,
    val name: String = "",
    val size: String = "",
    val date: String = "",
    val path: String = "",
    val isDirectory: Boolean = false
)