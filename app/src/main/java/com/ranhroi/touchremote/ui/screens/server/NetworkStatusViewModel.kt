package com.ranhroi.touchremote.ui.screens.server

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ranhroi.touchremote.network.BootReceiver
import com.ranhroi.touchremote.utils.BluetoothPermissionHandler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NetworkStatusViewModel(application: Application) : AndroidViewModel(application) {
    @SuppressLint("StaticFieldLeak")
    private val context = application.applicationContext
    private val _isWifiOn = MutableStateFlow(false)
    val isWifiOn: StateFlow<Boolean> = _isWifiOn.asStateFlow()
    private val _isBluetoothOn = MutableStateFlow(false)
    val isBluetoothOn: StateFlow<Boolean> = _isBluetoothOn.asStateFlow() // Luồng sự kiện để yêu cầu UI thực hiện hành động
    private val _requestAction = MutableSharedFlow<BluetoothAction>(extraBufferCapacity = 1)
    val requestAction = _requestAction.asSharedFlow()

    enum class BluetoothAction { REQUEST_PERMISSION, REQUEST_ENABLE, START_SCAN }

    private var receiver: BootReceiver? = null

    init {
        registerReceiver(context)
    }

    fun checkBleAndRequest() {
        val context = getApplication<Application>().applicationContext
        viewModelScope.launch {
            if (!BluetoothPermissionHandler.hasBluetoothPermissions(context)) {
                _requestAction.emit(BluetoothAction.REQUEST_PERMISSION)
            } else { // Nếu đã có quyền nhưng BT đang tắt -> Yêu cầu bật BT
            }
            val btAdapter =
                BluetoothAdapter.getDefaultAdapter()
            if (btAdapter?.isEnabled == false) {
                _requestAction.emit(BluetoothAction.REQUEST_ENABLE)
            } // Lưu ý: Nếu đã bật cả 2, logic tự động quét sẽ do LaunchedEffect(isBluetoothOn) xử lý
        }
    }

    /** * Mở màn hình/panel cài đặt Wi-Fi. * * LƯU Ý QUAN TRỌNG: * Vì [context] ở đây là Application context (không phải Activity), * bắt buộc phải thêm cờ [Intent.FLAG_ACTIVITY_NEW_TASK], nếu không sẽ gặp lỗi: * "Calling startActivity() from outside of an Activity context requires the FLAG_ACTIVITY_NEW_TASK flag." */
    fun checkWifiAndRequest() {
        if (_isWifiOn.value) return // Android 10 (Q) trở lên: mở trực tiếp panel Wi-Fi (nhanh, không rời app nhiều) // Android 9 trở xuống: mở màn hình Wireless Settings
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_WIFI)
        } else {
            Intent(Settings.ACTION_WIRELESS_SETTINGS)
        }.apply { // BẮT BUỘC: vì startActivity được gọi từ Application context
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback: một số ROM không hỗ trợ Settings.Panel.ACTION_WIFI
            try {
                context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (_: Exception) { // Fallback cuối: mở Settings chung
                context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        }
    }

    private fun registerReceiver(context: Context) {
        if (receiver != null) return // tránh đăng ký nhiều lần
        val filter = IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        receiver = BootReceiver(
            onWifiChanged = { enabled -> _isWifiOn.value = enabled },
            onBluetoothChanged = { enabled ->
                _isBluetoothOn.value = enabled
            })
        context.registerReceiver(receiver, filter) // set trạng thái hiện tại ngay khi mở
        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        _isWifiOn.value = wifiManager.isWifiEnabled
        val btAdapter = BluetoothAdapter.getDefaultAdapter()
        _isBluetoothOn.value = btAdapter?.isEnabled == true
    }

    private fun unregisterReceiver(context: Context) {
        receiver?.let {
            context.unregisterReceiver(it)
            receiver = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        unregisterReceiver(context)
    }
};