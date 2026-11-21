package com.ranhroi.touchremote.ui.pager.files

import android.content.Context
import com.ranhroi.touchremote.R
import com.ranhroi.touchremote.ui.screens.server.HybridConnectionModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.InputStream

class FileOperationsHandler(
    private val context: Context,
    private val hybridState: HybridConnectionModel,
    private val callbacks: FileOpsCallbacks
) {
    interface FileOpsCallbacks {
        fun getPath(): String
        suspend fun fetchFiles()
        fun emitEvent(msg: String): Job
        fun updateDetailsFile(update: (DetailsFileModel) -> DetailsFileModel)
        fun formatFileSize(size: Long): String
        suspend fun readLineFromSocket(input: InputStream): String
        val viewModelScope: kotlinx.coroutines.CoroutineScope
        val fileOperationEvent: MutableSharedFlow<String>
        val detailsFile: MutableStateFlow<DetailsFileModel>
    }

    // ==================== FILE OPERATIONS ====================
    fun openFile(name: String): Boolean {
        return sendBasicCommand(
            "openfile",
            name,
            context.getString(R.string.fileop_open_success),
            context.getString(R.string.fileop_open_failed)
        )
    }

    fun copyFile(name: String): Boolean {
        return sendBasicCommand(
            "copyfile",
            name,
            context.getString(R.string.fileop_copy_success),
            context.getString(R.string.fileop_copy_failed)
        )
    }

    fun cutFile(name: String): Boolean {
        return sendBasicCommand(
            "cutfile",
            name,
            context.getString(R.string.fileop_cut_success),
            context.getString(R.string.fileop_cut_failed)
        )
    }

    fun deleteFile(name: String): Boolean {
        return sendBasicCommand(
            "deletefile",
            name,
            context.getString(R.string.fileop_delete_success),
            context.getString(R.string.fileop_delete_failed),
            true
        )
    }

    fun pasteFile(): Boolean {
        var result = false
        callbacks.viewModelScope.launch(Dispatchers.IO) {
            try {
                hybridState.output?.write("pastefile ${callbacks.getPath()}\n".toByteArray())
                hybridState.output?.flush()
                if (callbacks.readLineFromSocket(hybridState.input!!) == "paste_success") {
                    result = true
                    callbacks.emitEvent(context.getString(R.string.fileop_paste_success)); callbacks.fetchFiles()
                    callbacks.fetchFiles()
                } else {
                    result = false
                    callbacks.emitEvent(context.getString(R.string.fileop_paste_failed))
                }
            } catch (e: Exception) {
                result = false
                callbacks.emitEvent(
                    context.getString(
                        R.string.fileop_error_prefix,
                        e.message ?: "Unknown"
                    )
                )
            }
        }
        return result
    }

    fun renameNode(oldName: String, newName: String): Boolean {
        var result = false
        callbacks.viewModelScope.launch(Dispatchers.IO) {
            try {
                hybridState.output?.write("renamefile ${callbacks.getPath()}/$oldName|$newName\n".toByteArray())
                hybridState.output?.flush()
                if (callbacks.readLineFromSocket(hybridState.input!!) == "rename_success") {
                    result = true
                    callbacks.emitEvent(context.getString(R.string.fileop_rename_success, newName))
                    callbacks.fetchFiles()
                } else {
                    result = false
                    callbacks.emitEvent(context.getString(R.string.fileop_rename_failed))
                }
            } catch (e: Exception) {
                result = false
            }
        }
        return result
    }

    fun createFile(fileName: String): Boolean {
        var result = false
        callbacks.viewModelScope.launch(Dispatchers.IO) {
            try {
                hybridState.output?.write("createfile ${callbacks.getPath()}/$fileName\n".toByteArray())
                hybridState.output?.flush()
                val response = callbacks.readLineFromSocket(hybridState.input!!)
                when (response) {
                    "mkfile_success" -> {
                        result = true
                        callbacks.emitEvent(
                            context.getString(
                                R.string.fileop_create_file_success,
                                fileName
                            )
                        )
                        callbacks.fetchFiles()
                    }

                    "mkfile_exists" -> {
                        result = false
                        callbacks.emitEvent(
                            context.getString(
                                R.string.fileop_create_file_exists,
                                fileName
                            )

                        )
                    }

                    else -> {
                        result = false
                        callbacks.emitEvent(context.getString(R.string.fileop_create_file_failed))
                    }
                }
            } catch (e: Exception) {
                result = false
            }
        }
        return result
    }

    fun createFolder(folderName: String): Boolean {
        var result = false
        callbacks.viewModelScope.launch(Dispatchers.IO) {
            try {
                hybridState.output?.write("createfolder ${callbacks.getPath()}/$folderName\n".toByteArray())
                hybridState.output?.flush()
                if (callbacks.readLineFromSocket(hybridState.input!!) == "mkdir_success") {
                    result = true
                    callbacks.emitEvent(
                        context.getString(
                            R.string.fileop_create_folder_success,
                            folderName
                        )
                    ); callbacks.fetchFiles()
                } else {
                    result = false
                    callbacks.emitEvent(context.getString(R.string.fileop_create_folder_failed))
                }
            } catch (e: Exception) {
                result = false
            }
        }
        return result
    }

    fun getFileDetails(name: String): Boolean {
        var result = false
        callbacks.viewModelScope.launch(Dispatchers.IO) {
            try {
                val fullPath = "${callbacks.getPath()}/$name"
                hybridState.output?.write("detailsfile $fullPath\n".toByteArray())
                hybridState.output?.flush()
                val response = callbacks.readLineFromSocket(hybridState.input!!)

                if (response.isNotEmpty() && response.contains("|")) {
                    result = true
                    val parts = response.split("|")
                    val nameVal = parts.getOrNull(0)?.substringAfter("name:") ?: name
                    val sizeVal = parts.getOrNull(1)?.substringAfter("size:") ?: "0"
                    val dateVal = parts.getOrNull(2)?.substringAfter("date:") ?: ""
                    val isDirectory = sizeVal.toLongOrNull() == 0L && !nameVal.contains(".")
                    val formattedDate = try {
                        dateVal.replace("T", " ").substringBefore(".")
                    } catch (e: Exception) {
                        dateVal
                    }

                    callbacks.updateDetailsFile {
                        it.copy(
                            isShow = true, name = nameVal,
                            size = if (isDirectory) context.getString(R.string.fileop_details_folder) else callbacks.formatFileSize(
                                sizeVal.toLongOrNull() ?: 0
                            ),
                            date = formattedDate.ifEmpty { context.getString(R.string.fileop_details_no_info) },
                            path = fullPath, isDirectory = isDirectory
                        )
                    }
                } else callbacks.emitEvent(context.getString(R.string.fileop_details_failed))
            } catch (e: Exception) {
                result = false
            }
        }
        return result
    }

    fun closeDetailsDialog() {
        callbacks.updateDetailsFile { it.copy(isShow = false) }
    }

    private fun sendBasicCommand(
        cmd: String,
        name: String,
        successMsg: String,
        failMsg: String,
        fetchAfter: Boolean = false
    ): Boolean {
        var result = false
        callbacks.viewModelScope.launch(Dispatchers.IO) {
            try {
                val fullPath = "${callbacks.getPath()}/$name"
                hybridState.output?.write("$cmd $fullPath\n".toByteArray())
                hybridState.output?.flush()
                val response = callbacks.readLineFromSocket(hybridState.input!!)

                // ✅ XỬ LÝ LỖI TẬP TRUNG
                when {
                    ErrorHandler.isErrorResponse(response) -> {
                        callbacks.emitEvent(
                            ErrorHandler.handleServerError(
                                response,
                                context
                            ).message ?: "Unknown"
                        )
                    }

                    response.contains("success") -> {
                        result = true
                        callbacks.emitEvent("$successMsg: $name")
                    }

                    else -> {
                        callbacks.emitEvent(failMsg)
                    }
                }

                if (response.contains("success") && fetchAfter) callbacks.fetchFiles()
            } catch (e: Exception) {
                callbacks.emitEvent(
                    context.getString(
                        R.string.fileop_error_prefix,
                        e.message ?: "Unknown"
                    )
                )
            }
        }
        return result
    }
}