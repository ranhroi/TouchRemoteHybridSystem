package com.ranhroi.touchremote.ui.pager.task

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ranhroi.touchremote.ui.screens.server.HybridConnectionModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class TaskManagerViewModel : ViewModel() {
    // State
    private val _ramUsages = MutableStateFlow<List<Float>>(emptyList())
    val ramUsages: StateFlow<List<Float>> = _ramUsages.asStateFlow()

    private val _cpuUsages = MutableStateFlow<List<MutableList<Float>>>(emptyList())
    val cpuUsages: StateFlow<List<MutableList<Float>>> = _cpuUsages.asStateFlow()

    private val _totalRamKBs = MutableStateFlow(0)
    val totalRamKBs: StateFlow<Int> = _totalRamKBs.asStateFlow()

    private val _processes = MutableStateFlow<List<ProcessModel>>(emptyList())
    val processes: StateFlow<List<ProcessModel>> = _processes.asStateFlow()

    private val _sortProcessesBy = MutableStateFlow(1)
    val sortProcessesBy: StateFlow<Int> = _sortProcessesBy.asStateFlow()

    private val _lastUpdateTime = MutableStateFlow("")
    val lastUpdateTime: StateFlow<String> = _lastUpdateTime.asStateFlow()

    // Graph history
    private val maxPoints = 100
    private val cpuHistory = mutableListOf<MutableList<Float>>()
    private val ramHistory = mutableListOf<Float>()

    // Auto-refresh
    private var refreshJob: Job? = null
    private var listenJob: Job? = null

    // Socket nguyên bản
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null

    // Flag
    private var isInitialized = false

    init {
        if (cpuHistory.isEmpty()) {
            cpuHistory.add(mutableListOf())
        }
    }

    /**
     * ✅ Xóa dữ liệu rác trong buffer - ĐỌC NGUYÊN BẢN
     */
    private suspend fun flushSocket() = withContext(Dispatchers.IO) {
        try {
            var cleared = 0
            val buffer = ByteArray(1024)
            while (inputStream?.available()?.let { it > 0 } == true) {
                inputStream?.read(buffer)
                cleared++
            }
            if (cleared > 0) {
                Log.d("TaskManager", "Flushed $cleared chunks of garbage data")
            }
        } catch (e: Exception) {
            Log.e("TaskManager", "Flush error: ${e.message}")
        }
    }

    /**
     * ✅ Gửi lệnh trực tiếp qua OutputStream
     */
    private fun sendCommand(command: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = (command + "\n").toByteArray()
                outputStream?.write(bytes)
                outputStream?.flush()
                Log.d("TaskManager", "Sent: $command")
            } catch (e: Exception) {
                Log.e("TaskManager", "Send error: ${e.message}")
            }
        }
    }

    fun requestData() {
        if (!isInitialized) return
        sendCommand("taskmanager get")
    }

    fun killProcess(pid: String) {
        sendCommand("taskmanager kill $pid")
        _processes.value = _processes.value.filter { it.pid != pid }
    }

    /**
     * ✅ Đọc dữ liệu trực tiếp từ InputStream - KHÔNG dùng BufferedReader
     */
    private fun startListening() {
        if (listenJob?.isActive == true) return

        listenJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                flushSocket()

                val buffer = ByteArray(4096)
                val stringBuilder = StringBuilder()

                while (isActive && isInitialized) {
                    // Kiểm tra dữ liệu có sẵn
                    val available = inputStream?.available() ?: 0
                    if (available == 0) {
                        delay(10)
                        continue
                    }

                    // Đọc dữ liệu
                    val bytesRead = inputStream?.read(buffer) ?: -1

                    if (bytesRead == -1) {
                        Log.d("TaskManager", "Socket closed by server")
                        break
                    }

                    // Xử lý từng byte
                    for (i in 0 until bytesRead) {
                        val byte = buffer[i]

                        // Null terminator (0) hoặc new line (\n) là kết thúc message
                        if (byte == 0.toByte() || byte == '\n'.code.toByte()) {
                            if (stringBuilder.isNotEmpty()) {
                                val line = stringBuilder.toString()
                                if (line.startsWith("task_manager")) {
                                    Log.d("TaskManager", "Received: ${line.take(100)}...")
                                    withContext(Dispatchers.Main) {
                                        processTaskManagerData(line)
                                    }
                                }
                                stringBuilder.clear()
                            }
                        } else {
                            stringBuilder.append(byte.toChar())
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("TaskManager", "Read error: ${e.message}")
            } finally {
                Log.d("TaskManager", "Listening stopped")
            }
        }
    }

    /**
     * ✅ Dừng lắng nghe
     */
    fun stopListening() {
        listenJob?.cancel()
        listenJob = null
        Log.d("TaskManager", "Stop listening")
    }

    fun processTaskManagerData(data: String) {
        try {
            val jsonString = data.removePrefix("task_manager ").trim()
            if (jsonString.isEmpty() || jsonString == "{}") return

            val json = JSONObject(jsonString)

            _lastUpdateTime.value = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

            val ramTotalMb = json.optInt("ram_total_mb", 0)
            _totalRamKBs.value = ramTotalMb * 1024

            val ramPercent = json.optInt("ram_percent", 0)
            val ramUsage = ramPercent / 100.0f
            ramHistory.add(ramUsage)
            while (ramHistory.size > maxPoints) ramHistory.removeAt(0)
            _ramUsages.value = ramHistory.toList()

            val cpuTotal = json.optInt("cpu_total", 0)
            val cpuUsage = cpuTotal / 100.0f
            if (cpuHistory.isEmpty()) cpuHistory.add(mutableListOf())
            cpuHistory[0].add(cpuUsage)
            while (cpuHistory[0].size > maxPoints) cpuHistory[0].removeAt(0)
            _cpuUsages.value = cpuHistory.map { it.toMutableList() }

            updateProcesses(json.optJSONArray("processes"))

        } catch (e: Exception) {
            Log.e("TaskManager", "Parse error: ${e.message}")
        }
    }

    private fun updateProcesses(array: JSONArray?) {
        array ?: return

        val newList = mutableListOf<ProcessModel>()
        for (i in 0 until array.length()) {
            try {
                val obj = array.getJSONObject(i)

                val cpuValue = when {
                    obj.has("cpu") -> {
                        when (val cpuRaw = obj.opt("cpu")) {
                            is Double -> cpuRaw
                            is Int -> cpuRaw.toDouble()
                            else -> 0.0
                        }
                    }
                    else -> 0.0
                }

                val ramValue = when {
                    obj.has("ram_mb") -> {
                        when (val ramRaw = obj.opt("ram_mb")) {
                            is Double -> ramRaw
                            is Int -> ramRaw.toDouble()
                            else -> 0.0
                        }
                    }
                    obj.has("ram") -> {
                        when (val ramRaw = obj.opt("ram")) {
                            is Double -> ramRaw
                            is Int -> ramRaw.toDouble()
                            else -> 0.0
                        }
                    }
                    else -> 0.0
                }

                newList.add(
                    ProcessModel(
                        pid = obj.optInt("pid", 0).toString(),
                        cpu = cpuValue,
                        ram = ramValue,
                        name = obj.optString("name", "Unknown")
                    )
                )
            } catch (e: Exception) {
                continue
            }
        }

        val sorted = when (_sortProcessesBy.value) {
            0 -> newList.sortedBy { it.name.lowercase() }
            1 -> newList.sortedByDescending { it.cpu }
            2 -> newList.sortedByDescending { it.ram }
            else -> newList
        }
        _processes.value = sorted
    }

    fun setSortOrder(order: Int) {
        _sortProcessesBy.value = order
        val current = _processes.value.toMutableList()
        val sorted = when (order) {
            0 -> current.sortedBy { it.name.lowercase() }
            1 -> current.sortedByDescending { it.cpu }
            2 -> current.sortedByDescending { it.ram }
            else -> current
        }
        _processes.value = sorted
    }

    fun startAutoRefresh(intervalMs: Long) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (isActive && isInitialized) {
                requestData()
                delay(intervalMs)
            }
        }
    }

    fun stopAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = null
    }

    /**
     * ✅ Khởi tạo - dùng socket nguyên bản
     */
    fun init(hybridState: HybridConnectionModel) {
        // Dọn dẹp cũ
        cleanup()

        // Gán trực tiếp stream
        inputStream = hybridState.input ?: throw Exception("Mất kết nối Input")
        outputStream = hybridState.output ?: throw Exception("Mất kết nối Output")

        isInitialized = true

        viewModelScope.launch {
            flushSocket()
            startListening()
            requestData()
        }

        Log.d("TaskManager", "Initialized with raw socket")
    }

    /**
     * ✅ Dọn dẹp - KHÔNG đóng stream vì socket dùng chung
     */
    fun cleanup() {
        Log.d("TaskManager", "Cleaning up...")

        isInitialized = false

        // Dừng tất cả jobs
        stopAutoRefresh()
        stopListening()

        // Xóa lịch sử
        cpuHistory.clear()
        ramHistory.clear()
        if (cpuHistory.isEmpty()) {
            cpuHistory.add(mutableListOf())
        }

        // Xóa state
        _ramUsages.value = emptyList()
        _cpuUsages.value = emptyList()
        _processes.value = emptyList()
        _totalRamKBs.value = 0
        _lastUpdateTime.value = ""

        // ✅ KHÔNG đóng outputStream/inputStream vì socket đang dùng chung
        // Chỉ gán null để giải phóng tham chiếu
        outputStream = null
        inputStream = null

        Log.d("TaskManager", "Cleanup completed (streams preserved for other fragments)")
    }

    override fun onCleared() {
        super.onCleared()
        cleanup()
    }
}