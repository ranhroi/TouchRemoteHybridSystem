package com.ranhroi.touchremote.ui.pager.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ranhroi.touchremote.R
import com.ranhroi.touchremote.network.UploadForegroundService
import com.ranhroi.touchremote.ui.screens.server.ConnectionManager
import com.ranhroi.touchremote.ui.screens.server.ConnectionViewModel
import com.ranhroi.touchremote.models.DeviceServerModel
import com.ranhroi.touchremote.ui.screens.server.HybridConnectionModel
import com.ranhroi.touchremote.utils.ConstantsUtils.PATH_FILE_PC
import com.ranhroi.touchremote.utils.DataStoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Stack
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
class FileExplorerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStoreManager,
    private val connection: ConnectionManager,
) : ViewModel() {

    companion object {
        private var globalPathStack = Stack<String>()
    }

    private val _shouldKeepScreenOn = MutableStateFlow(false)
    val shouldKeepScreenOn: StateFlow<Boolean> = _shouldKeepScreenOn.asStateFlow()

    private val _files = MutableStateFlow<List<FileModel>>(emptyList())
    val files: StateFlow<List<FileModel>> = _files.asStateFlow()

    private val _currentPath = MutableStateFlow("/")
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _progressDialog = MutableStateFlow(ProgressDialogStateModel())
    val progressDialog: StateFlow<ProgressDialogStateModel> = _progressDialog.asStateFlow()

    private val _fileOperationEvent = MutableSharedFlow<String>()
    val fileOperationEvent = _fileOperationEvent.asSharedFlow()

    private val _detailsFile = MutableStateFlow(DetailsFileModel())
    val detailsFile: StateFlow<DetailsFileModel> = _detailsFile.asStateFlow()

    private val _isTransferPaused = MutableStateFlow(false)
    val isTransferPaused: StateFlow<Boolean> = _isTransferPaused.asStateFlow()

    private val _isTransferring = MutableStateFlow(false)
    val isTransferring: StateFlow<Boolean> = _isTransferring.asStateFlow()

    private val _transferProgress = MutableStateFlow(0f)
    val transferProgress: StateFlow<Float> = _transferProgress.asStateFlow()

    private val _transferState = MutableStateFlow(HybridConnectionModel())
    val transferState: StateFlow<HybridConnectionModel> = _transferState.asStateFlow()

    private val _showExtractDialog = MutableStateFlow<ExtractRequestModel?>(null)
    val showExtractDialog: StateFlow<ExtractRequestModel?> = _showExtractDialog.asStateFlow()

    private val pathStack get() = globalPathStack


    private val updateLimiter = SimpleUpdateLimiter()
    private var lastNotificationProgress = -1

    private var downloadJob: Job? = null
    private var uploadJob: Job? = null
    private var fetchExplorerJob: Job? = null
    private var downloadHandler: FileDownloadHandler? = null
    private var uploadHandler: FileUploadHandler? = null
    private var operationsHandler: FileOperationsHandler? = null

    // ✅ Đếm số transfer đang chạy để quản lý service
    private var activeTransferCount = 0

    @Volatile
    private var cancelFlag = false

    private var currentDownloadFile = ""
    private var currentDownloadSize = 0L
    private var currentDownloadOffset = 0L

    // ✅ Lưu thông tin upload hiện tại để cập nhật notification
    private var currentUploadFile = ""
    private var currentUploadProgress = 0
    private var currentUploadSpeed = ""


    // ==================== SERVICE HELPERS (CHỈ 3 HÀM) ====================

    private fun sendToService(action: String, title: String = "", content: String = "") {
        if (!UploadForegroundService.isRunning) return
        val intent = Intent(context, UploadForegroundService::class.java).apply {
            this.action = action
            putExtra("title", title)
            putExtra("content", content)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun ensureServiceRunning() {
        if (UploadForegroundService.isRunning) return
        val intent = Intent(context, UploadForegroundService::class.java).apply {
            action = UploadForegroundService.ACTION_START
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stopServiceIfNeeded() {
        if (activeTransferCount > 0) return
        if (!UploadForegroundService.isRunning) return
        sendToService(UploadForegroundService.ACTION_STOP)
    }

    // ==================== UPDATE NOTIFICATION ====================

    private fun updateNotification(title: String, content: String) {
        sendToService(UploadForegroundService.ACTION_UPDATE, title, content)
    }

    // ==================== GET TRANSFER STATE ====================

    fun getTransferState(): HybridConnectionModel {
        return connection.getConnectionState(ConnectionManager.ROLE_TRANSFER)
    }

    fun isTransferAlive(): Boolean {
        val state = getTransferState()
        return state.isConnected && state.input != null && state.output != null
    }

    fun reconnectTransfer(server: DeviceServerModel) {
        viewModelScope.launch {
            connection.connect(server, ConnectionManager.ROLE_TRANSFER)
        }
    }

    fun disconnectTransfer() {
        connection.close(ConnectionManager.ROLE_TRANSFER)
    }

    fun initConnection(state: HybridConnectionModel) {
        if (state.isConnected && state.input != null && state.output != null) {
            _transferState.value = state
            initHandlers()
            initPath()
            fetchFiles()
        }
    }

    private fun initHandlers() {
        val state = _transferState.value

        if (!state.isConnected || state.input == null || state.output == null) {
            return
        }

        downloadHandler = FileDownloadHandler(
            context, state,
            object : FileDownloadHandler.DownloadCallbacks {
                override suspend fun getSavedProgress(fileName: String) =
                    this@FileExplorerViewModel.getSavedProgress(fileName)

                override suspend fun clearSavedProgress(fileName: String) =
                    deleteDownloadProgressByFileName(fileName)

                override fun onShowProgress(title: String, name: String, msg: String, size: Long) =
                    showProgress(title, name, msg, size)

                override fun onUpdateProgress(progress: Float, detail: String) {
                    updateProgress(progress, detail)
                    val percent = (progress * 100).toInt()

                    if (updateLimiter.shouldUpdate(percent)) {
                        updateNotification(
                            "📥 Đang download: ${currentDownloadFile.takeLast(20)}",
                            "Tiến độ: $percent% • $detail"
                        )
                    }
                }

                override fun onHideProgress() = hideProgress()
                override fun onEmitEvent(msg: String) = emitEvent(msg)
                override fun onGetPath() = getPath()
                override fun onGetCancelFlag() = cancelFlag
                override fun onUpdateCurrentDownload(name: String, offset: Long, size: Long) {
                    currentDownloadFile = name
                    currentDownloadOffset = offset
                    currentDownloadSize = size
                }

                override fun onSaveCurrentProgress() = saveCurrentProgress()
                override fun formatFileSize(size: Long) =
                    this@FileExplorerViewModel.formatFileSize(size)

                override suspend fun onReadLineFromSocket(input: InputStream) =
                    readLineFromSocket(input)

                override suspend fun onReadBinarySize(input: InputStream) = readBinarySize(input)

                override suspend fun ensureTransferConnection(): Boolean {
                    return (_transferState.value.output != null) && (_transferState.value.input != null)
                }

                override suspend fun askExtractOrKeepZip(
                    folderName: String,
                    zipPath: String
                ): Boolean {
                    return suspendCancellableCoroutine { continuation ->
                        _showExtractDialog.value = ExtractRequestModel(
                            folderName = folderName,
                            zipPath = zipPath,
                            onResult = { shouldExtract ->
                                continuation.resume(shouldExtract)
                                _showExtractDialog.value = null
                            }
                        )
                    }
                }

                override fun onTransferComplete() {
                    stopTransfer()
                }
            })

        uploadHandler = FileUploadHandler(
            context, state,
            object : FileUploadHandler.UploadCallbacks {
                override fun onShowProgress(title: String, name: String, msg: String, size: Long) =
                    showProgress(title, name, msg, size)

                override fun onUpdateProgress(percent: Float, detail: String) {
                    updateProgress(percent, detail)
                    val progressPercent = (percent * 100).toInt()

                    if (updateLimiter.shouldUpdate(progressPercent)) {
                        // ✅ CẬP NHẬT ĐƠN GIẢN
                        updateNotification(
                            "📤 Đang upload: ${currentUploadFile.takeLast(20)}",
                            "Tiến độ: $progressPercent% • $detail"
                        )
                    }
                }

                override fun onHideProgress() = hideProgress()
                override fun onEmitEvent(msg: String) = emitEvent(msg)
                override fun onGetPath() = this@FileExplorerViewModel.getPath()
                override fun onGetCancelFlag() = cancelFlag
                override suspend fun onFetchFiles() = fetchFiles()
                override fun onUpdateTransferProgress(progress: Float) {
                    _transferProgress.value = progress
                }

                override fun formatSize(size: Long) = formatFileSize(size)

                override suspend fun ensureTransferConnection(): Boolean {
                    return (_transferState.value.output != null) && (_transferState.value.input != null)
                }

                override fun onTransferComplete() {
                    stopTransfer()
                }
            })

        operationsHandler = FileOperationsHandler(
            context, state,
            object : FileOperationsHandler.FileOpsCallbacks {
                override fun getPath() = this@FileExplorerViewModel.getPath()
                override suspend fun fetchFiles() = this@FileExplorerViewModel.fetchFiles()
                override fun emitEvent(msg: String) = this@FileExplorerViewModel.emitEvent(msg)
                override fun updateDetailsFile(update: (DetailsFileModel) -> DetailsFileModel) {
                    _detailsFile.update(update)
                }

                override fun formatFileSize(size: Long) =
                    this@FileExplorerViewModel.formatFileSize(size)

                override suspend fun readLineFromSocket(input: InputStream) =
                    this@FileExplorerViewModel.readLineFromSocket(input)

                override val viewModelScope = this@FileExplorerViewModel.viewModelScope
                override val fileOperationEvent = this@FileExplorerViewModel._fileOperationEvent
                override val detailsFile = this@FileExplorerViewModel._detailsFile
            })
    }

    override fun onCleared() {
        super.onCleared()
        fullReset()
        stopServiceIfNeeded()
    }

    // ==================== TRANSFER TASKS WITH CONNECTION CHECK ====================

    private fun startDownloadTask(block: suspend () -> Unit) {
        // ✅ Nếu đang cancel thì reset flag
        if (cancelFlag) cancelFlag = false

        // ✅ KIỂM TRA KẾT NỐI TRƯỚC KHI BẮT ĐẦU
        if (!isTransferReady()) {
            viewModelScope.launch {
                _fileOperationEvent.emit("Mất kết nối, vui lòng thử lại")
            }
            return
        }

        updateLimiter.reset()

        // ✅ HỦY FETCH JOB ĐANG CHẠY (nếu có) để tránh xung đột
        fetchExplorerJob?.cancel()

        downloadJob?.cancel()
        _isTransferring.value = true
        _shouldKeepScreenOn.value = true
        downloadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                block()
            } finally {
                _isTransferring.value = false
                _shouldKeepScreenOn.value = false

                if (!cancelFlag) {
                    updateNotification("✅ Hoàn thành", "Đã tải xuống thành công!")
                    delay(1500.milliseconds)
                    updateNotification("📤 Sẵn sàng", "Đang chờ lệnh...")
                }

                // ✅ FETCH SAU KHI HOÀN THÀNH (CHỈ KHI KHÔNG BỊ CANCEL)
                if (!cancelFlag && isTransferReady()) {
                    // ✅ ĐỢI SERVER XỬ LÝ XONG
                    delay(500.milliseconds)
                    // ✅ KIỂM TRA LẠI TRƯỚC KHI FETCH
                    if (!cancelFlag && isTransferReady() && !_isTransferring.value) {
                        fetchFiles()
                    }
                }
            }
        }
    }

    private fun startUploadTask(block: suspend () -> Unit) {
        // ✅ Nếu đang cancel thì reset flag
        if (cancelFlag) cancelFlag = false

        // ✅ KIỂM TRA KẾT NỐI TRƯỚC KHI BẮT ĐẦU
        if (!isTransferReady()) {
            viewModelScope.launch {
                _fileOperationEvent.emit("Mất kết nối, vui lòng thử lại")
            }
            return
        }
        updateLimiter.reset()

        // ✅ HỦY FETCH JOB ĐANG CHẠY (nếu có) để tránh xung đột
        fetchExplorerJob?.cancel()

        uploadJob?.cancel()
        _isTransferring.value = true
        _shouldKeepScreenOn.value = true
        uploadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                block()
            } finally {
                _isTransferring.value = false
                _shouldKeepScreenOn.value = false
                currentUploadFile = ""
                currentUploadProgress = 0
                currentUploadSpeed = ""

                if (!cancelFlag) {
                    updateNotification("✅ Hoàn thành", "Đã tải lên thành công!")
                    delay(1500.milliseconds)
                    updateNotification("📤 Sẵn sàng", "Đang chờ lệnh...")
                }

                // ✅ FETCH SAU KHI HOÀN THÀNH (CHỈ KHI KHÔNG BỊ CANCEL)
                if (!cancelFlag && isTransferReady()) {
                    // ✅ ĐỢI SERVER XỬ LÝ XONG
                    delay(500.milliseconds)
                    // ✅ KIỂM TRA LẠI TRƯỚC KHI FETCH
                    if (!cancelFlag && isTransferReady() && !_isTransferring.value) {
                        fetchFiles()
                    }
                }
            }
        }
    }

    // ==================== DOWNLOAD PROGRESS DATASTORE ====================
    private suspend fun saveDownloadProgress(progress: DownloadProgressModel) {
        deleteDownloadProgressByFileName(progress.fileName)
        dataStore.saveDownloadProgress(progress)
    }

    private suspend fun getAllDownloadProgress(): List<DownloadProgressModel> {
        return dataStore.getAllDownloadProgress().first()
    }

    private suspend fun deleteDownloadProgressByFileName(fileName: String) {
        dataStore.deleteDownloadProgressByFileName(fileName)
    }

    private suspend fun clearAllDownloadProgress() {
        dataStore.clearAllDownloadProgress()
    }

    private suspend fun getDownloadProgressByFileName(fileName: String): DownloadProgressModel? {
        return getAllDownloadProgress().find { it.fileName == fileName }
    }

    private suspend fun saveProgress(fileName: String, fileSize: Long, offset: Long) {
        val progress = DownloadProgressModel(
            fileName = fileName,
            fileSize = fileSize,
            offset = offset,
            totalChunks = (fileSize / (1024 * 1024)).toInt(),
            lastModified = System.currentTimeMillis()
        )
        saveDownloadProgress(progress)
    }

    private suspend fun getSavedProgress(fileName: String): DownloadProgressModel? {
        return getDownloadProgressByFileName(fileName)
    }

    suspend fun clearSavedProgress(fileName: String) {
        deleteDownloadProgressByFileName(fileName)
    }

    private fun saveCurrentProgress() {
        if (currentDownloadFile.isEmpty()) return
        if (currentDownloadOffset <= 0) return
        if (currentDownloadOffset >= currentDownloadSize) return

        viewModelScope.launch(Dispatchers.IO) {
            deleteDownloadProgressByFileName(currentDownloadFile)
            saveProgress(currentDownloadFile, currentDownloadSize, currentDownloadOffset)
        }
    }

    suspend fun getPendingDownloadsCount(): Int {
        return getAllDownloadProgress().size
    }

    suspend fun getPendingDownloadsList(): List<DownloadProgressModel> {
        return getAllDownloadProgress()
    }

    fun clearAllPendingDownloads(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            clearAllDownloadProgress()
            _fileOperationEvent.emit(context.getString(R.string.file_explorer_cleared_history))
        }
    }

    // ==================== DIRECTORY NAVIGATION ====================
    private fun initPath() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val savedPath = dataStore.get(PATH_FILE_PC, "").first()
                if (savedPath.isNotEmpty()) {
                    restorePathStack(savedPath)
                } else {
                    pathStack.push("/")
                    _currentPath.value = "/"
                }
            } catch (e: Exception) {
                pathStack.push("/")
            }
        }
    }

    private fun restorePathStack(path: String) {
        pathStack.clear()
        if (path == "/" || path == "~") {
            pathStack.push(path)
        } else {
            val normalizedPath = path.replace("\\", "/")
            val parts = normalizedPath.split("/").filter { it.isNotBlank() }

            if (normalizedPath.startsWith("~")) {
                pathStack.push("~")
                parts.filter { it != "~" }.forEach { pathStack.push(it) }
            } else {
                pathStack.push("/")
                parts.forEach { pathStack.push(it) }
            }
        }
    }

    fun navigateTo(dir: String) {
        if (dir.startsWith("/") || dir.startsWith("~")) {
            pathStack.clear()
            pathStack.push(dir)
        } else if (dir.contains(":") || dir.startsWith("/") || dir.startsWith("~")) {
            restorePathStack(dir)
        } else {
            pathStack.push(dir)
        }
        updatePath()
        fetchFiles()
    }


    fun goBack() {
        if (pathStack.size > 1) {
            pathStack.pop()
        } else if (pathStack.isNotEmpty() || pathStack.peek() == "~") {
            pathStack.clear()
            pathStack.push("/")
        }

        updatePath()
        fetchFiles()
    }

    fun goHome() {
        pathStack.clear()
        pathStack.push("~")
        updatePath()
        fetchFiles()
    }

// ==================== FETCH FILES WITH ERROR HANDLING ====================

    fun fetchFiles() {
        // ✅ KHÔNG FETCH NẾU ĐANG TRANSFER
        if (_isTransferring.value) {
            return
        }

        val path = getPath()
        fetchExplorerJob?.cancel()
        fetchExplorerJob = viewModelScope.launch(Dispatchers.IO) {
            delay(100.milliseconds)

            // ✅ KIỂM TRA KẾT NỐI
            if (!isTransferReady()) {
                return@launch
            }

            try {
                val handler = FileExplorerHandler(context, path, _transferState.value)
                val result = handler.execute()
                if (result.isEmpty()) {
                    _fileOperationEvent.tryEmit(context.getString(R.string.file_explorer_empty_folder))
                    _files.value = emptyList()
                } else {
                    _files.value = result
                }
            } catch (e: Exception) {
                // ✅ BẮT LỖI VÀ LOG
                e.printStackTrace()
                _fileOperationEvent.tryEmit("Error across the entire list: ${e.message}")
            }
        }
    }

    fun getPath(): String {
        if (pathStack.isEmpty()) return "/"
        val first = pathStack.firstElement()
        if (pathStack.size == 1) return first

        val filesSnapshot = _files.value.toList()
        val fileHome = filesSnapshot.filter { it.name != ".." }

        return when (first) {
            "/" -> if (first.contains(":")) pathStack.drop(1)
                .joinToString("/") else pathStack.joinToString("/") { it }.replace("//", "/")

            "~" -> if (fileHome.isNotEmpty()) "~/" + pathStack.drop(1)
                .joinToString("/") else pathStack.joinToString("/") { it }.replace("//", "/")

            else -> pathStack.joinToString("/") { it }.replace("//", "/")
        }
    }

    private fun updatePath() {
        val newPath = getPath()
        _currentPath.value = newPath
        viewModelScope.launch(Dispatchers.IO) { dataStore.put(PATH_FILE_PC, newPath) }
    }

    // ==================== FILE OPERATIONS (Delegate) ====================
    fun openFile(name: String) {
        operationsHandler?.openFile(name)
    }

    fun copyFile(name: String) {
        operationsHandler?.copyFile(name)
    }

    fun cutFile(name: String) {
        operationsHandler?.cutFile(name)
    }

    fun deleteFile(name: String) {
        operationsHandler?.deleteFile(name)
    }



    fun pasteFile() {
        operationsHandler?.pasteFile()
    }

    fun renameNode(oldName: String, newName: String) {
        operationsHandler?.renameNode(oldName, newName)
    }

    fun createFile(fileName: String) {
        operationsHandler?.createFile(fileName)
    }

    fun createFolder(folderName: String) {
        operationsHandler?.createFolder(folderName)
    }

    fun getFileDetails(name: String) {
        operationsHandler?.getFileDetails(name)
    }

    fun closeDetailsDialog() {
        operationsHandler?.closeDetailsDialog()
    }

    // ==================== TRANSFER (Delegate) ====================
    fun downloadFileToClient(context: Context, name: String, resume: Boolean = true) =
        startDownloadTask { downloadHandler?.downloadFile(context, name, resume) }

    fun downloadFolderToClient(context: Context, serverFolderPath: String) =
        startDownloadTask { downloadHandler?.downloadFolder(context, serverFolderPath) }

    fun downloadFolderToClientZip(context: Context, folderName: String) =
        startDownloadTask { downloadHandler?.downloadFolderZip(context, folderName) }

    fun uploadFileToServer(context: Context, uri: Uri) = startUploadTask {
        uploadHandler?.uploadFile(context, uri)
    }

    fun uploadFolderToServer(context: Context, treeUri: Uri, pcTargetBasePath: String) =
        startUploadTask { uploadHandler?.uploadFolder(context, treeUri, pcTargetBasePath) }

    fun uploadFolderToServerZip(context: Context, treeUri: Uri, pcTargetBasePath: String) =
        startUploadTask { uploadHandler?.uploadFolderZip(context, treeUri, pcTargetBasePath) }

// ==================== TRANSFER CONTROL ====================

    fun cancelImmediately(connection: ConnectionViewModel) {
        cancelFlag = true
        _isTransferring.value = false
        _isTransferPaused.value = false
        _shouldKeepScreenOn.value = false
        _transferState.value = HybridConnectionModel()

        viewModelScope.launch(Dispatchers.IO) {
            if (currentDownloadFile.isNotEmpty()) {
                clearSavedProgress(currentDownloadFile)
                currentDownloadFile = ""
                currentDownloadOffset = 0
                currentDownloadSize = 0
            }

            connection.disconnectRole(ConnectionManager.ROLE_TRANSFER)
            downloadJob?.cancel()
            uploadJob?.cancel()
            fetchExplorerJob?.cancel()

            activeTransferCount = 0
            withContext(Dispatchers.Main) {
                hideProgress()
                _fileOperationEvent.tryEmit(context.getString(R.string.file_explorer_cancelled_emergency))
            }
        }
    }

    fun cancelTransfer(connection: ConnectionViewModel) {
        cancelFlag = true
        _isTransferring.value = false
        _isTransferPaused.value = false
        _shouldKeepScreenOn.value = false
        _transferState.value = HybridConnectionModel()

        viewModelScope.launch(Dispatchers.IO) {
            //saveCurrentProgress()
            connection.disconnectRole(ConnectionManager.ROLE_TRANSFER)
            downloadJob?.cancel()
            uploadJob?.cancel()
            fetchExplorerJob?.cancel()

            activeTransferCount = 0
            withContext(Dispatchers.Main) {
                hideProgress()
                _fileOperationEvent.tryEmit(context.getString(R.string.file_explorer_cancelled_resumable))
            }
        }
    }

    fun stopTransfer() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _transferState.value.output?.write("stop_transfer\n".toByteArray())
                _transferState.value.output?.flush()
            } catch (e: Exception) {
            }

            downloadJob?.cancel()
            uploadJob?.cancel()
            cancelFlag = false
            saveCurrentProgress()

            withContext(Dispatchers.Main) {
                _isTransferring.value = false
                _shouldKeepScreenOn.value = false
                hideProgress()
            }
        }
    }

    fun pauseTransfer(connection: ConnectionViewModel) {
        viewModelScope.launch(Dispatchers.IO) {
            saveCurrentProgress()
            connection.disconnectRole(ConnectionManager.ROLE_TRANSFER)

            withContext(Dispatchers.Main) {
                _isTransferPaused.value = true
                _isTransferring.value = false
                _fileOperationEvent.tryEmit(context.getString(R.string.file_explorer_paused))
            }
        }
    }

    fun resumeTransfer(context: Context, connection: ConnectionViewModel) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _isTransferPaused.value = false
                _isTransferring.value = true
                _fileOperationEvent.tryEmit(context.getString(R.string.file_explorer_reconnecting))
            }

            if (currentDownloadFile.isEmpty()) {
                withContext(Dispatchers.Main) {
                    _isTransferring.value = false
                    _fileOperationEvent.tryEmit(context.getString(R.string.file_explorer_no_resumable_file))
                }
                return@launch
            }

            val existing = connection.getConnectionState(ConnectionManager.ROLE_TRANSFER)

            if (existing == null) {
                connection.pingTransfer()
                var retry = 0
                while (!_transferState.value.isConnected && retry < 20) {
                    delay(100.milliseconds)
                    retry++
                }
            }

            if (isTransferReady()) {
                if (downloadHandler == null) {
                    initHandlers()
                }
                downloadFileToClient(context, currentDownloadFile, resume = true)
            } else {
                withContext(Dispatchers.Main) {
                    _isTransferring.value = false
                    _fileOperationEvent.tryEmit(context.getString(R.string.file_explorer_cannot_reconnect))
                }
            }
        }
    }

    // ==================== UTILITY & BYTE PARSING ====================
    suspend fun readLineFromSocket(input: InputStream): String = withContext(Dispatchers.IO) {
        val bos = ByteArrayOutputStream()
        var byte: Int
        while (true) {
            byte = input.read()
            if (byte == -1 || byte == '\n'.toInt()) break
            if (byte != '\r'.toInt()) bos.write(byte)
            if (bos.size() > 10 * 1024 * 1024) throw Exception(context.getString(R.string.file_explorer_response_too_large))
        }
        bos.toString("UTF-8")
    }

    private suspend fun readExactBytes(
        input: InputStream,
        buffer: ByteArray,
        offset: Int = 0,
        length: Int
    ): Int = withContext(Dispatchers.IO) {
        var bytesRead = 0
        while (bytesRead < length) {
            val read = input.read(buffer, offset + bytesRead, length - bytesRead)
            if (read == -1) throw Exception(context.getString(R.string.file_explorer_unexpected_stream))
            bytesRead += read
        }
        bytesRead
    }

    suspend fun readBinarySize(input: InputStream): Long = withContext(Dispatchers.IO) {
        val sizeBuffer = ByteArray(8)
        readExactBytes(input, sizeBuffer, 0, 8)
        var size = 0L
        for (i in 0 until 8) size = size or ((sizeBuffer[i].toLong() and 0xFF) shl (i * 8))
        size
    }

    fun formatFileSize(size: Long): String = when {
        size >= 1024 * 1024 * 1024 -> String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0))
        size >= 1024 * 1024 -> String.format("%.2f MB", size / (1024.0 * 1024.0))
        size >= 1024 -> String.format("%.2f KB", size / 1024.0)
        else -> "$size B"
    }

    fun showProgress(title: String, name: String, message: String, totalSize: Long = 0) {
        val detail = if (totalSize > 0) formatFileSize(totalSize) else ""
        _progressDialog.value =
            ProgressDialogStateModel(true, title, name, message, detail = detail, progress = 0f)
    }

    fun updateProgress(value: Float, detail: String) {
        _progressDialog.update { it.copy(progress = value, detail = detail) }
    }

    fun hideProgress() {
        _progressDialog.value = ProgressDialogStateModel(show = false)
    }

    private fun emitEvent(msg: String) = viewModelScope.launch { _fileOperationEvent.emit(msg) }

    private fun isTransferReady(): Boolean {
        return _transferState.value.isConnected &&
                _transferState.value.input != null &&
                _transferState.value.output != null
    }

    // ==================== FULL RESET ====================
    fun fullReset() {
        viewModelScope.launch(Dispatchers.IO) {
            stopTransfer()
            downloadJob?.cancel()
            uploadJob?.cancel()
            fetchExplorerJob?.cancel()

            activeTransferCount = 0

            cancelFlag = true
            _isTransferring.value = false
            _isTransferPaused.value = false
            _shouldKeepScreenOn.value = false
            _transferProgress.value = 0f

            currentDownloadFile = ""
            currentDownloadOffset = 0
            currentDownloadSize = 0
            currentUploadFile = ""
            currentUploadProgress = 0
            currentUploadSpeed = ""

            downloadHandler = null
            uploadHandler = null
            operationsHandler = null

            _transferState.value = HybridConnectionModel()
            pathStack.clear()
            _currentPath.value = ""
            _files.value = emptyList()

            withContext(Dispatchers.Main) {
                hideProgress()
                _fileOperationEvent.tryEmit(context.getString(R.string.file_explorer_reset_complete))
            }
        }
    }
}