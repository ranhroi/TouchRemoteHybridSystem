package com.ranhroi.touchremote.scanner

import android.content.Context
import com.ranhroi.touchremote.ui.screens.server.AddressPortsModel
import com.ranhroi.touchremote.models.DeviceServerModel
import com.ranhroi.touchremote.network.GetIPAddress
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

class TcpScanner(
    private val context: Context,
    private val addressPortsModel: AddressPortsModel
) {
    private val timeout = 500

    /**
     * Quét mạng và trả về Flow các thiết bị tìm thấy.
     * Tự động dừng khi CoroutineScope bên ngoài bị hủy.
     */
    fun scan(): Flow<DeviceServerModel> = flow {
        val localIp = GetIPAddress.getLocalIpAddress(context) ?: return@flow
        val subnet = GetIPAddress.getSubnet(localIp)

        // Chia 254 IP thành các nhóm nhỏ (chunks) để quét song song hiệu quả
        val ipRange = (1..254).toList()

        // Quét theo từng nhóm để tránh làm quá tải hệ thống socket
        ipRange.chunked(30).forEach { chunk ->
            coroutineScope {
                chunk.map { i ->
                    async(Dispatchers.IO) {
                        val hostAddress = "$subnet$i"
                        checkHost(hostAddress)
                    }
                }.awaitAll()
                    .filterNotNull()
                    .forEach { device -> emit(device) }
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun checkHost(hostAddress: String): DeviceServerModel? {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(hostAddress, addressPortsModel.portConnection), timeout)
                // Nếu kết nối thành công, lấy hostname
                val hostname = InetAddress.getByName(hostAddress).canonicalHostName
                DeviceServerModel(
                    name = hostname.ifEmpty { "Unknown Device" },
                    address = hostAddress,
                    port = addressPortsModel.portConnection,
                    password = "",
                    bluetooth = false
                )
            }
        } catch (e: Exception) {
            null
        }
    }
}
