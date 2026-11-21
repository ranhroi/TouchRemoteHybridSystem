package com.ranhroi.touchremote.utils

import android.util.Log
import com.ranhroi.touchremote.R

object IconsMap {
    private const val TAG = "IconsMap"

    // Cache chính – chỉ load 1 lần
    private var cachedMap: Map<String, Int>? = null

    /**
     * Map các icon, được load khi cần (lazy) nhưng có thể khởi tạo sớm từ Application.
     */
    val icon: Map<String, Int>
        get() = cachedMap ?: loadIcons().also { cachedMap = it }

    /**
     * Gọi từ Application để load sớm, tránh giật khi lần đầu dùng.
     */
    fun initialize() {
        if (cachedMap == null) {
            cachedMap = loadIcons()
            Log.d(TAG, "✅ Loaded ${cachedMap?.size} icons")
        }
    }

    /**
     * Reset cache – hữu ích khi muốn reload trong quá trình debug.
     */
    fun resetCache() {
        cachedMap = null
        Log.d(TAG, "🔄 Cache reset, will reload on next access")
    }

    /**
     * Load danh sách icon từ R.drawable và R.mipmap.
     * Chỉ lấy những resource có tên bắt đầu bằng baseline_, outline_ hoặc ic_.
     * Ưu tiên drawable nếu trùng tên (vì vector đẹp hơn).
     */
    private fun loadIcons(): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        // Danh sách tên icon cần loại bỏ (launcher icons, splash...)
        val excludeNames = setOf(
            "ic_launcher", "ic_launcher_foreground", "ic_launcher_background",
            "splash_icon", "ic_launcher_round"
        )
        try {
            // 1. Lấy từ thư mục drawable
            R.drawable::class.java.fields.forEach { field ->
                val name = field.name
                val isValid = name.startsWith("baseline_") ||
                              name.startsWith("outline_") ||
                              name.startsWith("ic_")
                if (isValid && !excludeNames.contains(name)) {
                    map[name] = field.getInt(null)
                }
            }

            // 2. Lấy từ thư mục mipmap (chỉ lấy ic_* và chưa có trong map)
            R.mipmap::class.java.fields.forEach { field ->
                val name = field.name
                if (name.startsWith("ic_") && !excludeNames.contains(name)) {
                    if (!map.containsKey(name)) {
                        map[name] = field.getInt(null)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading icons: ${e.message}")
        }

        // Đảm bảo luôn có fallback
        if (!map.containsKey("baseline_help")) {
            map["baseline_help"] = R.drawable.baseline_help
        }
        return map
    }

    /**
     * Lấy resource ID của icon theo tên, nếu không có thì trả về help icon.
     */
    fun getIconOrDefault(iconName: String): Int =
        icon[iconName] ?: R.drawable.baseline_help

    /**
     * Kiểm tra icon có tồn tại không.
     */
    fun hasIcon(iconName: String): Boolean = icon.containsKey(iconName)
}