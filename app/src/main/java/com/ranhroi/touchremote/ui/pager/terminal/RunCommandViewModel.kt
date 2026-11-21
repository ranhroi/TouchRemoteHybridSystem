package com.ranhroi.touchremote.ui.pager.terminal

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ranhroi.touchremote.ui.screens.server.HybridConnectionModel
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream


class RunCommandViewModel : ViewModel() {

    private val _uiState = mutableStateOf(RunCommandModel())
    val uiState: RunCommandModel get() = _uiState.value

    private var listenJob: Job? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null

    // Khởi tạo với ConnectionState
    fun init(hybridState: HybridConnectionModel) {
        if (hybridState.isConnected && hybridState.output != null && hybridState.input != null) {
            outputStream = hybridState.output
            inputStream = hybridState.input
            startListening()
            _uiState.value = _uiState.value.copy(isConnected = true)
        } else {
            _uiState.value = _uiState.value.copy(isConnected = false)
            appendOutput("[Error] Not connected to server")
        }
    }

    // ===== GỬI LỆNH QUA OUTPUTSTREAM =====
    private suspend fun sendCommand(command: String) = withContext(Dispatchers.IO) {
        try {
            val bytes = (command + "\n").toByteArray()
            outputStream?.write(bytes)
            outputStream?.flush()
            android.util.Log.d("RunCommand", "Sent: $command")
        } catch (e: Exception) {
            android.util.Log.e("RunCommand", "Send error: ${e.message}")
        }
    }

    // ===== XÓA SẠCH DỮ LIỆU RÁC TRONG SOCKET =====
    private suspend fun flushSocket() = withContext(Dispatchers.IO) {
        try {
            var cleared = 0
            val buffer = ByteArray(1024)
            while (inputStream?.available()?.let { it > 0 } == true) {
                inputStream?.read(buffer)
                cleared++
            }
            if (cleared > 0) {
                android.util.Log.d("RunCommand", "Flushed $cleared chunks of garbage data")
            }
        } catch (e: Exception) { }
    }

    // ===== LỌC DỮ LIỆU - CHỈ NHẬN NHỮNG GÌ THUỘC VỀ RunCommand =====
    private fun isValidRunCommandMessage(message: String): Boolean {
        // ✅ ƯU TIÊN NHẬN TẤT CẢ DỮ LIỆU CÓ PREFIX [CMD]
        if (message.startsWith("[CMD]")) {
            return true
        }
        
        // Giữ lại các trường hợp cũ cho tương thích (nếu server cũ chưa cập nhật)
        return when {
            message.length > 10000 -> false
            message.startsWith("screenmirror", ignoreCase = true) -> false
            message.startsWith("FILE ") -> false
            message.startsWith("file_header ") -> false
            message.startsWith("ScreenMirror", ignoreCase = true) -> false
            message.matches(Regex("\\d+\\s+\\d+\\s+\\d+\\s+\\w.*")) -> false
            message.matches(Regex("\\d+\\s+\\d+")) -> false
            message.matches(Regex("([\\d]+\\s?)+")) && message.contains(" ") -> false
            message.startsWith("task_manager") -> false
            message.startsWith("getramusagetask") -> false
            message.startsWith("getcpuusagetask") -> false
            message.startsWith("gettasks") -> false
            message.startsWith("killpidtask") -> false
            // Dữ liệu của RunCommand (hợp lệ)
            message.startsWith("[OK]") -> true
            message.startsWith("[Error]") -> true
            message.startsWith("[Timeout]") -> true
            message.startsWith("[Done]") -> true
            message.startsWith("suggestions:") -> true
            message.startsWith("cmdrun") -> true
            message.startsWith("Command Output:") -> true
            message.length < 5000 && message.all { it.code >= 32 || it.code == 10 || it.code == 13 } -> true
            else -> false
        }
    }

    // ===== LẮNG NGHE DỮ LIỆU TỪ SERVER =====
    private fun startListening() {
        listenJob?.cancel()
        listenJob = viewModelScope.launch(Dispatchers.IO) {
            flushSocket()

            val buffer = ByteArray(4096)
            val stringBuilder = StringBuilder()

            while (_uiState.value.isConnected && isActive) {
                try {
                    val available = inputStream?.available() ?: 0
                    if (available == 0) {
                        delay(10)
                        continue
                    }

                    val bytesRead = inputStream?.read(buffer) ?: -1
                    if (bytesRead == -1) {
                        withContext(Dispatchers.Main) {
                            appendOutput("[Error] Connection lost")
                            _uiState.value = _uiState.value.copy(isConnected = false)
                        }
                        break
                    }

                    for (i in 0 until bytesRead) {
                        val byte = buffer[i]
                        if (byte == 0.toByte() || byte == '\n'.code.toByte()) {
                            if (stringBuilder.isNotEmpty()) {
                                val message = stringBuilder.toString()
                                if (isValidRunCommandMessage(message)) {
                                    withContext(Dispatchers.Main) {
                                        handleServerMessage(message)
                                    }
                                } else {
                                    android.util.Log.d("RunCommand", "Ignoring: ${message.take(50)}")
                                }
                                stringBuilder.clear()
                            }
                        } else {
                            stringBuilder.append(byte.toChar())
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("RunCommand", "Read error: ${e.message}")
                    delay(100)
                }
            }
        }
    }

    // ===== XỬ LÝ DỮ LIỆU NHẬN TỪ SERVER =====
    private fun handleServerMessage(message: String) {
        // ✅ BỎ PREFIX [CMD] NẾU CÓ
        val actualMessage = if (message.startsWith("[CMD]")) {
            message.removePrefix("[CMD]")
        } else {
            message
        }
        
        when {
            actualMessage.startsWith("suggestions:") -> {
                val suggestionsStr = actualMessage.removePrefix("suggestions:")
                val suggestions = suggestionsStr.split("\n").filter { it.isNotBlank() }
                _uiState.value = _uiState.value.copy(suggestions = suggestions)
                android.util.Log.d("RunCommand", "Updated suggestions: ${suggestions.size} items")
            }
            actualMessage.startsWith("[OK] Started:") -> {
                appendOutput(actualMessage)
            }
            actualMessage.startsWith("[Error]") || actualMessage.startsWith("[Timeout]") -> {
                appendOutput(actualMessage)
            }
            actualMessage.isNotBlank() -> {
                appendOutput(actualMessage)
            }
        }
    }

    // ===== GỬI LỆNH CHẠY LÊN SERVER =====
    fun runCommand(command: String) {
        if (command.isBlank()) return
        if (!_uiState.value.isConnected || outputStream == null) {
            appendOutput("[Error] Not connected to server")
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true)
        android.util.Log.d("RunCommand", "runCommand: $command")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                flushSocket()
                sendCommand("cmdrun $command")
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    appendOutput("[Error] Failed to send command: ${e.message}")
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }
        }
    }

    // ===== GỬI YÊU CẦU GỢI Ý LỆNH =====
    fun requestSuggestions(prefix: String) {
        if (prefix.isBlank() || prefix.length < 2) return
        if (!_uiState.value.isConnected || outputStream == null) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                sendCommand("cmdrun suggestions $prefix")
                android.util.Log.d("RunCommand", "Requested suggestions for: $prefix")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ===== GỬI LỆNH CHẠY NỀN =====
    fun sendDetachedCommand(command: String) {
        if (command.isBlank()) return
        if (!_uiState.value.isConnected || outputStream == null) {
            appendOutput("[Error] Not connected to server")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                sendCommand("cmdrun start $command")
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    appendOutput("[Error] ${e.message}")
                }
            }
        }
    }

    // ===== HIỂN THỊ OUTPUT =====
    private fun appendOutput(text: String) {
        val currentOutput = _uiState.value.commandOutput
        val newOutput = if (currentOutput == "Output:") {
            text
        } else {
            "$currentOutput\n$text"
        }

        val limitedOutput = if (newOutput.length > 50000) {
            newOutput.takeLast(50000)
        } else {
            newOutput
        }

        _uiState.value = _uiState.value.copy(commandOutput = limitedOutput)
        android.util.Log.d("RunCommand", "Output appended, length: ${limitedOutput.length}")
    }

    // ===== XÓA OUTPUT =====
    fun clearOutput() {
        _uiState.value = _uiState.value.copy(commandOutput = "Output:")
    }

    // ===== DỪNG LẮNG NGHE =====
    fun stopListening() {
        listenJob?.cancel()
        listenJob = null
    }

    // ===== RESET TOÀN BỘ =====
    fun reset() {
        stopListening()
        outputStream = null
        inputStream = null
        _uiState.value = RunCommandModel()
    }

    override fun onCleared() {
        super.onCleared()
        stopListening()
    }
}