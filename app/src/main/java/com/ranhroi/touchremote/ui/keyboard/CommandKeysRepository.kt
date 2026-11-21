package com.ranhroi.touchremote.ui.keyboard

import com.ranhroi.touchremote.R
import com.ranhroi.touchremote.models.KeyGroup
import com.ranhroi.touchremote.models.KeyboardModel

/**
 * Repository các phím dùng để tổ hợp ShortKey.
 * CHỈ CHỨA DỮ LIỆU — không logic parse/gửi.
 * Việc gửi do InputManager.sendKeyCombo đảm nhận.
 */
object CommandKeysRepository {

    // ============================================================
    // NHÓM 1: MODIFIERS
    // ============================================================
    val modifiers: List<KeyboardModel> = listOf(
        KeyboardModel(label = "Ctrl", code = "CTRL", isCommand = true, os = "all"),
        KeyboardModel(label = "Shift", code = "SHIFT", isCommand = true, os = "all"),
        KeyboardModel(label = "Alt", code = "ALT", isCommand = true, os = "all"),
        KeyboardModel(label = "Win", code = "WIN", isCommand = true, os = "windows"),
        KeyboardModel(label = "Cmd", code = "CMD", isCommand = true, os = "mac"),
    )

    // ============================================================
    // NHÓM 2: CHỮ CÁI A → Z
    // ============================================================
    val letters: List<KeyboardModel> = ('A'..'Z').map { ch ->
        KeyboardModel(label = ch.toString(), code = ch.toString(), isCommand = false, os = "all")
    }

    // ============================================================
    // NHÓM 3: CHỮ SỐ 0 → 9
    // ============================================================
    val digits: List<KeyboardModel> = listOf(
        "1", "2", "3", "4", "5", "6", "7", "8", "9", "0"
    ).map { ch ->
        KeyboardModel(label = ch, code = ch, isCommand = false, os = "all")
    }

    // ============================================================
    // NHÓM 4: KÝ TỰ ĐẶC BIỆT
    // ============================================================
    val symbols: List<KeyboardModel> = listOf(
        // Dấu cơ bản (không cần Shift)
        KeyboardModel("-", "-"),
        KeyboardModel("=", "="),
        KeyboardModel("[", "["),
        KeyboardModel("]", "]"),
        KeyboardModel("\\", "\\"),
        KeyboardModel(";", ";"),
        KeyboardModel("'", "'"),
        KeyboardModel("`", "`"),
        KeyboardModel(",", ","),
        KeyboardModel(".", "."),
        KeyboardModel("/", "/"),

        // Dấu cần Shift
        KeyboardModel("!", "!"),
        KeyboardModel("@", "@"),
        KeyboardModel("#", "#"),
        KeyboardModel("$", "$"),
        KeyboardModel("%", "%"),
        KeyboardModel("^", "^"),
        KeyboardModel("&", "&"),
        KeyboardModel("*", "*"),
        KeyboardModel("(", "("),
        KeyboardModel(")", ")"),
        KeyboardModel("_", "_"),
        KeyboardModel("+", "+"),
        KeyboardModel("{", "{"),
        KeyboardModel("}", "}"),
        KeyboardModel("|", "|"),
        KeyboardModel(":", ":"),
        KeyboardModel("\"", "\""),
        KeyboardModel("~", "~"),
        KeyboardModel("<", "<"),
        KeyboardModel(">", ">"),
        KeyboardModel("?", "?"),
    )

    // ============================================================
    // NHÓM 5: PHÍM CHỨC NĂNG F1 → F12
    // ============================================================
    val functionKeys: List<KeyboardModel> = (1..12).map { i ->
        KeyboardModel(label = "F$i", code = "F$i", isCommand = false, os = "all")
    }

    // ============================================================
    // NHÓM 6: PHÍM ĐIỀU HƯỚNG
    // ============================================================
    val navigation: List<KeyboardModel> = listOf(
        KeyboardModel("Up", "UP", icon = "baseline_arrow_upward"),
        KeyboardModel("Down", "DW", icon = "baseline_arrow_downward"),
        KeyboardModel("Left", "LT", icon = "baseline_arrow_back"),
        KeyboardModel("Right", "RT", icon = "baseline_arrow_forward"),
        KeyboardModel("Home", "HOME", icon = "baseline_home"),
        KeyboardModel("End", "END"),
        KeyboardModel("PgUp", "PGUP", icon = "baseline_keyboard_arrow_up"),
        KeyboardModel("PgDn", "PGDN", icon = "baseline_keyboard_arrow_down"),
        KeyboardModel("Insert", "INS"),
        KeyboardModel("Delete", "DEL"),
    )

    // ============================================================
    // NHÓM 7: PHÍM ĐIỀU KHIỂN
    // ============================================================
    val controlKeys: List<KeyboardModel> = listOf(
        KeyboardModel("Enter", "RTN"),
        KeyboardModel("Esc", "ESC"),
        KeyboardModel("Tab", "TAB"),
        KeyboardModel("Space", "SPC"),
        KeyboardModel("Backspace", "BAS"),
        KeyboardModel("CapsLock", "CAP")
    )

    // ============================================================
    // NHÓM GỘP
    // ============================================================
    val allGroups: List<KeyGroup> = listOf(
        KeyGroup(R.string.key_group_modifiers, modifiers),
        KeyGroup(R.string.key_group_letters, letters),
        KeyGroup(R.string.key_group_digits, digits),
        KeyGroup(R.string.key_group_symbols, symbols),
        KeyGroup(R.string.key_group_function, functionKeys),
        KeyGroup(R.string.key_group_navigation, navigation),
        KeyGroup(R.string.key_group_control, controlKeys),
    )

    // ============================================================
    // HELPERS
    // ============================================================
    val allKeys: List<KeyboardModel> by lazy {
        allGroups.flatMap { it.items }
    }

    fun search(query: String): List<KeyboardModel> {
        if (query.isBlank()) return allKeys
        val q = query.lowercase()
        return allKeys.filter {
            it.label.lowercase().contains(q) || it.code.lowercase().contains(q)
        }
    }

    fun getByCode(code: String): KeyboardModel? =
        allKeys.find { it.code.equals(code, ignoreCase = true) }
}