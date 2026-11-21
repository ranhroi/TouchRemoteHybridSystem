package com.ranhroi.touchremote.ui.pager.mirror

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import android.view.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.nio.ByteBuffer
import kotlin.concurrent.thread

class VideoDecoderReceiver {
    // ===== UDP =====
    private var socket: DatagramSocket? = null
    private var isRunning = false
    private val assembler = FrameAssembler()

    // ===== DECODER =====
    private var codec: MediaCodec? = null
    private var surface: Surface? = null
    private var outputThread: Thread? = null
    private var decoderStarted = false

    // ===== THÔNG SỐ =====
    private var width = 0
    private var height = 0
    private var fps = 0
    private var udpPort = 0
    private var rotationAngle = 90

    // ===== KHÓA ĐỒNG BỘ =====
    private val lock = Any()

    // ===== FRAME COUNTER =====
    private var frameCount = 0

    // ===== SET SURFACE =====
    suspend fun setSurface(surface: Surface?) {
        this.surface = surface
        if (isRunning && codec != null && surface != null) {
            stop()
            start(
                VideoConfigModel(
                    udpPort = udpPort,
                    width = width,
                    height = height,
                    fps = fps,
                    surface = surface
                )
            )
        }
    }

    // ===== SET ROTATION =====
    suspend fun setRotation(angle: Int) {
        if (rotationAngle == angle) return
        rotationAngle = angle

        synchronized(lock) {
            if (isRunning && decoderStarted && codec != null) {
                stopInternal()
                startInternal(
                    VideoConfigModel(
                        udpPort = udpPort,
                        width = width,
                        height = height,
                        fps = fps,
                        surface = surface
                    )
                )
            }
        }
    }

    // ===== START =====
    suspend fun start(videoConfig: VideoConfigModel) {
        synchronized(lock) {
            // 🔥 RESET TRƯỚC KHI START
            resetState()

            if (isRunning) {
                stopInternal()
            }
            startInternal(videoConfig)
        }
    }

    // ===== RESET STATE =====
    private fun resetState() {
        frameCount = 0
        decoderStarted = false
        assembler.clear()
    }

    private fun startInternal(videoConfig: VideoConfigModel) {
        this.udpPort = videoConfig.udpPort
        this.width = videoConfig.width
        this.height = videoConfig.height
        this.fps = videoConfig.fps
        this.surface = videoConfig.surface
        this.decoderStarted = false
        this.isRunning = true
        this.frameCount = 0

        // 🔥 TẠO SOCKET MỚI
        try {
            socket = DatagramSocket(videoConfig.udpPort).apply {
                receiveBufferSize = 4 * 1024 * 1024
                reuseAddress = true
                // 🔥 XÓA BỘ ĐỆM SOCKET CŨ
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    // Không có cách xóa buffer trực tiếp, nhưng socket mới sẽ không có dữ liệu cũ
                }
            }
        } catch (e: Exception) {
            isRunning = false
            return
        }

        // 🔥 CHẠY LUỒNG NHẬN UDP
        Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            receiveLoop()
        }.start()
    }

    private fun receiveLoop() {
        val buffer = ByteArray(64 * 1024)
        val packet = DatagramPacket(buffer, buffer.size)

        while (isRunning) {
            try {
                packet.length = buffer.size
                socket?.receive(packet)

                val totalLength = packet.length
                if (totalLength < 16) continue

                val header = UdpHeaderModel.fromBuffer(packet.data, packet.offset)
                val payloadSize = totalLength - 16
                if (payloadSize <= 0) continue

                val payload = ByteArray(payloadSize)
                System.arraycopy(packet.data, packet.offset + 16, payload, 0, payloadSize)

                val completeFrame = assembler.addPacket(header, payload)

                if (completeFrame != null) {
                    frameCount++

                    if (!decoderStarted) {
                        val spsPps = extractSpsPps(completeFrame)
                        val sps = spsPps.first
                        val pps = spsPps.second

                        if (sps != null && pps != null) {
                            startDecoder(width, height, sps, pps, rotationAngle)
                            decoderStarted = true
                        } else {
                            continue
                        }
                    }

                    queueSample(completeFrame, header.timestamp.toLong() * 1000)
                }
            } catch (e: Exception) {
                if (isRunning) {
                }
                if (socket?.isClosed == true) break
            }
        }
    }

    // ===== KHỞI ĐỘNG DECODER =====
    private fun startDecoder(
        width: Int,
        height: Int,
        sps: ByteArray?,
        pps: ByteArray?,
        rotation: Int
    ) {
        try {
            // 🔥 GIẢI PHÓNG CODEC CŨ NẾU CÓ
            try {
                codec?.stop()
                codec?.release()
            } catch (_: Exception) {
            }
            codec = null

            codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            val format =
                MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)

            if (sps != null && sps.size > 4) {
                format.setByteBuffer("csd-0", ByteBuffer.wrap(sps))
            }
            if (pps != null && pps.size > 4) {
                format.setByteBuffer("csd-1", ByteBuffer.wrap(pps))
            }

            if (rotation != 0) {
                format.setInteger("rotation-degrees", rotation)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
            }

            codec?.configure(format, surface, null, 0)
            codec?.start()

            // Output thread
            outputThread = thread(name = "decoder-output") {
                val bufferInfo = MediaCodec.BufferInfo()
                while (isRunning) {
                    try {
                        val outIndex = codec?.dequeueOutputBuffer(bufferInfo, 10_000L) ?: -1
                        if (outIndex >= 0) {
                            codec?.releaseOutputBuffer(outIndex, true)
                        } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        }
                    } catch (_: Exception) {
                    }
                }
            }

        } catch (e: Exception) {
            if (sps != null || pps != null) {
                codec?.release()
                codec = null
                startDecoder(width, height, null, null, rotation)
            }
        }
    }

    // ===== QUEUE SAMPLE =====
    private fun queueSample(nal: ByteArray, pts: Long) {
        if (!isRunning) return
        try {
            val inputIndex = codec?.dequeueInputBuffer(10_000) ?: -1
            if (inputIndex >= 0) {
                val input = codec?.getInputBuffer(inputIndex)
                input?.clear()
                input?.put(nal)
                codec?.queueInputBuffer(inputIndex, 0, nal.size, pts, 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ===== TRÍCH XUẤT SPS/PPS =====
    private fun extractSpsPps(data: ByteArray): Pair<ByteArray?, ByteArray?> {
        var sps: ByteArray? = null
        var pps: ByteArray? = null
        var i = 0
        while (i < data.size - 4) {
            val startCodeLen = when {
                data[i] == 0.toByte() && data[i + 1] == 0.toByte() && data[i + 2] == 0.toByte() && data[i + 3] == 1.toByte() -> 4
                data[i] == 0.toByte() && data[i + 1] == 0.toByte() && data[i + 2] == 1.toByte() -> 3
                else -> {
                    i++; continue
                }
            }
            val nalStart = i + startCodeLen
            if (nalStart >= data.size) break
            val nalType = data[nalStart].toInt() and 0x1F
            var nextStart = data.size
            var j = nalStart + 1
            while (j < data.size - 3) {
                if ((data[j] == 0.toByte() && data[j + 1] == 0.toByte() && data[j + 2] == 0.toByte() && data[j + 3] == 1.toByte()) ||
                    (data[j] == 0.toByte() && data[j + 1] == 0.toByte() && data[j + 2] == 1.toByte())
                ) {
                    nextStart = j
                    break
                }
                j++
            }
            val nalData = data.sliceArray(nalStart until nextStart)
            when (nalType) {
                7 -> sps = nalData
                8 -> pps = nalData
            }
            i = nextStart
        }
        return Pair(sps, pps)
    }

    // ===== STOP =====
    private fun stopInternal() {
        isRunning = false
        decoderStarted = false

        // 1. Dừng output thread
        outputThread?.interrupt()
        try {
            outputThread?.join(500)
        } catch (_: Exception) {
        }
        outputThread = null

        // 2. Giải phóng codec
        try {
            codec?.stop()
            codec?.release()
        } catch (_: Exception) {
        }
        codec = null

        // 3. Đóng socket
        try {
            socket?.close()
            socket = null
        } catch (_: Exception) {
        }

        // 4. 🔥 CLEAR ASSEMBLER
        assembler.clear()

    }

    fun stop() {
        synchronized(lock) {
            stopInternal()
            resetState()
        }
    }

    fun getRunning() = isRunning
    fun getRotation() = rotationAngle
}