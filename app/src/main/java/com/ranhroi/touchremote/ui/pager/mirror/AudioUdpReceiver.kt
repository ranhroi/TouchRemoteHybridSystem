package com.ranhroi.touchremote.ui.pager.mirror

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketTimeoutException

class AudioUdpReceiver {
    private var socket: DatagramSocket? = null
    private var audioTrack: AudioTrack? = null
    private var isRunning = false
    private var currentSampleRate = 48000

    private val _state = MutableStateFlow(ReceiverState.SLEEP)
    val state: StateFlow<ReceiverState> = _state.asStateFlow()

    private var rmsThreshold = 30.0f
    private var isForcedPlay = false
    private var isOnAudio = false
    private var silenceCount = 0

    @SuppressLint("DefaultLocale")
    suspend fun start(
        audioConfig: AudioConfigModel
    ) {
        if (isRunning) return
        this.isRunning = true
        this.currentSampleRate = audioConfig.sampleRate
        this.rmsThreshold = audioConfig.rmsThreshold
        this.isForcedPlay = audioConfig.forcedPlay
        this.isOnAudio = audioConfig.enabled

        this.silenceCount = 0
        _state.value = ReceiverState.SLEEP

        withContext(Dispatchers.IO) {
            try {
                socket = DatagramSocket(audioConfig.udpPort).apply {
                    receiveBufferSize = 256 * 1024
                    soTimeout = 2000
                }

                reinitAudioTrack(currentSampleRate)

                val receiveBuffer = ByteArray(4096)
                val packet = DatagramPacket(receiveBuffer, receiveBuffer.size)

                var packetCount = 0

                while (isRunning && isOnAudio) {
                    try {
                        packet.length = receiveBuffer.size
                        socket?.receive(packet)

                        val len = packet.length

                        // KIỂM TRA ĐIỀU KIỆN AN TOÀN: PCM 16-bit Stereo bắt buộc kích thước byte phải chia hết cho 4
                        if (len > 0 && len % 4 == 0) {

                            // 1. Trích xuất mảng byte TINH KHIẾT từ vị trí offset thực tế của gói UDP nhận được
                            val cleanAudioData = ByteArray(len)
                            System.arraycopy(packet.data, packet.offset, cleanAudioData, 0, len)

                            // 2. Tính toán giá trị RMS chuẩn xác từ mảng dữ liệu tinh khiết này
                            val rawRms = calculateRMS(cleanAudioData, len)
                            val isSilence = if (isForcedPlay) false else rawRms < rmsThreshold

                            // 3. Bộ máy điều khiển trạng thái Âm thanh (StateMachine)
                            if (isSilence) {
                                silenceCount++
                                if (silenceCount >= 45 && _state.value == ReceiverState.PLAYING) {
                                    _state.value = ReceiverState.IDLE
                                    try {
                                        audioTrack?.pause()
                                        audioTrack?.flush() // Xóa sạch hàng đợi cũ để tránh tiếng bụp khi phát lại
                                    } catch (_: Exception) {
                                    }
                                }
                            } else {
                                silenceCount = 0
                                if (_state.value != ReceiverState.PLAYING) {
                                    _state.value = ReceiverState.PLAYING
                                    try {
                                        audioTrack?.play()
                                    } catch (_: Exception) {
                                    }
                                }
                            }

                            // 4. CHỈ GHI DUY NHẤT 1 LẦN KHI ĐANG Ở TRẠNG THÁI PLAYING
                            if (_state.value == ReceiverState.PLAYING) {
                                if (audioTrack != null && audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {

                                    // Ghi dữ liệu sạch trực tiếp (Chế độ chặn mặc định để loa đồng bộ nhịp nhàng)
                                    val written = audioTrack?.write(cleanAudioData, 0, len)

                                    if (written != len) {
                                    }
                                }
                            }

                            packetCount++
                        }
                    } catch (e: SocketTimeoutException) {
                        if (_state.value != ReceiverState.SLEEP) {
                            _state.value = ReceiverState.SLEEP
                            try {
                                audioTrack?.stop()
                                audioTrack?.flush()
                            } catch (_: Exception) {
                            }
                            silenceCount = 0
                        }
                    }
                }
            } catch (e: Exception) {
            } finally {
                stop()
            }
        }
    }

    /**
     * Hàm tính toán RMS chuẩn xác cho luồng PCM 16-bit Little Endian (Được truyền từ C++)
     */
    private fun calculateRMS(buffer: ByteArray, len: Int): Double {
        if (len <= 0) return 0.0
        val numSamples = len / 2
        var sum = 0.0
        for (i in 0 until numSamples) {
            val idx = 2 * i
            if (idx + 1 < len) {
                // Khử bit dấu của Java Byte bằng phép 'and 0xFF' chuẩn xác cho cả 2 byte
                val low = buffer[idx].toInt() and 0xFF
                val high = buffer[idx + 1].toInt() and 0xFF

                // Tái cấu trúc lại thành số nguyên 16-bit có dấu (Signed Short)
                val shortSample = ((high shl 8) or low).toShort()
                sum += shortSample.toDouble() * shortSample.toDouble()
            }
        }
        return Math.sqrt(sum / numSamples)
    }

    private fun reinitAudioTrack(sampleRate: Int) {
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {
        }

        val channelConfig = AudioFormat.CHANNEL_OUT_STEREO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBuffer = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        // Tăng gấp 4 lần để chống trồi sụt gói tin qua sóng mạng Wifi
        val bufferSize = minBuffer * 4

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()
    }

    fun stop() {
        isRunning = false
        socket?.close()
        socket = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {
        }
        audioTrack = null
        _state.value = ReceiverState.SLEEP
    }
}
