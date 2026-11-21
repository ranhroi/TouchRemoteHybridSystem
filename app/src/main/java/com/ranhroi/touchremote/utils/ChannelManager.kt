package com.ranhroi.touchremote.utils

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * ChannelManager là một singleton object dùng để quản lý việc gửi và nhận các sự kiện kết nối (ConnectionEvent)
 * giữa các thành phần như Service, ViewModel, hoặc UI trong ứng dụng.
 *
 * - Sử dụng Channel với BUFFERED để không bị chặn khi gửi nhiều sự kiện liên tục.
 * - Các sự kiện được phát ra dưới dạng Flow (`receiveAsFlow`) để có thể collect một cách reactive từ UI hoặc ViewModel.
 *
 * ✅ Ưu điểm:
 * - Cho phép gửi dữ liệu không đồng bộ mà không cần giữ tham chiếu giữa các lớp.
 * - Có thể sử dụng từ nhiều nơi mà không cần context hoặc lifecycle owner.
 *
 * 💡 Ví dụ sử dụng:
 * ChannelManager.send(ConnectionEvent.Connected(true))
 *
 * ChannelManager.channel.collect { event ->
 *     // xử lý sự kiện
 * }
 */
object ChannelManager {
    private val _channel = Channel<String>(Channel.BUFFERED)
    val channel = _channel.receiveAsFlow()

    suspend fun send(event: String) {
        _channel.send(event)
    }

}


