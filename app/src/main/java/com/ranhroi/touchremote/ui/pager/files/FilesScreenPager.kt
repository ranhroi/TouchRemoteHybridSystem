package com.ranhroi.touchremote.ui.pager.files

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ranhroi.touchremote.R
import com.ranhroi.touchremote.models.DropMenuModel
import com.ranhroi.touchremote.models.ShortKeyModel
import com.ranhroi.touchremote.ui.components.BadgedIcon
import com.ranhroi.touchremote.ui.components.MoreVertDropdown
import com.ranhroi.touchremote.ui.dialogs.ConfirmDialog
import com.ranhroi.touchremote.ui.dialogs.FileDetailsDialog
import com.ranhroi.touchremote.ui.dialogs.InputTextDialog
import com.ranhroi.touchremote.ui.dialogs.ProgressDialog
import com.ranhroi.touchremote.ui.screens.main.SelectAppState
import com.ranhroi.touchremote.ui.screens.server.ConnectionManager
import com.ranhroi.touchremote.ui.screens.server.ConnectionViewModel
import com.ranhroi.touchremote.ui.screens.server.HybridConnectionModel
import com.ranhroi.touchremote.utils.ConstantsUtils
import com.ranhroi.touchremote.utils.getStringSafe
import com.ranhroi.touchremote.utils.playSoundClick
import com.ranhroi.touchremote.utils.showToast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@SuppressLint("MutableCollectionMutableState")
@Composable
fun FilesScreenPager(
    selectState: SelectAppState,
    shortKeys: List<ShortKeyModel>,
    connection: ConnectionViewModel,
    fileExplorerViewModel: FileExplorerViewModel = hiltViewModel()
) {
    val view = LocalView.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    //  Lắng nghe tất cả connections để lấy TRANSFER state
    val allConnections by connection.connections.collectAsStateWithLifecycle()
    val transferState = allConnections[ConnectionManager.ROLE_TRANSFER] ?: HybridConnectionModel()

    val files by fileExplorerViewModel.files.collectAsState()
    val path by fileExplorerViewModel.currentPath.collectAsState()

    val progressDialog by fileExplorerViewModel.progressDialog.collectAsState()
    val detailsFile by fileExplorerViewModel.detailsFile.collectAsState()
    val extractRequest by fileExplorerViewModel.showExtractDialog.collectAsState()

    val isTransferring by fileExplorerViewModel.isTransferring.collectAsState()
    val isTransferPaused by fileExplorerViewModel.isTransferPaused.collectAsState()
    val transferProgress by fileExplorerViewModel.transferProgress.collectAsState()

    // Biến để lưu trạng thái hiển thị dialogs xóa riêng
    var isExpandedResumeMenu by remember { mutableStateOf(false) }
    var isShowClearPendingDialog by remember { mutableStateOf(false) }
    var isShowResumeDialog by remember { mutableStateOf(false) }
    var pendingCount by remember { mutableIntStateOf(0) }
    var pendingList by remember { mutableStateOf<List<DownloadProgressModel>>(emptyList()) }

    val shouldKeepScreenOn by fileExplorerViewModel.shouldKeepScreenOn.collectAsState()
    val folder = shortKeys.filter { it.type == "folder" }

    var isShowCreateFileAndFolderDialog by remember { mutableStateOf(false) }
    var isExpandedTopbarMenu by remember { mutableStateOf(false) }
    var isShowRenameDialog by remember { mutableStateOf(false) }
    var createTargetAction by remember { mutableStateOf<String?>(null) }
    var clipboardAction by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<FileModel?>(null) }

    // Permission handling (Android 13+ Notification post permission)
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasNotificationPermission = isGranted
            if (isGranted) {
                context.showToast(context.getStringSafe(R.string.files_notification_approved))
            } else {
                context.showToast(context.getStringSafe(R.string.files_notification_denied))
            }
        }
    )

    // Trigger permission requests
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val pickFileLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                scope.launch {
                    fileExplorerViewModel.uploadFileToServer(context, it)
                }
            }
        }

    val pickFolderLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            uri?.let { selectedUri ->
                if (path == "/" || path == "~") {
                    context.showToast(context.getStringSafe(R.string.files_select_drive_first))
                } else {
                    fileExplorerViewModel.uploadFolderToServer(context, selectedUri, path)
                }
            }
        }

    val pickFolderZipLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            uri?.let { selectedUri ->
                if (path == "/" || path == "~") {
                    context.showToast(context.getStringSafe(R.string.files_select_drive_first))
                } else {
                    fileExplorerViewModel.uploadFolderToServerZip(context, selectedUri, path)
                }
            }
        }


    BackHandler(
        enabled = path != "/" && path != "~"
    ) {
        fileExplorerViewModel.goBack()
        view.playSoundClick()
    }

    LaunchedEffect(Unit) {
        launch { fileExplorerViewModel.fileOperationEvent.collect { context.showToast(it) } }
    }

    // Lắng nghe toàn bộ transferState
    LaunchedEffect(transferState) {
        if (transferState.isConnected && transferState.input != null && transferState.output != null) {
            fileExplorerViewModel.initConnection(transferState)
        } else {
            fileExplorerViewModel.fullReset()
        }
    }

    LaunchedEffect(isShowResumeDialog, transferState.isConnected) {
        if (transferState.isConnected) {
            delay(1000.milliseconds)
            pendingCount = fileExplorerViewModel.getPendingDownloadsCount()
            if (pendingCount > 0) {
                pendingList = fileExplorerViewModel.getPendingDownloadsList()
            }
        }
    }

    LaunchedEffect(shouldKeepScreenOn) {
        val activity = view.context as? Activity
        if (shouldKeepScreenOn) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    if (extractRequest != null) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.files_download_complete)) },
            text = {
                Column {
                    Text(stringResource(R.string.files_zip_downloaded, extractRequest!!.folderName))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.files_zip_question), fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        view.playSoundClick()
                        extractRequest!!.onResult(true)
                    }
                ) {
                    Text(stringResource(R.string.files_extract))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        view.playSoundClick()
                        extractRequest!!.onResult(false)
                    }
                ) {
                    Text(stringResource(R.string.files_keep_zip))
                }
            }
        )
    }

    if (progressDialog.show) {
        ProgressDialog(
            title = progressDialog.title,
            fileName = progressDialog.name,
            progress = progressDialog.progress,
            detail = progressDialog.detail.toString(),
            isPaused = isTransferPaused,
            onPause = { fileExplorerViewModel.pauseTransfer(connection) },
            onResume = { fileExplorerViewModel.resumeTransfer(context, connection) },
            onCancel = { fileExplorerViewModel.cancelTransfer(connection) },
            onCancelImmediate = { fileExplorerViewModel.cancelImmediately(connection) }
        )
    }

    if (isShowResumeDialog && pendingList.isNotEmpty() && transferState.isConnected) {
        var selectedFile by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = {
                isShowResumeDialog = false
            },
            title = { Text(stringResource(R.string.files_resume_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = stringResource(R.string.files_resume_detected, pendingList.size),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    pendingList.forEach { progress ->
                        val isSelected = selectedFile == progress.fileName

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    view.playSoundClick()
                                    selectedFile = if (isSelected) null else progress.fileName
                                },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(if (isSelected) R.drawable.baseline_check_circle else R.drawable.baseline_radio_button_checked),
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray
                                )

                                Text(
                                    text = progress.fileName,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp)
                                )

                                Text(
                                    text = "${fileExplorerViewModel.formatFileSize(progress.offset)} / ${
                                        fileExplorerViewModel.formatFileSize(
                                            progress.fileSize
                                        )
                                    }",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (selectedFile != null) {
                    TextButton(
                        onClick = {
                            view.playSoundClick()
                            fileExplorerViewModel.downloadFileToClient(context, selectedFile!!)
                            scope.launch {
                                fileExplorerViewModel.clearSavedProgress(selectedFile!!)
                            }
                            isShowResumeDialog = false
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.files_resume_download, selectedFile!!),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        view.playSoundClick()
                        isShowResumeDialog = false
                        fileExplorerViewModel.clearAllPendingDownloads(context)
                    }
                ) {
                    Text(stringResource(R.string.files_resume_skip_all), color = Color.Red)
                }
            }
        )
    }

    if (isShowClearPendingDialog) {
        ConfirmDialog(
            show = isShowClearPendingDialog,
            title = stringResource(R.string.files_clear_history),
            message = stringResource(R.string.files_clear_confirm, pendingCount),
            onConfirm = {
                fileExplorerViewModel.clearAllPendingDownloads(context)
                pendingCount = 0
                isShowClearPendingDialog = false
            },
            onDismiss = { isShowClearPendingDialog = false }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            if (selectState.tabIndex != 4) {
                fileExplorerViewModel.stopServiceIfNeeded()
                fileExplorerViewModel.fullReset()
                connection.disconnectRole(ConnectionManager.ROLE_TRANSFER)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Progress bar khi transfer
        if (isTransferring) {
            LinearProgressIndicator(
                progress = { transferProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
            )
        }

        // ===== HEADER CHÍNH =====
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            tonalElevation = 3.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (!transferState.isConnected) Color.Red.copy(alpha = 0.2f) else Color.Transparent)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    enabled = !isTransferring && transferState.isConnected,
                    onClick = {
                        view.playSoundClick()
                        fileExplorerViewModel.goBack()
                    }
                ) {
                    Icon(
                        painterResource(R.drawable.baseline_arrow_back),
                        contentDescription = stringResource(R.string.files_back)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                val titleText = when (path) {
                    "/" -> stringResource(R.string.files_drives)
                    "~" -> stringResource(R.string.files_ShortKeys)
                    else -> path.substringAfterLast("/")
                }

                Text(
                    text = titleText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            view.playSoundClick()
                            connection.pingTransfer()
                        }
                    ) {
                        Icon(
                            painterResource(R.drawable.baseline_refresh),
                            contentDescription = stringResource(R.string.files_repeat)
                        )
                    }

                    if (pendingCount > 0) {
                        Box {
                            IconButton(
                                onClick = {
                                    isExpandedResumeMenu = true
                                    view.playSoundClick()
                                },
                            ) {
                                BadgedIcon(
                                    badge = {
                                        Text(
                                            text = "$pendingCount",
                                            fontSize = 10.sp,
                                            color = Color.White
                                        )
                                    },
                                    // Thay đổi thông số lệch nếu muốn dịch chuyển badge xa/gần hơn
                                    badgeOffset = Pair(6.dp, (-6).dp),
                                    badgeBackgroundColor = Color(0xFFE53935)
                                ) {
                                    // Đặt icon vào trong Box cố định size để icon không bị méo hay lệch tâm
                                    Box(
                                        modifier = Modifier.size(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.baseline_system_update_alt),
                                            contentDescription = stringResource(R.string.files_pending_downloads),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                            }

                            if (isExpandedResumeMenu) {
                                MoreVertDropdown(
                                    view = view,
                                    expanded = isExpandedResumeMenu,
                                    onDismiss = { isExpandedResumeMenu = false },
                                    menuItems = listOf(
                                        DropMenuModel(
                                            text = stringResource(
                                                R.string.files_resume_view_list,
                                                pendingCount
                                            ),
                                            onClick = {
                                                isExpandedResumeMenu = false
                                                isShowResumeDialog = true
                                            },
                                            icon = "baseline_list_alt",
                                        ),
                                        DropMenuModel(
                                            text = stringResource(R.string.files_resume_all),
                                            onClick = {
                                                isExpandedResumeMenu = false
                                                scope.launch {
                                                    pendingList.forEach { progress ->
                                                        fileExplorerViewModel.downloadFileToClient(
                                                            context,
                                                            progress.fileName
                                                        )
                                                        delay(500.milliseconds)
                                                    }
                                                }
                                            },
                                            icon = "baseline_download",
                                        ),
                                        DropMenuModel(
                                            text = stringResource(R.string.files_clear_all_pending),
                                            onClick = {
                                                isExpandedResumeMenu = false
                                                isShowClearPendingDialog = true
                                            },
                                            icon = "baseline_delete",
                                            isDestructive = true,
                                        )
                                    )
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = {
                            view.playSoundClick()
                            fileExplorerViewModel.goHome()
                        },
                        enabled = transferState.isConnected
                    ) {
                        Icon(
                            painterResource(R.drawable.baseline_home),
                            contentDescription = stringResource(R.string.files_home)
                        )
                    }

                    IconButton(
                        onClick = {
                            view.playSoundClick()
                            fileExplorerViewModel.fetchFiles()
                        },
                        enabled = transferState.isConnected
                    ) {
                        Icon(
                            painterResource(R.drawable.baseline_import_export),
                            contentDescription = stringResource(R.string.files_refresh)
                        )
                    }

                    if (clipboardAction != null) {
                        IconButton(
                            onClick = {
                                view.playSoundClick()
                                fileExplorerViewModel.pasteFile()
                                clipboardAction = null
                            },
                            enabled = transferState.isConnected
                        ) {
                            Icon(
                                painterResource(R.drawable.outline_content_paste),
                                contentDescription = stringResource(R.string.files_paste)
                            )
                        }
                    }

                    Box {
                        IconButton(
                            onClick = {
                                view.playSoundClick()
                                isExpandedTopbarMenu = true
                            },
                            enabled = transferState.isConnected
                        ) {
                            Icon(
                                painterResource(R.drawable.baseline_more_vert),
                                contentDescription = stringResource(R.string.files_add),
                                tint = if (transferState.isConnected) Color.White else Color.Gray
                            )
                        }
                        if (isExpandedTopbarMenu) {
                            MoreVertDropdown(
                                view = view,
                                expanded = isExpandedTopbarMenu,
                                onDismiss = { isExpandedTopbarMenu = false },
                                menuItems = listOf(
                                    DropMenuModel(
                                        text = stringResource(R.string.files_new_file),
                                        onClick = {
                                            createTargetAction = "File"
                                            isExpandedTopbarMenu = false
                                            isShowCreateFileAndFolderDialog = true
                                        },
                                        icon = "baseline_insert_drive_file"
                                    ),
                                    DropMenuModel(
                                        text = stringResource(R.string.files_new_folder),
                                        onClick = {
                                            createTargetAction = "Folder"
                                            isExpandedTopbarMenu = false
                                            isShowCreateFileAndFolderDialog = true
                                        },
                                        icon = "baseline_create_new_folder"
                                    ),
                                    DropMenuModel(
                                        text = stringResource(R.string.files_upload_file),
                                        onClick = {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            } else {
                                                fileExplorerViewModel.ensureServiceRunning()
                                                pickFileLauncher.launch("*/*")
                                            }
                                        },
                                        icon = "baseline_file_upload"
                                    ),
                                    DropMenuModel(
                                        text = stringResource(R.string.files_upload_folder),
                                        onClick = {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            } else {
                                                fileExplorerViewModel.ensureServiceRunning()
                                                pickFolderLauncher.launch(null)
                                            }
                                        },
                                        icon = "baseline_drive_folder_upload"
                                    ),
                                    DropMenuModel(
                                        text = stringResource(R.string.files_upload_folder_zip),
                                        onClick = {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            } else {
                                                fileExplorerViewModel.ensureServiceRunning()
                                                pickFolderZipLauncher.launch(null)
                                            }
                                        },
                                        icon = "baseline_folder_zip"
                                    )
                                )
                            )
                        }
                    }
                }
            }
        }

        if (transferState.isBluetooth) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(0.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.LightGray.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(0.dp),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 10.dp)
                            .fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painterResource(R.drawable.baseline_warning),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Text(
                            textAlign = TextAlign.Start,
                            text = stringResource(R.string.files_bt_warning),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }


        // ===== DANH SÁCH =====
        LazyVerticalGrid(
            columns = GridCells.Fixed(1),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            if (path == "/") {
                val displayFiles = files.filter { it.name != ".." }
                if (displayFiles.isNotEmpty()) {
                    itemsIndexed(displayFiles) { _, node ->
                        FileItem(
                            view = view,
                            icon = "ic_folder",
                            node = node,
                            isBack = true,
                            onNode = {
                                fileExplorerViewModel.openFile(node.name)
                            },
                            onRename = { renameTarget = node; isShowRenameDialog = true },
                            onDelete = { fileExplorerViewModel.deleteFile(node.name) },
                            onDownload = {
                                if (node.isDirectory) {
                                    fileExplorerViewModel.downloadFolderToClient(
                                        context,
                                        "${fileExplorerViewModel.getPath()}/${node.name}"
                                    )
                                } else {
                                    fileExplorerViewModel.downloadFileToClient(context, node.name)
                                }
                            },
                            onDownloadZip = {
                                if (node.isDirectory) {
                                    fileExplorerViewModel.downloadFolderToClientZip(
                                        context,
                                        node.name
                                    )
                                }
                            },
                            onDetails = { fileExplorerViewModel.getFileDetails(node.name) },
                            onClick = {
                                if (node.isDirectory) {
                                    fileExplorerViewModel.navigateTo(node.name)
                                } else {
                                    fileExplorerViewModel.openFile(node.name)
                                }
                            }
                        )
                    }
                }
            } else if (path == "~") {
                val displayFiles = files.filter { it.name != ".." }
                if (displayFiles.isNotEmpty()) {
                    itemsIndexed(displayFiles) { _, node ->
                        FileItem(
                            view = view,
                            icon = "ic_folder",
                            node = node,
                            isBack = true,
                            onClick = {
                                fileExplorerViewModel.navigateTo("~/" + node.name)
                            },
                        )
                    }
                } else {
                    itemsIndexed(folder) { _, shortKey ->
                        FileItem(
                            view = view,
                            icon = shortKey.icon,
                            node = FileModel(shortKey.name, true),
                            isBack = true,
                            onClick = {
                                val sysIdx =
                                    ConstantsUtils.getSystemInfoInt(transferState.os)
                                if (sysIdx != -1) {
                                    fileExplorerViewModel.navigateTo(shortKey.keys[sysIdx].first())
                                }
                            }
                        )
                    }
                }
            } else {
                val displayFiles = files.filter { it.name != ".." }
                itemsIndexed(displayFiles) { _, node ->
                    FileItem(
                        view = view,
                        icon = if (node.isDirectory) "ic_folder" else "ic_file",
                        node = node,
                        isBack = false,
                        onNode = {
                            fileExplorerViewModel.openFile(node.name)
                        },
                        onCopy = {
                            fileExplorerViewModel.copyFile(node.name)
                            clipboardAction = "copy"
                        },
                        onCut = {
                            fileExplorerViewModel.cutFile(node.name)
                            clipboardAction = "cut"
                        },
                        onRename = { renameTarget = node; isShowRenameDialog = true },
                        onDelete = { fileExplorerViewModel.deleteFile(node.name) },
                        onDownload = {
                            if (node.isDirectory) {
                                fileExplorerViewModel.downloadFolderToClient(
                                    context,
                                    "${fileExplorerViewModel.getPath()}/${node.name}"
                                )
                            } else {
                                fileExplorerViewModel.downloadFileToClient(context, node.name)
                            }
                        },
                        onDownloadZip = {
                            if (node.isDirectory) {
                                fileExplorerViewModel.downloadFolderToClientZip(context, node.name)
                            }
                        },
                        onDetails = { fileExplorerViewModel.getFileDetails(node.name) },
                        onClick = {
                            if (node.isDirectory) {
                                fileExplorerViewModel.navigateTo(node.name)
                            } else {
                                fileExplorerViewModel.openFile(node.name)
                            }
                        }
                    )
                }
            }
        }
    }

    if (isShowCreateFileAndFolderDialog && createTargetAction != null) {
        val isFolder = createTargetAction == "Folder"
        InputTextDialog(
            show = isShowCreateFileAndFolderDialog,
            title = if (isFolder) stringResource(R.string.files_create_folder) else stringResource(R.string.files_create_file),
            hint = stringResource(R.string.files_enter_name),
            onConfirm = { name ->
                if (name.isNotBlank()) {
                    if (isFolder) fileExplorerViewModel.createFolder(name) else fileExplorerViewModel.createFile(
                        name
                    )
                }
                isShowCreateFileAndFolderDialog = false
                createTargetAction = null
            },
            onDismiss = { isShowCreateFileAndFolderDialog = false; createTargetAction = null }
        )
    }

    if (isShowRenameDialog && renameTarget != null) {
        InputTextDialog(
            show = isShowRenameDialog,
            title = stringResource(R.string.files_rename_title),
            hint = stringResource(R.string.files_new_name),
            initialText = renameTarget?.name ?: "",
            onConfirm = { newName ->
                if (newName.isNotBlank()) {
                    fileExplorerViewModel.renameNode(renameTarget!!.name, newName)
                }
                isShowRenameDialog = false
                renameTarget = null
            },
            onDismiss = { isShowRenameDialog = false; renameTarget = null }
        )
    }

    if (detailsFile.isShow) {
        FileDetailsDialog(
            details = detailsFile,
            onDismiss = { fileExplorerViewModel.closeDetailsDialog() }
        )
    }
}