package com.ranhroi.touchremote.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat

object BluetoothPermissionHandler {

    private const val REQUEST_CODE_BLUETOOTH = 1001

    /**
     * Kiểm tra quyền Bluetooth (Android 12+ mới cần)
     */
    fun hasBluetoothPermissions(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_SCAN
                    ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Android 11 trở xuống không cần runtime permission
        }
    }

    fun checkSelfPermission(context: Context): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_SCAN
        ) != PackageManager.PERMISSION_GRANTED
    }


    /**
     * Xin quyền Bluetooth từ Activity
     */
    fun requestBluetoothPermissions(activity: Activity, requestCode: Int = REQUEST_CODE_BLUETOOTH) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                ),
                requestCode
            )
        }
    }

    // ============================================================
    // ✅ THÊM MỚI — KHÔNG ĐỤNG VÀO CÁC HÀM CŨ Ở TRÊN
    // ============================================================

    /**
     * Trả về đúng danh sách quyền cần xin cho BLE scan theo phiên bản Android.
     * - Android 12+ : BLUETOOTH_SCAN + BLUETOOTH_CONNECT
     * - Android 11- : ACCESS_FINE_LOCATION + ACCESS_COARSE_LOCATION
     *                 (BLE scan trên Android 11- vẫn yêu cầu quyền vị trí)
     */
    fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }

    /**
     * Kiểm tra đã có ĐỦ quyền (bao gồm cả quyền vị trí trên Android 11-).
     *
     * Khác với hasBluetoothPermissions() ở chỗ:
     *   - hasBluetoothPermissions() : trả true cho Android 11- (giữ nguyên hành vi cũ)
     *   - hasAllRequiredPermissions(): check thêm ACCESS_FINE_LOCATION trên Android 11-
     *
     * Dùng hàm này khi quyết định "có nên xin quyền trước khi scan BLE hay không".
     */
    fun hasAllRequiredPermissions(context: Context): Boolean {
        return getRequiredPermissions().all { permission ->
            ActivityCompat.checkSelfPermission(
                context, permission
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}