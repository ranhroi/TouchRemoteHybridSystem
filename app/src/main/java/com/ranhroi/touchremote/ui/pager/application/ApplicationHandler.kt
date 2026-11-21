package com.ranhroi.touchremote.ui.pager.application

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.ranhroi.touchremote.ui.screens.server.HybridConnectionModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.nio.charset.StandardCharsets

class ApplicationHandler(
    private val hybridState: HybridConnectionModel
) {
    suspend fun fetchApplications(
        onListReady: (List<DockInfoModel>) -> Unit,
        onIconReady: (String, Bitmap) -> Unit
    ) = withContext(Dispatchers.IO) {
        val input = hybridState.input ?: return@withContext
        val output = hybridState.output ?: return@withContext

        try {
            // GIAI ĐOẠN 1: LẤY DANH SÁCH TEXT
            output.write("get_dock_info\n".toByteArray(StandardCharsets.UTF_8))
            output.flush()

            val sb = StringBuilder()
            // Vòng lặp nhận JSON danh sách
            while (isActive) {
                val available = input.available()
                if (available > 0) {
                    val bArr = ByteArray(available)
                    val read = input.read(bArr)
                    sb.append(String(bArr, 0, read, StandardCharsets.UTF_8))
                    if (sb.contains("\n")) break
                } else {
                    delay(100) // delay thay cho sleep (không block thread)
                }
            }

            val responseStr = sb.toString()
            val dockInfoIndex = responseStr.indexOf("dock_info")
            if (dockInfoIndex == -1) return@withContext

            val jsonPart = responseStr.substring(dockInfoIndex + 9).trim()
            val jsonArray = JSONArray(jsonPart)
            val initialList = mutableListOf<DockInfoModel>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                initialList.add(DockInfoModel(
                    name = obj.getString("Name"),
                    bundleID = obj.getString("BundleID"),
                    running = obj.getBoolean("Running"),
                    icon = null
                ))
            }

            // Cập nhật danh sách thô về Main Thread
            withContext(Dispatchers.Main) {
                onListReady(initialList)
            }

            // GIAI ĐOẠN 2: LẤY TỪNG ICON MỘT
            for (app in initialList) {
                if (!isActive) break // Kiểm tra nếu coroutine bị hủy ở ngoài

                output.write("get_icon ${app.bundleID}\n".toByteArray(StandardCharsets.UTF_8))
                output.flush()

                val iconBuffer = StringBuilder()
                val temp = ByteArray(8192) // Tăng buffer lên 8KB để nhận Base64 nhanh hơn

                while (isActive) {
                    val read = input.read(temp)
                    if (read > 0) {
                        val chunk = String(temp, 0, read, StandardCharsets.UTF_8)
                        iconBuffer.append(chunk)
                        if (chunk.contains("\n")) break
                    }
                }

                val fullResponse = iconBuffer.toString().trim()
                val prefix = "icon ${app.bundleID} png "

                if (fullResponse.startsWith(prefix)) {
                    val base64Str = fullResponse.removePrefix(prefix)
                    decodeAndEmitIcon(app.bundleID, base64Str, onIconReady)
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun decodeAndEmitIcon(
        bundleID: String,
        base64Str: String,
        onIconReady: (String, Bitmap) -> Unit
    ) {
        try {
            val iconBytes = Base64.decode(base64Str, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(iconBytes, 0, iconBytes.size)
            if (bitmap != null) {
                withContext(Dispatchers.Main) {
                    onIconReady(bundleID, bitmap)
                }
            }
        } catch (e: Exception) {
            // Log lỗi nếu cần
        }
    }
}
