package com.ranhroi.touchremote.ui.pager.files

data class ExtractRequestModel(
    val folderName: String,
    val zipPath: String,
    val onResult: (Boolean) -> Unit
)