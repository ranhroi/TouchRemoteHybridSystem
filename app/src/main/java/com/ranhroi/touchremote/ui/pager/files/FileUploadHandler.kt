package com.ranhroi.touchremote.ui.pager.files

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.ranhroi.touchremote.R
import com.ranhroi.touchremote.ui.screens.server.HybridConnectionModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.time.Duration.Companion.milliseconds

data class DocFileInfo(val document: DocumentFile, val relativePath: String)


class FileUploadHandler(
    private val context: Context,
    private val hybridState: HybridConnectionModel,
    private val callbacks: UploadCallbacks
) {
    interface UploadCallbacks {
        fun onShowProgress(title: String, name: String, msg: String, size: Long = 0)
        fun onUpdateProgress(percent: Float, detail: String)
        fun onHideProgress()
        fun onEmitEvent(msg: String): Job
        fun onGetPath(): String
        fun onGetCancelFlag(): Boolean
        suspend fun onFetchFiles()
        fun onUpdateTransferProgress(progress: Float)
        fun formatSize(size: Long): String
        suspend fun ensureTransferConnection(): Boolean
        fun onTransferComplete()
    }

    companion object {
        private const val BLUETOOTH_CHUNK_SIZE = 16 * 1024  // 16KB cho Bluetooth
        private const val TCP_CHUNK_SIZE = 32 * 1024        // 32KB cho TCP
        private const val BLUETOOTH_DELAY_MS = 20L          // 20ms delay
        private const val RESPONSE_TIMEOUT_MS = 30_000L     // ✅ 30s timeout cho response
    }

    // ==================== CORE UPLOAD (ĐÃ TÍCH HỢP CHỐNG LỆCH PHA MẠNG) ====================
    private suspend fun performCoreUpload(
        inputStream: InputStream,
        commandString: String,
        fileSize: Long,
        expectFinalAck: Boolean,
        onProgress: suspend (bytesUploadedThisFile: Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        val input =
            hybridState.input
                ?: throw Exception(context.getString(R.string.download_connection_lost))
        val output = hybridState.output
            ?: throw Exception(context.getString(R.string.download_connection_lost_output))

        if (!callbacks.ensureTransferConnection()) {
            throw Exception(context.getString(R.string.upload_no_connection))
        }

        // #2: BỎ dòng dọn rác `while (input.available() > 0) input.read()`
        //    — không an toàn vì available() không đáng tin trên socket stream.
        //    Với expectFinalAck = true, client luôn đọc hết response → không có rác tồn đọng.

        // 1. Ra lệnh upload
        output.write(commandString.toByteArray(Charsets.UTF_8))
        output.flush()

        // 2. Chờ PC đồng ý
        var response = ""
        while (true) {
            response = readLineFromSocket(context, input).trim()
            if (response == "pong" || response.isEmpty()) continue
            break
        }
        if (response != "OK" && response != "upload_ready") {
            throw ErrorHandler.handleServerError(response, context)
        }

        // 3. Bơm dữ liệu
        val bufferSize = if (hybridState.isBluetooth) BLUETOOTH_CHUNK_SIZE else TCP_CHUNK_SIZE
        val buffer = ByteArray(bufferSize)
        val chunkDelay = if (hybridState.isBluetooth) BLUETOOTH_DELAY_MS else 0L
        var totalSent = 0L
        var lastUpdate = 0L
        var bytesRead: Int

        while (true && !callbacks.onGetCancelFlag()) {
            bytesRead = inputStream.read(buffer)
            if (bytesRead == -1) break

            output.write(buffer, 0, bytesRead)
            totalSent += bytesRead

            if (chunkDelay > 0) {
                delay(chunkDelay)
            }

            val currentTime = System.currentTimeMillis()
            if (currentTime - lastUpdate > 300 || totalSent == fileSize) {
                onProgress(totalSent)
                lastUpdate = currentTime
            }

            if (totalSent % (512 * 1024) == 0L) {
                output.flush()
                yield() // Nhường luồng cho hệ thống thở
            }
        }
        output.flush()

        // 4. Đọc xác nhận hoàn tất luân phiên theo cơ chế 3 Giai Đoạn
        if (!callbacks.onGetCancelFlag() && expectFinalAck) {
            var finalStatus = ""
            while (true) {
                finalStatus = readLineFromSocket(context, input).trim()
                if (finalStatus == "pong" || finalStatus.isEmpty()) continue

                // 🟩 GIAI ĐOẠN 2: Server báo nhận đủ 100% dữ liệu và bắt đầu giải nén
                if (finalStatus == "unzipping") {
                    onProgress(-1L) // Gửi mã tín hiệu đặc biệt về hàm cha để cập nhật UI loading
                    continue // Tiếp tục đứng im giữ kết nối để chờ gói tin chốt hạ cuối cùng
                }

                // 🟩 GIAI ĐOẠN 3: Nhận được lệnh chốt hạ thành công hoàn toàn từ Server
                if (finalStatus == "upload_complete") {
                    break
                }

                // Nếu Server phản hồi bất kỳ chuỗi lỗi nào khác
                throw ErrorHandler.handleServerError(finalStatus, context)
            }
        }
    }


    // ==================== UTILITY ====================
    // #4: Thêm timeout safety net — không treo vô hạn nếu server không gửi response
    private suspend fun readLineFromSocket(context: Context, input: InputStream): String =
        withTimeoutOrNull(RESPONSE_TIMEOUT_MS.milliseconds) {
            withContext(Dispatchers.IO) {
                val bos = ByteArrayOutputStream()
                var byte: Int
                while (true) {
                    byte = input.read()
                    if (byte == -1 || byte == '\n'.toInt()) break
                    if (byte != '\r'.toInt()) bos.write(byte)
                    if (bos.size() > 10 * 1024 * 1024) {
                        throw Exception(context.getString(R.string.upload_response_too_large))
                    }
                }
                bos.toString("UTF-8")
            }
        } ?: throw Exception("Server response timeout after ${RESPONSE_TIMEOUT_MS}ms")

    private fun getFileName(resolver: ContentResolver, uri: Uri): String? {
        return resolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst(); cursor.getString(index)
        }
    }

    private fun getFileSize(resolver: ContentResolver, uri: Uri): Long {
        return resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
    }

    private fun scanDirectoryRecursive(root: DocumentFile): List<DocFileInfo> {
        val all = mutableListOf<DocFileInfo>()
        val queue = ArrayDeque<Pair<DocumentFile, String>>()
        queue.add(Pair(root, ""))
        while (queue.isNotEmpty()) {
            val (doc, rel) = queue.removeFirst()
            doc.listFiles().forEach { child ->
                val path = if (rel.isEmpty()) child.name!! else "$rel/${child.name}"
                if (child.isDirectory) queue.add(Pair(child, path))
                else all.add(DocFileInfo(child, path))
            }
        }
        return all
    }

    // ==================== UPLOAD FUNCTIONS ====================

    // 1. Tải lên 1 file lẻ
    suspend fun uploadFile(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        if (!callbacks.ensureTransferConnection()) {
            withContext(Dispatchers.Main) {
                callbacks.onEmitEvent(context.getString(R.string.upload_no_connection))
            }
            return@withContext
        }

        var inputStream: InputStream? = null
        try {
            val resolver = context.contentResolver
            val fileName = getFileName(resolver, uri) ?: "file_${System.currentTimeMillis()}"
            val fileSize = getFileSize(resolver, uri)

            withContext(Dispatchers.Main) {
                callbacks.onShowProgress(
                    context.getString(R.string.upload_title),
                    fileName,
                    context.getString(R.string.upload_initializing),
                    fileSize
                )
            }

            inputStream = resolver.openInputStream(uri)
                ?: throw Exception(context.getString(R.string.upload_cannot_open_file))
            val cmd = "uploadfile ${callbacks.onGetPath()}/$fileName|$fileSize\n"

            performCoreUpload(inputStream, cmd, fileSize, expectFinalAck = true) { totalSent ->
                val progress = totalSent.toFloat() / fileSize
                val detail =
                    "${callbacks.formatSize(totalSent)} / ${callbacks.formatSize(fileSize)}"
                withContext(Dispatchers.Main) {
                    callbacks.onUpdateProgress(progress, detail)
                    callbacks.onUpdateTransferProgress(progress)
                }
            }

            if (!callbacks.onGetCancelFlag()) {
                withContext(Dispatchers.Main) {
                    callbacks.onHideProgress()
                    callbacks.onEmitEvent(context.getString(R.string.upload_success, fileName))
                    delay(300)
                    callbacks.onFetchFiles()
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                callbacks.onHideProgress()
                callbacks.onEmitEvent(
                    context.getString(
                        R.string.upload_error,
                        e.message ?: "Unknown"
                    )
                )
            }
        } finally {
            inputStream?.close()
        }
    }

    // 2. Tải lên thư mục (Từng file lẻ)
    suspend fun uploadFolder(context: Context, treeUri: Uri, pcTargetBasePath: String) =
        withContext(Dispatchers.IO) {
            if (!callbacks.ensureTransferConnection()) {
                withContext(Dispatchers.Main) {
                    callbacks.onEmitEvent(context.getString(R.string.upload_no_connection))
                }
                return@withContext
            }

            try {
                val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: throw Exception(
                    context.getString(R.string.upload_access_denied)
                )
                if (!rootDoc.isDirectory) throw Exception(context.getString(R.string.upload_invalid_path))
                val folderName = rootDoc.name ?: "Folder"

                withContext(Dispatchers.Main) {
                    callbacks.onShowProgress(
                        context.getString(R.string.upload_scanning_folder),
                        folderName,
                        context.getString(R.string.download_please_wait)
                    )
                }

                val allFiles = scanDirectoryRecursive(rootDoc)

                if (allFiles.isEmpty()) throw Exception(context.getString(R.string.upload_empty_folder))
                val totalFolderSize = allFiles.sumOf { it.document.length() }
                val totalFiles = allFiles.size
                if (totalFolderSize > 10L * 1024 * 1024 * 1024 || totalFiles > 50000) throw Exception(
                    context.getString(R.string.upload_folder_too_large)
                )

                var currentUploadedBytes = 0L
                var failedCount = 0

                for ((index, fileInfo) in allFiles.withIndex()) {
                    if (callbacks.onGetCancelFlag()) break

                    val relativePath = fileInfo.relativePath
                    val pcFullTargetPath = "$pcTargetBasePath/$folderName/$relativePath"
                    val fileSize = fileInfo.document.length()
                    val fileName = fileInfo.document.name ?: "file"

                    withContext(Dispatchers.Main) {
                        callbacks.onShowProgress(
                            context.getString(
                                R.string.upload_folder_progress,
                                folderName
                            ),
                            fileName,
                            context.getString(
                                R.string.upload_folder_file_progress,
                                index + 1,
                                totalFiles
                            ),
                            totalFolderSize
                        )
                    }

                    try {
                        val inputStream =
                            context.contentResolver.openInputStream(fileInfo.document.uri)
                                ?: throw Exception(context.getString(R.string.upload_read_error))

                        val cmd = "uploadfile $pcFullTargetPath|$fileSize\n"

                        // ✅ FIX #1: ĐỔI expectFinalAck = false → TRUE
                        //    Client phải đọc "upload_complete" sau mỗi file
                        //    → không có response tồn đọng → không lệch pha
                        performCoreUpload(
                            inputStream,
                            cmd,
                            fileSize,
                            expectFinalAck = true
                        ) { sentThisFile ->
                            val overallProgress =
                                (currentUploadedBytes + sentThisFile).toFloat() / totalFolderSize
                            val detail = "File ${index + 1}/${totalFiles} | ${
                                callbacks.formatSize(currentUploadedBytes + sentThisFile)
                            } / ${callbacks.formatSize(totalFolderSize)}"
                            withContext(Dispatchers.Main) {
                                callbacks.onUpdateProgress(overallProgress, detail)
                                callbacks.onUpdateTransferProgress(overallProgress)
                            }
                        }

                        inputStream.close()
                        delay(50.milliseconds)

                    } catch (fileEx: Exception) {
                        failedCount++
                        // #3: Không nuốt lỗi — abort toàn bộ folder
                        //    Nếu lỗi (lệch pha, mất kết nối, timeout), socket có thể
                        //    ở trạng thái nửa vời → tiếp tục sẽ hỏng dữ liệu file sau.
                        throw Exception(
                            "Upload failed at file ${index + 1}/${totalFiles} (${fileName}): ${fileEx.message}",
                            fileEx
                        )
                    }

                    currentUploadedBytes += fileSize
                }

                if (!callbacks.onGetCancelFlag()) {
                    withContext(Dispatchers.Main) {
                        callbacks.onHideProgress()
                        if (failedCount > 0) {
                            callbacks.onEmitEvent(
                                context.getString(
                                    R.string.upload_folder_complete,
                                    folderName,
                                    failedCount
                                )
                            )
                        } else {
                            callbacks.onEmitEvent(
                                context.getString(
                                    R.string.upload_folder_success,
                                    folderName
                                )
                            )
                        }
                        delay(300)
                        callbacks.onFetchFiles()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callbacks.onHideProgress()
                    callbacks.onEmitEvent(
                        context.getString(
                            R.string.upload_folder_error,
                            e.message ?: "Unknown"
                        )
                    )
                }
            }
        }

    // 3. Tải lên thư mục (Zip Mode) - ĐÃ ĐỒNG BỘ 3 GIAI ĐOẠN GIAO DIỆN
    suspend fun uploadFolderZip(context: Context, treeUri: Uri, pcTargetBasePath: String) =
        withContext(Dispatchers.IO) {
            if (!callbacks.ensureTransferConnection()) {
                withContext(Dispatchers.Main) {
                    throw Exception(context.getString(R.string.upload_cannot_access_folder))
                }
                return@withContext
            }

            val tempZipFile = File(context.cacheDir, "upload_${System.currentTimeMillis()}.zip")

            try {
                val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: throw Exception(
                    context.getString(R.string.upload_cannot_access_folder)
                )
                val folderName = rootDoc.name ?: "Folder"

                withContext(Dispatchers.Main) {
                    callbacks.onShowProgress(
                        context.getString(R.string.upload_zip_compressing),
                        folderName,
                        context.getString(R.string.upload_zip_packaging)
                    )
                }

                // NÉN ZIP TẠI ĐIỆN THOẠI
                ZipOutputStream(FileOutputStream(tempZipFile)).use { zos ->
                    val queue = ArrayDeque<Pair<DocumentFile, String>>()
                    queue.add(Pair(rootDoc, ""))
                    while (queue.isNotEmpty()) {
                        if (callbacks.onGetCancelFlag()) break
                        val (currentDoc, currentRelativePath) = queue.removeFirst()
                        currentDoc.listFiles().forEach { child ->
                            val childName = child.name ?: "unknown"
                            val entryPath =
                                if (currentRelativePath.isEmpty()) childName else "$currentRelativePath/$childName"
                            if (child.isDirectory) {
                                queue.add(Pair(child, entryPath))
                            } else {
                                zos.putNextEntry(ZipEntry(entryPath))
                                context.contentResolver.openInputStream(child.uri)
                                    ?.use { it.copyTo(zos) }
                                zos.closeEntry()
                            }
                        }
                    }
                }

                if (callbacks.onGetCancelFlag()) throw Exception(context.getString(R.string.upload_cancelled))
                delay(200)

                val zipSize = tempZipFile.length()
                val pcPath =
                    if (pcTargetBasePath.endsWith("/")) pcTargetBasePath + folderName else "$pcTargetBasePath/$folderName"
                val cmd = "uploadfolder_zip $pcPath|$zipSize\n"

                withContext(Dispatchers.Main) {
                    callbacks.onShowProgress(
                        context.getString(R.string.upload_zip_uploading),
                        folderName,
                        context.getString(R.string.upload_zip_sending),
                        zipSize
                    )
                }

                // TIẾN HÀNH TRUYỀN TẢI MẠNG ĐÃ ĐỒNG BỘ UI
                FileInputStream(tempZipFile).use { fis ->
                    var isServerUnzippingStarted = false

                    performCoreUpload(fis, cmd, zipSize, expectFinalAck = true) { sentBytes ->
                        if (sentBytes == -1L) {
                            // 🌟 PHA 3: SERVER BÁO ĐANG GIẢI NÉN
                            if (!isServerUnzippingStarted) {
                                isServerUnzippingStarted = true
                                withContext(Dispatchers.Main) {
                                    callbacks.onShowProgress(
                                        context.getString(R.string.upload_unzipping_title),
                                        folderName,
                                        context.getString(R.string.upload_unzipping_status)
                                    )
                                    callbacks.onUpdateProgress(-1f, context.getString(R.string.upload_processing_disk))
                                    callbacks.onUpdateTransferProgress(-1f)
                                }
                            }
                        } else {
                            // 🌟 PHA 2: ĐANG BƠM BYTE DỮ LIỆU LÊN MẠNG
                            val progress = sentBytes.toFloat() / zipSize
                            withContext(Dispatchers.Main) {
                                callbacks.onUpdateProgress(
                                    progress,
                                    "${callbacks.formatSize(sentBytes)} / ${callbacks.formatSize(zipSize)}"
                                )
                                callbacks.onUpdateTransferProgress(progress)
                            }
                        }
                    }
                }

                // HA HẠ MÀN THÀNH CÔNG
                delay(50)
                withContext(Dispatchers.Main) {
                    callbacks.onHideProgress()
                    callbacks.onEmitEvent(context.getString(R.string.upload_zip_success))
                    delay(300)
                    callbacks.onFetchFiles()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callbacks.onHideProgress()
                    callbacks.onEmitEvent(
                        context.getString(
                            R.string.upload_folder_error,
                            e.message ?: "Unknown"
                        )
                    )
                }
            } finally {
                tempZipFile.delete()
            }
        }

}