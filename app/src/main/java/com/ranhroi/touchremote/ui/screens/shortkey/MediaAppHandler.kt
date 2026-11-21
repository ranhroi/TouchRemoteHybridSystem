package com.ranhroi.touchremote.ui.screens.shortkey

import com.ranhroi.touchremote.ui.screens.server.HybridConnectionModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import kotlin.time.Duration.Companion.milliseconds

class MediaAppHandler(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private var input: InputStream? = null
    private var output: OutputStream? = null

    // trạng thái đọc
    @Volatile
    private var running = false

    interface Callback {
        fun onAppId(appId: String)
        fun onError(e: Exception)
    }

    private var callback: Callback? = null

    fun register(callback: Callback?) {
        this.callback = callback
    }

    fun connect(hybridState: HybridConnectionModel) {
        input = hybridState.input
        output = hybridState.output
        scope.launch { readLoop() }     // Bắt đầu listener
    }

    fun start() {
        running = true
        scope.launch(Dispatchers.IO) { send("reportCurrentApp\n") }
    }

    fun stop() {
        running = false
        scope.launch(Dispatchers.IO) { send("dontreportCurrentApp\n") }
    }

    private suspend fun readLoop() = withContext(Dispatchers.IO) {
        try {
            val inp = input ?: return@withContext
            val buffer = ByteArray(1024)

            while (currentCoroutineContext().isActive) {
                if (!running) {
                    delay(120.milliseconds)
                    continue
                }

                if (inp.available() > 0) {
                    val bytes = inp.read(buffer)
                    if (bytes > 0) {
                        val text = String(buffer, 0, bytes)
                        parseAppId(text)?.let { callback?.onAppId(it) }
                    }
                }

                delay(120.milliseconds)
            }
        } catch (e: Exception) {
            callback?.onError(e)
        }
    }

    private fun parseAppId(data: String): String? {
        val idx = data.indexOf(" ")
        if (idx == -1) return null
        return data.substring(idx + 1).trim()
    }

    private suspend fun send(msg: String) {
        try {
            output?.apply {
                write(msg.toByteArray())
                flush()
            }
        } catch (e: Exception) {
            callback?.onError(e)
        }
    }
}