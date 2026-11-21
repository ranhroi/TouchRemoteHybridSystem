package com.ranhroi.touchremote.ui.pager.files

import android.content.Context
import com.ranhroi.touchremote.R
import com.ranhroi.touchremote.ui.screens.server.HybridConnectionModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.lang.StringBuilder

class FileExplorerHandler(
    private val context: Context,
    private val path: String,
    private val hybridState: HybridConnectionModel
) {

    suspend fun execute(): List<FileModel> = withContext(Dispatchers.IO) {
        if (!hybridState.isConnected || hybridState.input == null || hybridState.output == null) {
            return@withContext emptyList()  // ✅ TRẢ VỀ RỖNG, KHÔNG LỖI
        }

        val fileList = mutableListOf<FileModel>()
        val sb = StringBuilder()
        val input =
            hybridState.input
                ?: throw Exception(context.getString(R.string.download_connection_lost))
        val output = hybridState.output
            ?: throw Exception(context.getString(R.string.download_connection_lost_output))


        try {
            // Xóa rác trong stream trước khi gửi lệnh mới
            input.skip(input.available().toLong())

            // Gửi lệnh truy vấn file
            output.apply {
                write("fileexplorer $path\n".toByteArray(Charsets.UTF_8))
                flush()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext emptyList()
        }

        var retry = 0
        // Vòng lặp nhận dữ liệu
        while (retry < 20) {
            try {
                val available = input?.available() ?: 0
                if (available > 0) {
                    val buffer = ByteArray(available)
                    input?.read(buffer)
                    val chunk = String(buffer, Charsets.UTF_8)
                    sb.append(chunk)

                    // Nếu kết thúc bằng dấu xuống dòng thì đã nhận đủ
                    if (sb.endsWith("\n")) break
                } else {
                    retry++
                    delay(100)
                }
            } catch (e: Exception) {
                break
            }
        }

        // Xử lý logic thư mục cha ".."
        if (path != "~" && path != "/" && path.isNotEmpty()) {
            fileList.add(FileModel("..", isDirectory = true))
        }

        // Parse dữ liệu nhận được
        if (sb.length > 2) {
            // Loại bỏ ký tự kết thúc và tách dòng
            sb.toString().trimEnd().split("\r").forEach { line ->
                if (line.isNotEmpty()) {
                    val isDir = line.first() != 'f'
                    fileList.add(FileModel(line.substring(1), isDir))
                }
            }
        }

        return@withContext fileList
    }
}
