package com.ranhroi.touchremote.send

import com.ranhroi.touchremote.models.CombineSettingModel
import com.ranhroi.touchremote.models.QuickKeysModel
import com.ranhroi.touchremote.models.ShortKeyModel
import com.ranhroi.touchremote.ui.pager.path.PathModel
import com.ranhroi.touchremote.ui.screens.server.HybridConnectionModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class InputManager(
    private val senderOutput: SenderOutput,
    private val configProvider: () -> CombineSettingModel?
) {
    private val config: CombineSettingModel?
        get() = configProvider()

    private val _latchedModifiers = MutableStateFlow<Set<String>>(emptySet())
    val latchedModifiers: StateFlow<Set<String>> = _latchedModifiers.asStateFlow()

    private val _momentaryModifiers = MutableStateFlow<Set<String>>(emptySet())
    val momentaryModifiers: StateFlow<Set<String>> = _momentaryModifiers.asStateFlow()

    // Chỉ dùng để launch — không channel, không writer loop
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val comboDelayMs = 8L

    // ============================================================
    // MODIFIERS
    // ============================================================
    fun toggleModifier(name: String) {
        val canonical = normalizeModifier(name) ?: return
        val current = _latchedModifiers.value.toMutableSet()
        if (canonical in current) current.remove(canonical) else current.add(canonical)
        _latchedModifiers.value = current
    }

    fun setMomentaryModifier(name: String, isDown: Boolean) {
        val canonical = normalizeModifier(name) ?: return
        val current = _momentaryModifiers.value.toMutableSet()
        if (isDown) current.add(canonical) else current.remove(canonical)
        _momentaryModifiers.value = current
    }

    fun currentActiveModifiers(): List<String> {
        val set = _latchedModifiers.value + _momentaryModifiers.value
        val order = listOf("CTRL", "SHIFT", "ALT", "WIN", "CMD")
        return order.filter { it in set }
    }

    fun clearModifiers() {
        _latchedModifiers.value = emptySet()
        _momentaryModifiers.value = emptySet()
    }

    fun normalizeModifier(name: String): String? = when (name.uppercase()) {
        "CTRL", "CONTROL", "LCTRL", "RCTRL" -> "CTRL"
        "SHIFT", "LSHIFT", "RSHIFT" -> "SHIFT"
        "ALT", "LALT", "RALT", "OPTION" -> "ALT"
        "CMD", "COMMAND" -> "CMD"
        "WIN", "SUPER", "META", "GUI" -> "WIN"
        else -> null
    }

    // ============================================================
    // sendKeySequence — gọi thẳng SenderOutput
    // ============================================================
    private fun sendKeySequence(keys: List<String>) {
        scope.launch {
            if (keys.isEmpty()) return@launch

            val upper = keys.map { it.trim().uppercase() }

            upper.forEach { key ->
                senderOutput.send_key_down(key)
                delay(comboDelayMs.milliseconds)
            }
            upper.reversed().forEach { key ->
                senderOutput.send_key_up(key)
                delay(comboDelayMs.milliseconds)
            }
        }
    }

    // ============================================================
    // sendKeyCombo
    // ============================================================
    fun sendKeyCombo(keys: List<String>, send: Int, char: Boolean) {
        if (keys.isEmpty()) return

        if (keys.size == 1 && !char) {
            // Lệnh text đơn — gửi thô
            when (send) {
                0 -> senderOutput.send_key(keys[0])
                1 -> senderOutput.send_string_message(keys[0])
                else -> sendKeySequence(keys)
            }
        } else {
            // Phím ký tự đơn HOẶC combo nhiều phím — luôn down/up
            sendKeySequence(keys)
        }
    }

    // ============================================================
    // BÀN PHÍM NHẬP LIỆU
    // ============================================================
    fun sendComboFromCodes(codes: List<String>, send: Int = 1): Boolean {
        if (codes.isEmpty()) return false
        val mods = mutableListOf<String>()
        val keys = mutableListOf<String>()
        for (code in codes) {
            val c = normalizeModifier(code)
            if (c != null) {
                if (c !in mods) mods.add(c)
            } else keys.add(code)
        }
        if (keys.isEmpty()) {
            if (mods.isEmpty()) return false
            sendKeySequence(mods)
            return true
        }
        sendKeyCombo(mods + keys, send, true)
        return true
    }

    // ============================================================
    // INSTANT MODE
    // ============================================================
    fun sendKeyTap(key: String) {
        val mods = currentActiveModifiers()
        sendKeySequence(mods + key)
    }

    fun sendKeyTap(key: String, extraModifiers: List<String>) {
        val mods = (currentActiveModifiers() + extraModifiers).distinct()
        sendKeySequence(mods + key)
    }

    // ============================================================
    // TEXT / BACKSPACE / ENTER / TAB
    // ============================================================
    fun onTextInput(text: String) {
        if (text.isEmpty()) return
        senderOutput.send_string_keys(text)
    }

    fun sendBackspace() = senderOutput.send_key("BAS")

    fun sendEnter() = senderOutput.send_key("RTN")


    fun sendTab() = senderOutput.send_key("TAB")


    // ============================================================
    // EXECUTE SHORTCUT / QUICK KEYS
    // ============================================================
    fun executeShortKey(shortcut: ShortKeyModel): Boolean {
        val osIndex = config?.osIndex ?: 0
        val combo = shortcut.keys.getOrNull(osIndex) ?: shortcut.keys.getOrNull(0) ?: return false
        if (combo.isEmpty()) return false
        sendKeyCombo(combo, shortcut.send, shortcut.isChar)
        return true
    }

    fun executeQuickKeys(quickKey: QuickKeysModel): Boolean {
        val osIndex = config?.osIndex ?: 0
        val combo = quickKey.keys.getOrNull(osIndex) ?: quickKey.keys.getOrNull(0) ?: return false
        if (combo.isEmpty()) return false

        // Ghép modifier + key và gửi
        sendKeyCombo(combo, 1, true)
        return true
    }


    fun sendKeyComboDirect(codes: List<String>, send: Int = 1, isChar: Boolean): Boolean {
        if (codes.isEmpty()) return false
        sendKeyCombo(codes, send, isChar)
        return true
    }


    fun handlePath(path: PathModel): Boolean {
        val osIndex = config?.osIndex ?: 0
        if (osIndex >= 0 && path.name.isNotEmpty() && path.path.isNotEmpty() && path.send != 0) {
            if (path.send == 1) {
                senderOutput.send_string_message("${path.type} ${path.path}")
                return true
            }
        }
        return false
    }

    // ============================================================
    // MOUSE
    // ============================================================
    fun onMouseMove(dx: Int, dy: Int) = senderOutput.send_mouse(dx, dy)

    fun sendDragStart() = senderOutput.send_drag_start()
    fun sendDragEnd() = senderOutput.send_drag_end()

    fun onMouseScrollVertical(direction: Boolean, steps: Int) =
        senderOutput.send_mouse_middlescroll(direction, steps)

    fun onMouseScrollHorizontal(direction: Boolean, steps: Int = 0) =
        senderOutput.send_mouse_hscroll(direction, steps)

    fun onLeftClick() = senderOutput.send_mouse_click()

    fun onRightClick() = senderOutput.send_mouse_realrightclick()

    fun onMiddleClick() = senderOutput.send_middle_button_click()

    fun sendMouseRightDown() = senderOutput.send_mouse_right_down()
    fun sendMouseRightUp() = senderOutput.send_mouse_right_up()

    fun sendMouseLeftDown() = senderOutput.send_mouse_leftdown()
    fun sendMouseLeftUp() = senderOutput.send_mouse_leftup()

    fun onMouseButtonDown(isLeft: Boolean) {
        if (isLeft) senderOutput.send_mouse_leftdown()
        else senderOutput.send_mouse_right_down()
    }

    fun onMouseButtonUp(isLeft: Boolean) {
        if (isLeft) senderOutput.send_mouse_leftup()
        else senderOutput.send_mouse_right_up()
    }

    fun sendKey(str: String) = senderOutput.send_key(str)

    fun sendStringMessage(str: String): Boolean = senderOutput.send_string_message(str)

    // ============================================================
    // CLEANUP
    // ============================================================
    fun close() {
        clearModifiers()
    }

    fun destroy() {
        scope.cancel()
        clearModifiers()
    }
}