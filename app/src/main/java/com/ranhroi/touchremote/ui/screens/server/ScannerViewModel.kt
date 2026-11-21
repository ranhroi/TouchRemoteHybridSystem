package com.ranhroi.touchremote.ui.screens.server


import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ranhroi.touchremote.models.DeviceServerModel
import com.ranhroi.touchremote.scanner.BluetoothScanner
import com.ranhroi.touchremote.scanner.TcpScanner
import com.ranhroi.touchremote.scanner.UdpScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val app: Application
) : ViewModel() {

    private val bluetoothScanner = BluetoothScanner(app)

    private val _devicesBlt = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val devicesBlt = _devicesBlt.asStateFlow()

    private val _isLoadingBlt = MutableStateFlow(false)
    val isLoadingBlt = _isLoadingBlt.asStateFlow()


    private var tcpScanJob: Job? = null

    private val _devicesTcp = MutableStateFlow<List<DeviceServerModel>>(emptyList())
    val devicesTcp = _devicesTcp.asStateFlow()

    private val _isLoadingTcp = MutableStateFlow(false)
    val isLoadingTcp = _isLoadingTcp.asStateFlow()

    /** --- QUÉT MẠNG (UDP + TCP) --- */
    fun startScanTcp(addressPortsModel: AddressPortsModel) {
        // Hủy đợt quét cũ nếu đang chạy
        stopScanTcp()
        _devicesTcp.value = emptyList()
        _isLoadingTcp.value = true

        tcpScanJob = viewModelScope.launch {
            // Chạy song song: UDP và TCP Scanner
            // Khi tìm thấy thiết bị, dữ liệu sẽ được đẩy thẳng vào _devicesTcp

            launch(Dispatchers.IO) {
                // 1. Quét nhanh bằng UDP Broadcast
                val udpScanner = UdpScanner(addressPortsModel)
                udpScanner.scan().collect { device ->
                    updateTcpDeviceList(device)
                }
            }

            launch(Dispatchers.IO) {
                // 2. Quét toàn bộ dải IP bằng TCP (Dự phòng cho UDP)
                val tcpScanner = TcpScanner(app, addressPortsModel)
                tcpScanner.scan().collect { device ->
                    updateTcpDeviceList(device)
                }
            }

            // Tự động tắt loading sau 10 giây (hoặc tùy bạn chỉnh)
            delay(10000.milliseconds)
            _isLoadingTcp.value = false
        }
    }

    private fun updateTcpDeviceList(device: DeviceServerModel) {
        _devicesTcp.update { list ->
            // Tránh trùng lặp IP và Port
            if (list.none { it.address == device.address && it.port == device.port }) {
                list + device
            } else {
                list
            }
        }
    }

    fun stopScanTcp() {
        tcpScanJob?.cancel()
        tcpScanJob = null
        _isLoadingTcp.value = false
    }

    /** --- QUÉT BLUETOOTH (Có refresh) --- */
    @Suppress("MissingPermission")
    fun startScanBlt() {
        try {
            _isLoadingBlt.value = true
            bluetoothScanner.startScan()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            _devicesBlt.value = bluetoothScanner.getPairedDevices().toList()
            stopScanBlt()
        }
    }

    fun stopScanBlt() {
        bluetoothScanner.stopScan()
        _isLoadingBlt.value = false
    }

    override fun onCleared() {
        super.onCleared()
        stopScanTcp()
        stopScanBlt()
    }
}
