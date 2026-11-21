package com.ranhroi.touchremote.send

import com.ranhroi.touchremote.models.CombineSettingModel
import com.ranhroi.touchremote.ui.screens.server.HybridConnectionModel
import com.ranhroi.touchremote.utils.ConstantsUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import kotlin.math.abs
import kotlin.math.max


class SenderOutput {

    // ============================================================
    // STATE
    // ============================================================
    private var cursorProgress = 50
    private var cursorScroll = 50
    private var isScrollDirection = false
    private var lastSendMouseTime: Long = 0
    private var lastx = 0
    private var lasty = 0

    private var currentState: HybridConnectionModel? = null
    private var config: CombineSettingModel? = null

    var os: Int = ConstantsUtils.SERVER_TYPE_UNKNOW

    // ============================================================
    // SCOPE + CHANNELS
    //  - keyboardChannel: UNLIMITED — không mất phím
    //  - mouseChannel:    capacity=2, DROP_OLDEST — realtime
    // ============================================================
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val keyboardChannel = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    private val mouseChannel = Channel<suspend () -> Unit>(
        capacity = 2,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private var workerJob: Job? = null

    init {
       workerJob = scope.launch {
            while (isActive) {
                // Ưu tiên mouse trước, sau đó keyboard
                mouseChannel.tryReceive().getOrNull()?.invoke()
                    ?: keyboardChannel.tryReceive().getOrNull()?.invoke()
                    ?: select {
                        mouseChannel.onReceiveCatching { it.getOrNull()?.invoke() }
                        keyboardChannel.onReceiveCatching { it.getOrNull()?.invoke() }
                    }
            }
        }
    }

    // ============================================================
    // ENQUEUE
    // ============================================================
    private fun enqueueKeyboard(block: suspend () -> Unit) {
        keyboardChannel.trySend(block)
    }

    private fun enqueueMouse(block: suspend () -> Unit) {
        mouseChannel.trySend(block)
    }

    // ============================================================
    // LOW-LEVEL WRITE
    // ============================================================
    private fun writeBytes(bytes: ByteArray) {
        scope.launch {
            try {
                getOutputStream()?.write(bytes)
                getOutputStream()?.flush()
            } catch (_: Exception) {
            }
        }
    }

    private fun writeString(str: String) {
        scope.launch {
            try {
                getOutputStream()?.write(str.toByteArray())
                getOutputStream()?.flush()
            } catch (_: Exception) {
            }
        }
    }

    private fun keyHeader(len: Int): String {
        if (len >= 100) return ""
        return if (len < 10) "key  $len" else "key $len"
    }

    fun refreshConfig(config: CombineSettingModel?, state: HybridConnectionModel) {
        this.config = config
        this.currentState = state
        this.os = ConstantsUtils.getSystemInfoInt(state.os)
    }

    fun updateSystemInfo(systemInfo: String) {
        this.os = ConstantsUtils.getSystemInfoInt(systemInfo)
    }

    private fun getOutputStream(): OutputStream? {
        return currentState?.output
    }

    // ============================================================
    // KEYBOARD — enqueue vào keyboardChannel
    // ============================================================
    fun send_key_down(str: String) {
        val key = remapModifierForOs(str)
        enqueueKeyboard {
            val body = "[R] $key d"
            val header = keyHeader(body.length)
            if (header.isNotEmpty()) writeString(header + body)
        }
    }

    fun send_key_up(str: String) {
        val key = remapModifierForOs(str)
        enqueueKeyboard {
            val body = "[R] $key u"
            val header = keyHeader(body.length)
            if (header.isNotEmpty()) writeString(header + body)
        }
    }

    fun send_key(str: String) {
        enqueueKeyboard {
            val header = keyHeader(str.length)
            if (header.isNotEmpty()) writeString(header + str)
        }
    }

    fun send_string_message(str: String): Boolean {
        enqueueKeyboard { writeString("$str\n") }
        return true
    }

    fun send_string_keys(str: String) {
        enqueueKeyboard {
            writeBytes(("utf8 $str\n").toByteArray(StandardCharsets.UTF_8))
        }
    }

    // ============================================================
    // MOUSE MOVE — enqueue vào mouseChannel
    // (vẫn giữ throttle + accumulate như cũ)
    // ============================================================
    fun send_mouse(dx: Int, dy: Int): Boolean {
        val now = System.nanoTime()

        if (now - lastSendMouseTime < 8_000_000) {
            lastx += dx
            lasty += dy
            return false
        }

        val x = dx + lastx
        val y = dy + lasty

        lastx = 0
        lasty = 0
        lastSendMouseTime = now

        if (x == 0 && y == 0) return true

        val max = max(abs(x), abs(y))

        val sx: Int
        val sy: Int

        if (max <= 10) {
            sx = x; sy = y
        } else if (max <= 20) {
            sx = ((x * 1.1f) - 1f).toInt()
            sy = ((y * 1.1f) - 1f).toInt()
        } else {
            sx = ((x * 1.3f) - 5f).toInt()
            sy = ((y * 1.3f) - 5f).toInt()
        }

        val mx = (sx * (cursorProgress + 50)) / 100
        val my = (sy * (cursorProgress + 50)) / 100

        val body = "m $mx $my"
        val header = if (body.length >= 10) "mos ${body.length}" else "mos  ${body.length}"

        enqueueMouse { writeString(header + body) }
        return true
    }

    // ============================================================
    // MOUSE EVENTS — enqueue vào mouseChannel
    // ============================================================
    fun send_mouse_hscroll(z: Boolean, i: Int) {
        var dir = z
        if (isScrollDirection) dir = !dir
        val step = (cursorScroll + 50) / 100
        val value = if (dir) step else -step
        val cmd = "h $value"
        if (cmd.length <= 9) {
            enqueueMouse { writeString("mos  ${cmd.length}$cmd") }
        }
    }

    fun send_mouse_middlescroll(z: Boolean, i: Int) {
        var dir = z
        var step = i
        if (isScrollDirection) dir = !dir
        if (step > 4) {
            step = if (step <= 8) (step * 2) - 4
            else if (step <= 12) (step * 4) - 20
            else if (step <= 16) (step * 6) - 44
            else (step * 8) - 76
        }
        val value = ((cursorScroll + 50) * step) / 100
        val v = if (dir) value else -value
        val cmd = "w $v"
        if (cmd.length <= 9) {
            enqueueMouse { writeString("mos  ${cmd.length}$cmd") }
        }
    }

    fun send_mouse_right_down() {
        enqueueMouse { writeBytes("mos  5R r d".toByteArray()) }
    }

    fun send_mouse_right_up() {
        enqueueMouse { writeBytes("mos  5R r u".toByteArray()) }
    }

    fun send_mouse_realrightclick() {
        enqueueMouse {
            writeBytes("mos  5R r d".toByteArray())
            writeBytes("mos  5R r u".toByteArray())
        }
    }

    fun send_mouse_leftclick(bool: Boolean): Boolean {
        enqueueMouse {
            if (bool) {
                writeBytes("mos  5R r d".toByteArray())
                writeBytes("mos  5R r u".toByteArray())
            } else {
                writeBytes("mos  5R l d".toByteArray())
                writeBytes("mos  5R l u".toByteArray())
            }
        }
        return true
    }

    fun send_mouse_rightclick(bool: Boolean): Boolean {
        enqueueMouse {
            if (bool) {
                writeBytes("mos  5R l d".toByteArray())
                writeBytes("mos  5R l u".toByteArray())
            } else {
                writeBytes("mos  5R r d".toByteArray())
                writeBytes("mos  5R r u".toByteArray())
            }
        }
        return true
    }

    fun send_middle_button_click() {
        enqueueMouse {
            writeBytes("mos  5R m d".toByteArray())
            writeBytes("mos  5R m u".toByteArray())
        }
    }

    fun send_mouse_click() {
        enqueueMouse { writeBytes("mos  1c".toByteArray()) }
    }

    fun send_mouse_leftdown(): Boolean {
        enqueueMouse { writeBytes("mos  5R l d".toByteArray()) }
        return true
    }

    fun send_mouse_leftup() {
        enqueueMouse { writeBytes("mos  5R l u".toByteArray()) }
    }

    // ============================================================
    // MOUSE DIRECTLY / DRAG — enqueue mouse
    // ============================================================
    fun send_mouse_directly(ratioX: Float, ratioY: Float, flag: Int): Boolean {
        val clampedX = ratioX.coerceIn(0f, 1f)
        val clampedY = ratioY.coerceIn(0f, 1f)
        val dataStr = "a $clampedX $clampedY $flag"
        val finalCommandStr = "mos ${dataStr.length}$dataStr"
        enqueueMouse { writeBytes("$finalCommandStr\n".toByteArray(Charsets.UTF_8)) }
        return true
    }

    fun send_drag_move(dx: Int, dy: Int): Boolean {
        if (dx == 0 && dy == 0) return true
        val absx = abs(dx)
        val absy = abs(dy)
        val max = max(absx, absy)
        val rx: Int
        val ry: Int
        if (max <= 5) {
            rx = dx * 2; ry = dy * 2
        } else if (max <= 10) {
            if (absx > absy) {
                rx = (dx * 3) - 5; ry = (rx * dy) / dx
            } else {
                ry = (dy * 3) - 5; rx = (ry * dx) / dy
            }
        } else {
            if (absx > absy) {
                rx = (dx * 4) - 15; ry = (rx * dy) / dx
            } else {
                ry = (dy * 4) - 15; rx = (ry * dx) / dy
            }
        }
        enqueueMouse { writeString("drag $rx $ry\n") }
        return true
    }

    fun send_drag_start() {
        enqueueMouse { writeString("drag start\n") }
    }

    fun send_drag_end() {
        enqueueMouse { writeString("drag end\n") }
    }

    // ============================================================
    // SLIDER / PPT / WINDOW — enqueue keyboard
    // ============================================================
    fun send_sliderup(z: Boolean) {
        val cmd = if (z) "slideup\n" else "slidedown\n"
        enqueueKeyboard { writeString(cmd) }
    }

    fun send_sliderbegin(z: Boolean) {
        val cmd = if (z) "slide begin\n" else "slide end\n"
        enqueueKeyboard { writeString(cmd) }
    }

    fun send_zoomout() {
        enqueueKeyboard { writeString("zoomout\n") }
    }

    fun send_zoomin() {
        enqueueKeyboard { writeString("zoomin\n") }
    }

    fun send_ppt_start(z: Boolean): Boolean {
        val cmd = if (z) "ppt run\n" else "ppt exit\n"
        enqueueKeyboard { writeString(cmd) }
        return true
    }

    fun send_ppt_page(z: Boolean) {
        val cmd = if (z) "ppt pgup\n" else "ppt pgdn\n"
        enqueueKeyboard { writeString(cmd) }
    }

    fun send_ppt_play(z: Boolean) {
        val cmd = if (z) "ppt play\n" else "ppt stop\n"
        enqueueKeyboard { writeString(cmd) }
    }

    suspend fun send_ppt_output(): Boolean {
        enqueueKeyboard { writeString("ppt switch\n") }
        return true
    }

    suspend fun send_window_close() {
        enqueueKeyboard { writeString("window close\n") }
    }

    fun send_window_max(): Boolean {
        enqueueKeyboard { writeString("window maximize\n") }
        return true
    }

    fun send_window_min(): Boolean {
        enqueueKeyboard { writeString("window minimize\n") }
        return true
    }

    // ============================================================
    // HELPERS
    // ============================================================
    private fun remapModifierForOs(key: String): String {
        return when (key) {
            "ALT" if os == ConstantsUtils.SERVER_TYPE_MAC -> "WIN"
            "WIN" if os == ConstantsUtils.SERVER_TYPE_MAC -> "ALT"
            else -> key
        }
    }

    // ============================================================
    // CLEANUP
    // ============================================================
    fun close() {
        // Không đóng channel — chỉ xóa state
    }

    fun destroy() {
        keyboardChannel.close()
        mouseChannel.close()
        scope.cancel()
        workerJob?.cancel()
        workerJob = null
    }
}