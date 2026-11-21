package com.ranhroi.touchremote.scanner

import android.util.Log
import com.ranhroi.touchremote.ui.screens.server.AddressPortsModel
import com.ranhroi.touchremote.models.DeviceServerModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets

class UdpScanner(private val addressPortsModel: AddressPortsModel) {

    private val multicastGroupIp = "224.3.2.1"
    private val timeout = 3000

    /**
     * Thực hiện quét UDP Multicast và trả về Flow các thiết bị tìm thấy.
     */
    fun scan(): Flow<DeviceServerModel> = flow {
        var ssdpMultiSock: MulticastSocket? = null
        var multiInetAddress: InetAddress? = null

        try {
            ssdpMultiSock = MulticastSocket(addressPortsModel.portScan).apply {
                reuseAddress = true
                broadcast = true
                soTimeout = timeout
                // Lưu ý: joinGroup kiểu cũ bị deprecated trên Android hiện đại,
                // nhưng vẫn hoạt động ổn định cho multicast cơ bản.
                multiInetAddress = InetAddress.getByName(multicastGroupIp)
                joinGroup(multiInetAddress)
            }

            Log.i("UdpScanner", "Đã tham gia nhóm multicast: $multicastGroupIp")

            val buffer = ByteArray(256)
            val datagramPacket = DatagramPacket(buffer, buffer.size)

            // Vòng lặp nhận gói tin (Tự động dừng khi Coroutine bị cancel)
            while (currentCoroutineContext().isActive) {
                try {
                    ssdpMultiSock.receive(datagramPacket)

                    val hostAddress = datagramPacket.address?.hostAddress
                    val hostname = parseComputerName(datagramPacket)

                    if (!hostname.isNullOrEmpty() && hostAddress != null) {
                        emit(
                            DeviceServerModel(
                                address = hostAddress,
                                name = hostname,
                                port = addressPortsModel.portConnection,
                                password = "",
                                bluetooth = false
                            )
                        )
                    }
                } catch (e: SocketTimeoutException) {
                    // Timeout là bình thường khi không có ai broadcast
                    Log.d("UdpScanner", "Socket timeout, tiếp tục chờ...")
                } catch (e: SocketException) {
                    // Thường xảy ra khi socket bị đóng từ bên ngoài
                    break
                }
            }

        } catch (e: Exception) {
            Log.e("UdpScanner", "Lỗi Multicast: ${e.message}")
        } finally {
            // Giải phóng tài nguyên
            try {
                ssdpMultiSock?.leaveGroup(multiInetAddress)
                ssdpMultiSock?.close()
            } catch (e: Exception) {
                Log.e("UdpScanner", "Lỗi khi dọn dẹp socket: ${e.message}")
            }
            Log.i("UdpScanner", "Đã dừng quét UDP.")
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Giải mã tên máy tính từ gói tin với chữ ký "BC"
     */
    private fun parseComputerName(packet: DatagramPacket): String? {
        val data = packet.data
        val length = packet.length

        // Kiểm tra chữ ký "BC" ở đầu gói tin
        if (length >= 5 && data[0] == 'B'.toByte() && data[1] == 'C'.toByte()) {
            try {
                // Giải mã độ dài tên máy tính từ byte 2, 3, 4
                var nameLen = 0
                if (data[2] != ' '.toByte()) nameLen += (data[2].toInt() and 0xCF) * 100
                if (data[3] != ' '.toByte()) nameLen += (data[3].toInt() and 0xCF) * 10
                if (data[4] != ' '.toByte()) nameLen += (data[4].toInt() and 0xCF)

                if (nameLen > 0 && nameLen <= length - 5) {
                    return String(data, 5, nameLen, StandardCharsets.UTF_8)
                }
            } catch (e: Exception) {
                Log.e("UdpScanner", "Lỗi giải mã tên: ${e.message}")
            }
        }
        return null
    }
}
