package com.ranhroi.touchremote.ui.pager.files

// ==================== DOWNLOAD PROGRESS DATA CLASS ====================
data class DownloadProgressModel(
    val id: Int = 0,
    val fileName: String = "",
    val fileSize: Long = 0L,
    val offset: Long = 0L,
    val totalChunks: Int = 0,
    val lastModified: Long = 0L
)