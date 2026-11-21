package com.ranhroi.touchremote.ui.pager.mirror

import android.content.Context
import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ranhroi.touchremote.models.CombineSettingModel
import com.ranhroi.touchremote.ui.screens.server.HybridConnectionModel
import com.ranhroi.touchremote.send.InputManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject

@HiltViewModel
class ScreenMirrorViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    // ===== RECEIVERS =====
    private val videoDecoderReceiver = VideoDecoderReceiver()
    private val audioReceiver = AudioUdpReceiver()

    // ===== STATE =====
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _audioState = MutableStateFlow(ReceiverState.SLEEP)
    val audioState: StateFlow<ReceiverState> = _audioState.asStateFlow()

    private val _mouseData = MutableStateFlow(MouseDataModel())
    val mouseData: StateFlow<MouseDataModel> = _mouseData.asStateFlow()

    // ===== NGUỒN DỮ LIỆU =====
    private var output: OutputStream? = null
    private var input: InputStream? = null
    private var mouseJob: Job? = null

    companion object {
        const val HEADER_MOUSE = 0x09
        const val PACKET_SIZE = 18
    }

    // ===== HÀM ĐỌC DỮ LIỆU NHỊ PHÂN =====

    private suspend fun readExactly(size: Int): ByteArray? = withContext(Dispatchers.IO) {
        val buffer = ByteArray(size)
        var totalRead = 0
        while (totalRead < size) {
            val read = input?.read(buffer, totalRead, size - totalRead) ?: -1
            if (read == -1) return@withContext null
            totalRead += read
        }
        return@withContext buffer
    }

    private suspend fun readByte(): Int = withContext(Dispatchers.IO) {
        input?.read() ?: -1
    }

    // ==================== INIT CONNECTION ====================
    fun initConnection(state: HybridConnectionModel, conf: CombineSettingModel) {
        if (state.isConnected && state.input != null && state.output != null) {
            closeIoStreams()
            input = state.input
            output = state.output
            _isConnected.value = true
        } else {
            _isConnected.value = false
            stopStream()
        }
    }

    // ==================== START STREAM ====================
    fun startStream(
        videoConfig: VideoConfigModel,
        audioConfig: AudioConfigModel,
        inputManager: InputManager
    ) {
        val videoPort = videoConfig.udpPort
        val audioPort = audioConfig.udpPort
        val videoBitrate = videoConfig.getBitrate()

        val fullJson = """
    {"type":"stream","video":{"udpPort":$videoPort,"width":${videoConfig.width},"height":${videoConfig.height},"fps":${videoConfig.fps},"quality":${videoConfig.quality},"bitrate":$videoBitrate},"audio":{"udpPort":$audioPort,"enabled":${audioConfig.enabled},"sampleRate":${audioConfig.sampleRate},"channels":${audioConfig.channels},"bitDepth":${audioConfig.bitDepth},"codec":"${audioConfig.codec}","rmsThreshold":${audioConfig.rmsThreshold},"forcedPlay":${audioConfig.forcedPlay}}}
    """.trimIndent()

        viewModelScope.launch {
            try {
                inputManager.sendStringMessage("mirror config:$fullJson")
                val sent = inputManager.sendStringMessage("mirror start")

                if (sent) {
                    _isStreaming.value = true
                    videoDecoderReceiver.start(videoConfig)
                    videoDecoderReceiver.setRotation(90)
                    audioReceiver.start(audioConfig)

                    viewModelScope.launch {
                        audioReceiver.state.collect { state ->
                            _audioState.value = state
                        }
                    }

                    startMouseListening()
                } else {
                    _isStreaming.value = false
                }

            } catch (e: Exception) {
                _isStreaming.value = false
                e.printStackTrace()
                stopStream()
            }
        }
    }


    fun setSurface(surface: Surface?) {
        viewModelScope.launch {
            videoDecoderReceiver.setSurface(surface)
        }
    }

    // ==================== STOP STREAM ====================
    fun stopStream() {
        viewModelScope.launch {
            try {
                videoDecoderReceiver.stop()
                audioReceiver.stop()
                _isStreaming.value = false
                mouseJob?.cancel()
                mouseJob = null
                _audioState.value = ReceiverState.SLEEP
                _mouseData.value = MouseDataModel()
            } catch (_: Exception) {
            }
        }
    }

    // ==================== MOUSE LISTENING ====================
    private fun startMouseListening() {
        // Chặn trùng lặp luồng lắng nghe
        if (!_isConnected.value || mouseJob?.isActive == true) return

        mouseJob = viewModelScope.launch(Dispatchers.IO) {
            if (input == null) return@launch

            while (_isConnected.value && isActive) {
                try {
                    // 1. ĐỌC TỪNG BYTE ĐỂ TÌM ĐÚNG HEADER 0x09 CỦA SERVER C++
                    val header = readByte()
                    if (header == -1) break // Ngắt luồng nếu socket phía Server đóng kết nối

                    if (header != HEADER_MOUSE) {
                        // Nếu lệch byte hoặc lẫn gói tin cấu hình khác, gặm nhấm tiếp byte sau
                        continue
                    }

                    // 2. KHI ĐÃ KHỚP HEADER CHUỘT (0x09), ĐỌC ĐÚNG 17 BYTES PAYLOAD CÒN LẠI
                    val payloadSize = PACKET_SIZE - 1 // 18 - 1 = 17 bytes
                    val payload = readExactly(payloadSize)
                    if (payload == null) break

                    // 3. PARSE DỮ LIỆU TỪ 17 BYTES PAYLOAD (Xử lý Big-Endian tương thích htonl/htonf)
                    val buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)

                    val screenW = buffer.int        // Đọc 4 bytes int (screenWidth)
                    val screenH = buffer.int        // Đọc 4 bytes int (screenHeight)
                    val xRatio = buffer.float       // Đọc 4 bytes float (floatX)
                    val yRatio = buffer.float       // Đọc 4 bytes float (floatY)
                    val shape = buffer.get().toInt() and 0xFF // Đọc 1 byte cuối (currentShape)

                    // 4. KIỂM TRA ĐIỀU KIỆN LỌC AN TOÀN TRƯỚC KHI ĐẨY RA UI JETPACK COMPOSE
                    // Tránh trường hợp dữ liệu rác ban đầu làm chuột nhảy về góc (0,0) trái tự nhiên
                    if (screenW > 0 && screenH > 0) {
                        withContext(Dispatchers.Main) {
                            _mouseData.value = MouseDataModel(
                                screenWidth = screenW,
                                screenHeight = screenH,
                                xRatio = xRatio.coerceIn(0f, 1f),
                                yRatio = yRatio.coerceIn(0f, 1f),
                                shape = shape
                            )
                        }
                    }

                } catch (e: java.io.EOFException) {
                    break // Hết luồng truyền dữ liệu
                } catch (e: Exception) {
                    e.printStackTrace()
                    break
                }
            }
        }
    }


    private fun closeIoStreams() {
        try {
            input?.close()
            _isConnected.value = false
        } catch (_: Exception) {
        }
        try {
            output?.close()
        } catch (_: Exception) {
        }
        input = null
        output = null
    }

    override fun onCleared() {
        super.onCleared()
        stopStream()
        closeIoStreams()
    }
}