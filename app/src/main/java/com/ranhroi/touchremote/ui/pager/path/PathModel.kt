package com.ranhroi.touchremote.ui.pager.path

data class PathModel(
    val id: Int = 0,
    val name: String = "",
    val type: String = "",
    val path: String = "",
    val icon: String = "Apps", // Mặc định là biểu tượng Link
    val send: Int = 1, // Mặc định là "1" (có gửi)
    val createdAt: Long = System.currentTimeMillis(), // Gán thời gian tạo mặc định
)