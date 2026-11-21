package com.ranhroi.touchremote.ui.screens.server

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import com.ranhroi.touchremote.models.DeviceServerModel
import com.ranhroi.touchremote.utils.BluetoothPermissionHandler
import com.ranhroi.touchremote.utils.toBluetoothDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.milliseconds

@SuppressLint("MissingPermission")
class ConnectionManager(
    private val context: Context,
) {
    companion object {
        const val ROLE_COMMAND: Byte = 0x01
        const val ROLE_TRANSFER: Byte = 0x02
        const val ROLE_STREAM: Byte = 0x03
    }


    private var scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    private val connections = ConcurrentHashMap<Byte, HybridConnectionModel>()

    private val listeners = CopyOnWriteArrayList<Listener>()

    val isConnectedAll = MutableStateFlow(false)

    interface Listener {
        fun onConnectResult(role: Byte, state: HybridConnectionModel)
        fun onPay(role: Byte, isPay: Boolean)
        fun onNeedPassword(role: Byte, isNeedPassword: Boolean)
        fun onVerifyResult(role: Byte, success: Boolean)
    }

    fun setScope(scope: CoroutineScope) {
        this.scope = scope
    }

    fun registerListener(listener: Listener) {
        listeners.add(listener)
    }

    fun unregisterListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun unregisterAllListeners() {
        listeners.clear()
    }

    // ==================== LẤY KẾT NỐI THEO ROLE ====================
    fun getConnection(role: Byte): HybridConnectionModel? = connections[role]

    fun getConnectionState(role: Byte): HybridConnectionModel =
        connections[role] ?: HybridConnectionModel()

    // ==================== KẾT NỐI ====================
    fun connect(server: DeviceServerModel, role: Byte = ROLE_COMMAND) {
        scope.launch {
            // 1. Đóng kết nối cũ của role này (nếu có)
            close(role)

            // 2. Kết nối vật lý
            val success = configConnect(server, role)
            if (!success) {
                listeners.forEach { it.onConnectResult(role, HybridConnectionModel()) }
                return@launch
            }

            val state = connections[role] ?: return@launch

            // 3. Đọc thông tin hệ thống (Handshake)
            readSystemData(state)

            // 4. Nếu có mật khẩu, tự động verify
            if (state.isNeedPassword && server.password.isNotEmpty()) {
                val verifyOk = doVerify(state, server.password)
                listeners.forEach { it.onVerifyResult(role, verifyOk) }
            }

            listeners.forEach {
                it.onConnectResult(role, state)
                it.onNeedPassword(role, state.isNeedPassword)
                it.onPay(role, state.isPay)
            }
        }
    }

    private suspend fun configConnect(server: DeviceServerModel, role: Byte): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val (socket, input, output, isConnected, isBluetooth) = when (server.bluetooth) {
                    false -> connectTcp(server, role)
                    true -> connectBluetooth(server, role)
                } ?: return@withContext false

                connections[role] = HybridConnectionModel(
                    role = role,
                    isConnected = isConnected,
                    socket = socket,
                    input = input,
                    output = output,
                    isBluetooth = isBluetooth,
                    server = server
                )
                isConnectedAll.value = true
                true
            } catch (e: Exception) {
                Log.e("ConnectionManager", "configConnect error: ${e.message}")
                false
            }
        }

    private fun connectTcp(
        server: DeviceServerModel,
        role: Byte
    ): Quintuple<Socket, InputStream, OutputStream, Boolean, Boolean>? {
        return try {
            if (BluetoothPermissionHandler.checkSelfPermission(context)) adapter?.cancelDiscovery()
            val socket = Socket()
            socket.connect(InetSocketAddress(server.address, server.port), 3000)
            socket.keepAlive = true
            socket.tcpNoDelay = true

            val out = socket.getOutputStream()
            val ins = socket.getInputStream()

            // GỬI BYTE ĐỊNH DANH
            out.write(role.toInt())
            out.flush()

            Quintuple(socket, ins, out, true, false)
        } catch (e: Exception) {
            Log.e("ConnectionManager", "connectTcp error: ${e.message}")
            null
        }
    }

    private fun connectBluetooth(
        server: DeviceServerModel,
        role: Byte
    ): Quintuple<BluetoothSocket, InputStream, OutputStream, Boolean, Boolean>? {
        var socket: BluetoothSocket? = null
        return try {
            val device: BluetoothDevice? = server.toBluetoothDevice()
            if (device == null) {
                Log.e("ConnectionManager", "Cannot get Bluetooth device")
                return null
            }


            val uuid = UUID.fromString(server.uuid)
            socket = device.createRfcommSocketToServiceRecord(uuid)

            if (BluetoothPermissionHandler.checkSelfPermission(context)) {
                adapter?.cancelDiscovery()
            }

            try {
                socket?.connect()
            } catch (e: IOException) {
                socket?.close()
                return null
            }

            val out = socket?.outputStream
            val ins = socket?.inputStream

            out?.write(role.toInt())
            out?.flush()

            if (socket != null && ins != null && out != null) {
                Quintuple(socket, ins, out, true, true)
            } else {
                socket?.close()
                null
            }
        } catch (e: Exception) {
            socket?.close()
            null
        }
    }

    private suspend fun readSystemData(state: HybridConnectionModel) =
        withContext(Dispatchers.IO) {
            try {
                val input = state.input ?: return@withContext

                var emptyCount = 0
                while (true) {
                    if (input.available() <= 0) {
                        delay(150.milliseconds)
                        emptyCount++
                        if (emptyCount > 15) return@withContext
                    } else {
                        val available = input.available()
                        val buffer = ByteArray(available)
                        if (input.read(buffer, 0, available) > 0) {
                            val str = String(buffer)
                            val lines = str.split("\n")
                            if (lines.isEmpty()) continue
                            if (!lines[0].startsWith("system")) return@withContext
                            state.os = lines[0].split(" ").getOrNull(1).toString()
                            for (line in lines) {
                                if (line.contains("needpassword")) {
                                    state.isNeedPassword = true
                                    break
                                }
                            }
                            for (line in lines) {
                                if (line.contains("luminateOK")||line.contains("needPay")) {
                                    state.isPay = true
                                    break
                                }
                            }
                            // Đọc thêm nếu còn dữ liệu
                            if (input.available() <= 0 || input.read(buffer, 0, available) <= 0) {
                                return@withContext
                            }
                        } else {
                            continue
                        }
                    }
                }
            } catch (_: Exception) {
                // ignore
            }
        }

    // ==================== XÁC THỰC ====================
    suspend fun verifyPassword(password: String, role: Byte = ROLE_COMMAND): Boolean =
        withContext(Dispatchers.IO) {
            val holder = connections[role] ?: return@withContext false
            val success = doVerify(holder, password)
            listeners.forEach { it.onVerifyResult(role, success) }
            success                       // ← THÊM: trả kết quả thật
        }

    private suspend fun doVerify(
        state: HybridConnectionModel,
        password: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val output = state.output ?: return@withContext false
            output.write("verifypassword ${md5(password)}\n".toByteArray())
            output.flush()

            val input = state.input ?: return@withContext false
            var attempts = 0
            while (attempts < 20) {
                if (input.available() > 0) {
                    val available = input.available()
                    val buffer = ByteArray(available)
                    val read = input.read(buffer, 0, available)
                    if (read > 0) {
                        val response = String(buffer, 0, read)
                        val parts = response.split(" ")
                        if (parts.size > 1 && parts[1] == "ok\n") {
                            state.isNeedPassword = false
                            return@withContext true
                        }
                    }
                }
                delay(150L.milliseconds)
                attempts++
            }
        } catch (_: Exception) {
            // ignore
        }
        return@withContext false
    }

    // ==================== ĐÓNG KẾT NỐI ====================
    fun close(role: Byte) {
        connections.remove(role)?.let { hybridState ->
            try {
                hybridState.input?.close()
                hybridState.output?.close()

                hybridState.input = null
                hybridState.output = null

                if (hybridState.isBluetooth) {
                    (hybridState.socket as? BluetoothSocket)?.close()
                } else {
                    (hybridState.socket as? Socket)?.close()
                }
                hybridState.socket = null
            } catch (e: Exception) {
            }
        }

        if (connections.isEmpty()) {
            isConnectedAll.value = false
        }
    }

    fun closeAll() {
        connections.keys.toList().forEach { close(it) }
    }

    // ==================== UTILITY ====================
    private fun md5(str: String): String {
        if (str.isEmpty()) return ""
        return try {
            val digest = MessageDigest.getInstance("MD5").digest(str.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: NoSuchAlgorithmException) {
            ""
        }
    }
}

// Helper class để trả về 5 giá trị
private data class Quintuple<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val friday: E
)