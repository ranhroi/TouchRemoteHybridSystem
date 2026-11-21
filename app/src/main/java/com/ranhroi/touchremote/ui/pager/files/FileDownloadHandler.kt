package com.ranhroi.touchremote.ui.pager.files

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import com.ranhroi.touchremote.R
import com.ranhroi.touchremote.ui.screens.server.HybridConnectionModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlin.time.Duration.Companion.milliseconds

class FileDownloadHandler(
    private val context: Context,
    private val hybridState: HybridConnectionModel,
    private val callbacks: DownloadCallbacks
) {
    interface DownloadCallbacks {
        suspend fun getSavedProgress(fileName: String): DownloadProgressModel?
        suspend fun clearSavedProgress(fileName: String)
        fun onShowProgress(title: String, name: String, msg: String, size: Long = 0)
        fun onUpdateProgress(progress: Float, detail: String)
        fun onHideProgress()
        fun onEmitEvent(msg: String): Job
        fun onGetPath(): String
        fun onGetCancelFlag(): Boolean
        fun onUpdateCurrentDownload(name: String, offset: Long, size: Long)
        fun onSaveCurrentProgress()
        fun formatFileSize(size: Long): String
        suspend fun onReadLineFromSocket(input: InputStream): String
        suspend fun onReadBinarySize(input: InputStream): Long
        suspend fun ensureTransferConnection(): Boolean
        suspend fun askExtractOrKeepZip(folderName: String, zipPath: String): Boolean
        fun onTransferComplete()
    }

    companion object {
        private const val BLUETOOTH_CHUNK_SIZE = 16 * 1024  // 16KB cho Bluetooth
        private const val TCP_CHUNK_SIZE = 64 * 1024        // 64KB cho TCP
        private const val BLUETOOTH_DELAY_MS = 20L          // 20ms delay
    }

    /**
     * HÀM LÕI VẠN NĂNG: Xử lý luồng tải nhị phân từ Socket vào File
     * Dùng chung cho: Tải file đơn, Tải từng file trong Folder, Tải file ZIP tạm
     */
    private suspend fun downloadBinaryStream(
        command: String,
        targetFile: File,
        isAppend: Boolean,
        displayName: String,
        isSilentHandshake: Boolean = false,
    ): Long = withContext(Dispatchers.IO) {
        val input =
            hybridState.input
                ?: throw Exception(context.getString(R.string.download_connection_lost))
        val output = hybridState.output
            ?: throw Exception(context.getString(R.string.download_connection_lost_output))

        if (!isSilentHandshake) {
            if (!callbacks.ensureTransferConnection()) {
                throw Exception(context.getString(R.string.download_connection_lost_output))
            }
            // 1. Dọn rác
            while (input.available() > 0) input.read()
            // 2. Gửi lệnh
            output.write(command.toByteArray(Charsets.UTF_8))
            output.flush()

            // 3. Chờ phản hồi OK
            var response = ""
            while (isActive) {
                response = callbacks.onReadLineFromSocket(input).trim()
                if (response == "pong" || response.isEmpty()) continue
                break
            }


            if (response.startsWith("download_error") || response != "OK") throw ErrorHandler.handleServerError(
                response,
                context
            )
        }

        // 4. Đọc Kích thước (8 bytes)
        val totalFileSize = callbacks.onReadBinarySize(input)
        if (totalFileSize <= 0) throw Exception(context.getString(R.string.download_invalid_size))

        val startOffset = if (isAppend) targetFile.length() else 0L
        val expectedToRead = totalFileSize - startOffset
        if (expectedToRead <= 0) return@withContext totalFileSize

        // 5. Bắt đầu đọc dữ liệu nhị phân
        val bufferSize = if (hybridState.isBluetooth) BLUETOOTH_CHUNK_SIZE else TCP_CHUNK_SIZE
        val buffer = ByteArray(bufferSize)
        val chunkDelay = if (hybridState.isBluetooth) BLUETOOTH_DELAY_MS else 0L
        var bytesReadInThisSession = 0L
        var lastUpdate = 0L

        FileOutputStream(targetFile, isAppend).use { fileStream ->
            while (bytesReadInThisSession < expectedToRead && !callbacks.onGetCancelFlag() && isActive) {
                val toRead =
                    minOf(buffer.size.toLong(), expectedToRead - bytesReadInThisSession).toInt()

                var currentChunkRead = 0
                while (currentChunkRead < toRead && !callbacks.onGetCancelFlag() && isActive) {
                    val r = input.read(buffer, currentChunkRead, toRead - currentChunkRead)
                    if (r == -1) {
                        callbacks.onSaveCurrentProgress()
                        throw Exception(context.getString(R.string.download_network_lost))
                    }
                    currentChunkRead += r
                }

                fileStream.write(buffer, 0, currentChunkRead)
                bytesReadInThisSession += currentChunkRead

                // Cập nhật tọa độ cho ViewModel (để resume)
                callbacks.onUpdateCurrentDownload(
                    displayName,
                    startOffset + bytesReadInThisSession,
                    totalFileSize
                )

                // Cập nhật UI
                val now = System.currentTimeMillis()
                if (now - lastUpdate > 300 || bytesReadInThisSession == expectedToRead) {
                    val progress = (startOffset + bytesReadInThisSession).toFloat() / totalFileSize
                    val detail =
                        "${callbacks.formatFileSize(startOffset + bytesReadInThisSession)} / ${
                            callbacks.formatFileSize(totalFileSize)
                        }"
                    withContext(Dispatchers.Main) { callbacks.onUpdateProgress(progress, detail) }
                    lastUpdate = now
                }

                // ✅ DELAY CHO BLUETOOTH
                if (chunkDelay > 0) {
                    delay(chunkDelay.milliseconds)
                }

                yield()
            }
            fileStream.flush()
        }

        if (!callbacks.onGetCancelFlag() && (startOffset + bytesReadInThisSession) != totalFileSize) {
            throw Exception(context.getString(R.string.download_incomplete_data))
        }

        return@withContext totalFileSize
    }


    // ==================== 1. TẢI FILE ĐƠN ====================
    suspend fun downloadFile(context: Context, name: String, resume: Boolean = true) =
        withContext(Dispatchers.IO) {
            if (!callbacks.ensureTransferConnection()) {
                withContext(Dispatchers.Main) {
                    callbacks.onEmitEvent(context.getString(R.string.download_no_connection))
                }
                return@withContext
            }

            val downloadDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            var targetFile = File(downloadDir, name)
            var startOffset = 0L

            try {
                if (resume) {
                    val saved = callbacks.getSavedProgress(name)
                    if (saved != null && saved.offset > 0 && targetFile.exists()) {
                        startOffset = saved.offset
                        withContext(Dispatchers.Main) {
                            callbacks.onShowProgress(
                                context.getString(R.string.download_resume_title),
                                name,
                                context.getString(R.string.download_resuming),
                                saved.fileSize
                            )
                        }
                    }
                }

                if (startOffset == 0L) {
                    callbacks.clearSavedProgress(name)
                    var count = 1
                    while (targetFile.exists()) {
                        targetFile = File(
                            downloadDir,
                            "${name.substringBeforeLast(".")}($count).${name.substringAfterLast(".")}"
                        )
                        count++
                    }
                    withContext(Dispatchers.Main) {
                        callbacks.onShowProgress(
                            context.getString(R.string.download_title),
                            name,
                            context.getString(R.string.download_initializing)
                        )
                    }
                }

                val serverPath = "${callbacks.onGetPath().removeSuffix("/")}/$name"
                val cmd = "downloadfile $serverPath|$startOffset\n"

                downloadBinaryStream(cmd, targetFile, resume, name)

                if (!callbacks.onGetCancelFlag()) {
                    callbacks.clearSavedProgress(name)
                    withContext(Dispatchers.Main) {
                        callbacks.onShowProgress(
                            context.getString(R.string.download_complete_title),
                            targetFile.name,
                            context.getString(R.string.download_saving)
                        )
                        delay(1500.milliseconds)
                        callbacks.onHideProgress()
                        callbacks.onEmitEvent(
                            context.getString(
                                R.string.download_complete_msg,
                                targetFile.name
                            )
                        )
                        MediaScannerConnection.scanFile(
                            context,
                            arrayOf(targetFile.absolutePath),
                            null,
                            null
                        )
                    }
                }
                callbacks.onTransferComplete()
            } catch (e: Exception) {
                callbacks.onSaveCurrentProgress()
                withContext(Dispatchers.Main) {
                    callbacks.onEmitEvent(
                        context.getString(
                            R.string.download_error,
                            e.message ?: "Unknown"
                        )
                    )
                }
            }
        }


    // ==================== 2. TẢI THƯ MỤC (Duyệt JSON) ====================
    suspend fun downloadFolder(context: Context, serverFolderPath: String) =
        withContext(Dispatchers.IO) {
            if (!callbacks.ensureTransferConnection()) {
                withContext(Dispatchers.Main) {
                    callbacks.onEmitEvent(context.getString(R.string.download_connection_lost_output))
                }
                return@withContext
            }

            try {
                withContext(Dispatchers.Main) {
                    callbacks.onShowProgress(
                        context.getString(R.string.download_scanning_folder),
                        "",
                        context.getString(R.string.download_please_wait)
                    )
                }
                val input =
                    hybridState.input
                        ?: throw Exception(context.getString(R.string.download_connection_lost))
                val output = hybridState.output
                    ?: throw Exception(context.getString(R.string.download_connection_lost_output))


                while (input.available() > 0) input.read()
                output.write("getfolderinfo $serverFolderPath\n".toByteArray())
                output.flush()

                var response = ""
                while (isActive) {
                    response = callbacks.onReadLineFromSocket(input).trim()
                    if (response != "pong" && response.isNotEmpty()) break
                }
                if (!response.startsWith("folder_info ")) throw Exception(context.getString(R.string.download_no_folder_response))

                val rootObj = JSONObject(response.substring(12))
                val folderName = rootObj.getString("folder_name")
                val totalSize = rootObj.getLong("total_size")
                val items = rootObj.getJSONArray("items")

                val downloadDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                var targetRoot = File(downloadDir, folderName)
                var count = 1
                while (targetRoot.exists()) {
                    targetRoot = File(downloadDir, "$folderName($count)"); count++
                }
                targetRoot.mkdirs()

                var downloadedBytes = 0L
                for (i in 0 until items.length()) {
                    if (callbacks.onGetCancelFlag() || !isActive) break
                    val item = items.getJSONObject(i)
                    val relPath = item.getString("path")
                    val localFile = File(targetRoot, relPath)

                    if (item.getBoolean("isDir")) {
                        localFile.mkdirs(); continue
                    }
                    localFile.parentFile?.mkdirs()

                    withContext(Dispatchers.Main) {
                        callbacks.onShowProgress(
                            context.getString(
                                R.string.download_folder_title,
                                folderName
                            ),
                            localFile.name,
                            context.getString(
                                R.string.download_folder_progress,
                                i + 1,
                                items.length()
                            ),
                            totalSize
                        )
                    }

                    val itemCmd = "downloadfile $serverFolderPath/$relPath|0\n"
                    // Sử dụng hàm lõi để tải từng file con
                    downloadBinaryStream(itemCmd, localFile, false, localFile.name)

                    downloadedBytes += localFile.length()
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(localFile.absolutePath),
                        null,
                        null
                    )
                    delay(50.milliseconds)
                }

                withContext(Dispatchers.Main) {
                    callbacks.onHideProgress()
                    callbacks.onEmitEvent(context.getString(R.string.download_folder_complete))
                }
                callbacks.onTransferComplete()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callbacks.onHideProgress(); callbacks.onEmitEvent(
                    context.getString(R.string.download_error, e.message ?: "Unknown")
                )
                }
            }
        }


    // ==================== 3. TẢI THƯ MỤC (ZIP MODE) ====================
    suspend fun downloadFolderZip(context: Context, folderName: String) =
        withContext(Dispatchers.IO) {
            if (!callbacks.ensureTransferConnection()) {
                withContext(Dispatchers.Main) {
                    callbacks.onEmitEvent(context.getString(R.string.download_connection_lost_output))
                }
                return@withContext
            }

            // Tạo file tạm trong cache
            val tempZipFile =
                File(context.cacheDir, "${folderName}_${System.currentTimeMillis()}.zip")

            try {
                withContext(Dispatchers.Main) {
                    callbacks.onShowProgress(
                        context.getString(R.string.download_zip_title),
                        folderName,
                        context.getString(R.string.download_zip_compressing)
                    )
                }

                val currentPath = callbacks.onGetPath().removeSuffix("/")
                val cmd = "downloadfolder_zip $currentPath/$folderName\n"

                // 1. TẢI FILE ZIP VỀ CACHE
                val fileSize = downloadBinaryStream(cmd, tempZipFile, false, "$folderName.zip")

                if (fileSize <= 0) {
                    throw Exception(context.getString(R.string.download_zip_empty))
                }

                if (callbacks.onGetCancelFlag()) return@withContext

                // 2. HỎI NGƯỜI DÙNG
                val shouldExtract =
                    callbacks.askExtractOrKeepZip(folderName, tempZipFile.absolutePath)

                if (shouldExtract) {
                    // GIẢI NÉN
                    withContext(Dispatchers.Main) {
                        callbacks.onShowProgress(
                            context.getString(R.string.download_extracting),
                            folderName,
                            context.getString(R.string.download_extracting_progress)
                        )
                    }

                    val downloadDir =
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    var destFolder = File(downloadDir, folderName)
                    var count = 1
                    while (destFolder.exists()) {
                        destFolder = File(downloadDir, "$folderName($count)")
                        count++
                    }
                    destFolder.mkdirs()

                    var fileCount = 0
                    var hasError = false

                    ZipInputStream(FileInputStream(tempZipFile)).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null && isActive) {
                            val newFile = File(destFolder, entry.name)
                            if (entry.isDirectory) {
                                newFile.mkdirs()
                            } else {
                                newFile.parentFile?.mkdirs()
                                try {
                                    FileOutputStream(newFile).use { zis.copyTo(it) }
                                    fileCount++
                                } catch (e: Exception) {
                                    hasError = true
                                    callbacks.onEmitEvent(
                                        context.getString(
                                            R.string.download_extract_error,
                                            entry.name
                                        )
                                    )
                                }
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }

                    // Xóa file tạm sau khi giải nén thành công
                    tempZipFile.delete()

                    withContext(Dispatchers.Main) {
                        callbacks.onHideProgress()
                        if (hasError) {
                            callbacks.onEmitEvent(
                                context.getString(
                                    R.string.download_extract_complete_error,
                                    destFolder.name
                                )
                            )
                        } else {
                            callbacks.onEmitEvent(
                                context.getString(
                                    R.string.download_extract_complete,
                                    destFolder.name,
                                    fileCount
                                )
                            )
                        }
                        MediaScannerConnection.scanFile(
                            context,
                            arrayOf(destFolder.absolutePath),
                            null,
                            null
                        )
                    }
                } else {
                    // GIỮ NGUYÊN FILE ZIP - LƯU VÀO DOWNLOADS
                    val downloadDir =
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    var zipFile = File(downloadDir, "$folderName.zip")
                    var count = 1
                    while (zipFile.exists()) {
                        zipFile = File(downloadDir, "$folderName($count).zip")
                        count++
                    }

                    if (tempZipFile.exists() && tempZipFile.length() > 0) {
                        // Copy file từ cache sang Downloads (không rename vì cache có thể bị xóa)
                        tempZipFile.copyTo(zipFile, overwrite = false)
                        tempZipFile.delete() // Xóa file tạm sau khi copy thành công

                        withContext(Dispatchers.Main) {
                            callbacks.onHideProgress()
                            callbacks.onEmitEvent(
                                context.getString(
                                    R.string.download_zip_saved,
                                    zipFile.name,
                                    callbacks.formatFileSize(zipFile.length())
                                )
                            )
                            MediaScannerConnection.scanFile(
                                context,
                                arrayOf(zipFile.absolutePath),
                                null,
                                null
                            )
                        }
                    } else {
                        throw Exception(context.getString(R.string.download_zip_temp_error))
                    }
                }
                callbacks.onTransferComplete()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callbacks.onHideProgress()
                    callbacks.onEmitEvent(
                        context.getString(
                            R.string.download_zip_error,
                            e.message ?: "Unknown"
                        )
                    )
                }
                // Xóa file tạm nếu có lỗi
                if (tempZipFile.exists()) tempZipFile.delete()
            } finally {
                // Đảm bảo file tạm được xóa (nếu chưa xóa)
                if (tempZipFile.exists()) {
                    tempZipFile.delete()
                }
            }
        }
}