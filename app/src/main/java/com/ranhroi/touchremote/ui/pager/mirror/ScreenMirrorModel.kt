package com.ranhroi.touchremote.ui.pager.mirror

import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder

// ===== MOUSE DATA =====
data class MouseDataModel(
    val screenWidth: Int = 0,
    val screenHeight: Int = 0,
    val xRatio: Float = 0f,
    val yRatio: Float = 0f,
    val shape: Int = 0
)

// ===== UDP HEADER =====
data class UdpHeaderModel(
    val frameId: Int,
    val packetIdx: Int,
    val packetCount: Int,
    val timestamp: Int
) {
    companion object {
        fun fromBuffer(buffer: ByteArray, offset: Int): UdpHeaderModel {
            val byteBuffer = ByteBuffer.wrap(buffer, offset, 16).order(ByteOrder.LITTLE_ENDIAN)
            return UdpHeaderModel(byteBuffer.int, byteBuffer.int, byteBuffer.int, byteBuffer.int)
        }
    }
}

// ===== RECEIVER STATE =====
enum class ReceiverState {
    PLAYING,
    IDLE,
    SLEEP
}

data class VideoConfigModel(
    val name: String = "video",
    val udpPort: Int = 5050,
    val surface: Surface? = null,
    val width: Int = 1600,
    val height: Int = 900,
    val fps: Int = 30,
    val quality: Int = 65
) {

    fun getBitrate(): Int {
        val pixels = width * height
        val baseBitrate = when {
            pixels >= 3840 * 2160 -> 20000000
            pixels >= 2560 * 1440 -> 10000000
            pixels >= 1920 * 1080 -> 5000000
            pixels >= 1600 * 900 -> 3000000
            pixels >= 1280 * 720 -> 2000000
            else -> 800000
        }

        val qualityMultiplier = when {
            quality >= 85 -> 1.8f
            quality >= 65 -> 1.2f
            quality >= 45 -> 0.8f
            else -> 0.5f
        }

        val fpsMultiplier = when {
            fps >= 60 -> 1.5f
            fps >= 30 -> 1.0f
            fps >= 20 -> 0.8f
            else -> 0.6f
        }

        var bitrate = (baseBitrate * qualityMultiplier * fpsMultiplier).toInt()
        bitrate = bitrate.coerceIn(200000, 25000000)
        return ((bitrate + 49999) / 50000) * 50000
    }
}


// ===== Video CONFIG OPTIONS =====
data class VideoPresetModel(
    val name: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val quality: Int
)

val VIDEO_PRESETS = listOf(
    VideoPresetModel("4K Ultra", 3840, 2160, 30, 100),
    VideoPresetModel("2K High", 2560, 1440, 30, 85),
    VideoPresetModel("1080p High", 1920, 1080, 30, 85),
    VideoPresetModel("1080p Medium", 1920, 1080, 30, 65),
    VideoPresetModel("1600x900 High", 1600, 900, 30, 85),
    VideoPresetModel("1600x900 Medium", 1600, 900, 30, 65),
    VideoPresetModel("720p High", 1280, 720, 30, 85),
    VideoPresetModel("720p Medium", 1280, 720, 30, 65),
    VideoPresetModel("720p Low", 1280, 720, 15, 45),
    VideoPresetModel("480p Low", 854, 480, 15, 25)
)


// ===== AudioConfig =====

data class AudioConfigModel(
    val name: String = "audio",
    val udpPort: Int = 5051,
    val enabled: Boolean = true,
    val sampleRate: Int = 48000,
    val channels: Int = 2,
    val bitDepth: Int = 16,
    val codec: String = "pcm",
    val rmsThreshold: Float = 30.0f,
    val forcedPlay: Boolean = false
)

// ===== AUDIO PRESETS =====
val AUDIO_PRESETS = listOf(
    AudioPresetModel("CD Quality", 44100, 2, 16, "pcm"),
    AudioPresetModel("Studio Quality", 48000, 2, 24, "pcm"),
    AudioPresetModel("High Quality", 48000, 2, 16, "pcm"),
    AudioPresetModel("Voice Optimized", 16000, 1, 16, "pcm"),
    AudioPresetModel("Low Bitrate", 8000, 1, 8, "pcm")
)

data class AudioPresetModel(
    val name: String,
    val sampleRate: Int,
    val channels: Int,
    val bitDepth: Int,
    val codec: String = "pcm"
)