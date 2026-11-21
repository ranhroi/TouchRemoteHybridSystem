package com.ranhroi.touchremote.utils

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.view.SoundEffectConstants
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.annotation.StringRes
import com.ranhroi.touchremote.models.DeviceServerModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

fun Context.showToast(msg: String) {
    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

fun Context.showToastLong(msg: String) {
    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}

fun Context.showToastSafe(@StringRes resId: Int, vararg formatArgs: Any?) {
    try {
        val message = getString(resId, *formatArgs)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        // Bỏ qua hoặc log error
    }
}

fun Context.showToastSafe(message: String?) {
    if (!message.isNullOrEmpty()) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

fun Context.getStringSafe(resId: Int): String {
    return try {
        getString(resId)
    } catch (e: Exception) {
        ""
    }
}


fun Context.getStringSafe(@StringRes id: Int, vararg formatArgs: Any): String {
    return try {
         getString(id, *formatArgs)
    } catch (e: Exception) {
        ""
    }
}

fun View.playSoundClick() {
    this.playSoundEffect(SoundEffectConstants.CLICK)
}

// Thêm RegexOption.IGNORE_CASE để xử lý nhanh hơn và an toàn hơn
private val UUID_REGEX = Regex(
    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
    RegexOption.IGNORE_CASE
)

fun String?.isValidUUID(): Boolean {
    if (this == null) return false

    // 1. Dùng trim() để loại bỏ khoảng trắng, dấu cách, ký tự xuống dòng ẩn
    val cleanInput = this.trim()

    // 2. Kiểm tra bằng regex
    return UUID_REGEX.matches(cleanInput)
}

@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
fun BluetoothDevice.toEntity(): DeviceServerModel {

    // Chuyển danh sách ParcelUuid thành List<String>
//    val listUuids = this.uuids?.map { it.toString() } ?: emptyList()

    // 1. Tìm xem trong danh sách có mã của mình không
//    val selectedUuid = listUuids.find { it.equals(ConstantsUtils.DEFAULT_SERVER_UUID, ignoreCase = true) }
//    // 2. Nếu không có, lấy thằng ĐẦU TIÊN (Ưu tiên cao hơn cuối)
//        ?: listUuids.firstOrNull()
//        // 3. Nếu danh sách trống rỗng, dùng luôn mã mặc định
//        ?: ConstantsUtils.DEFAULT_SERVER_UUID


    // Lấy 3 chữ số đầu tiên của thời gian hiện tại dưới dạng String
    val timeStr = System.currentTimeMillis().toString()
    val randomThree = timeStr.substring(timeStr.length - 3)

    return DeviceServerModel(
        name = this.name ?: "Unknown Device",
        address = this.address,
        uuid = ConstantsUtils.DEFAULT_SERVER_UUID,
        port = randomThree.toInt(),
        bluetooth = true
    )
}


@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
fun List<BluetoothDevice>.toEntity(): List<DeviceServerModel> {
    return map { it.toEntity() } // Tái sử dụng hàm trên
}


fun DeviceServerModel.toBluetoothDevice(): BluetoothDevice? {
    if (!bluetooth) return null // chỉ convert nếu là thiết bị bluetooth

    val adapter = BluetoothAdapter.getDefaultAdapter()
    return adapter?.getRemoteDevice(address)
}

fun CoroutineScope.sendChannel(event: String) {
    launch {
        ChannelManager.send(event)
    }
}

