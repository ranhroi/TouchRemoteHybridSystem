package com.ranhroi.touchremote.ui.pager.files

data class ProgressDialogStateModel(
    val show: Boolean = false,
    val title: String = "",
    val name: String = "",
    val message: String = "",
    val progress: Float? = null, // null = indeterminate
    val detail: String? = null
)