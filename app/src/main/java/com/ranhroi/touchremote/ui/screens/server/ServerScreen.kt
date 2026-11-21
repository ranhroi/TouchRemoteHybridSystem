package com.ranhroi.touchremote.ui.screens.server

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.ranhroi.touchremote.R
import com.ranhroi.touchremote.models.DeviceServerModel
import com.ranhroi.touchremote.models.DropMenuModel
import com.ranhroi.touchremote.ui.components.DotsLoadingIndicator
import com.ranhroi.touchremote.ui.components.MoreVertDropdown
import com.ranhroi.touchremote.ui.dialogs.ConfirmDialog
import com.ranhroi.touchremote.ui.dialogs.DialogAddNewServer
import com.ranhroi.touchremote.ui.dialogs.DialogAddPorts
import com.ranhroi.touchremote.ui.dialogs.DialogEditServer
import com.ranhroi.touchremote.ui.screens.main.NetworkStatusBar
import com.ranhroi.touchremote.utils.BluetoothPermissionHandler
import com.ranhroi.touchremote.utils.playSoundClick
import com.ranhroi.touchremote.utils.toEntity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@SuppressLint("UnrememberedGetBackStackEntry", "MissingPermission", "AutoboxingStateCreation")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerScreen(
    navController: NavHostController,
    connection: ConnectionViewModel = hiltViewModel(),
    serversControl: ServersControlViewModel = hiltViewModel(),
    networkStatus: NetworkStatusViewModel = hiltViewModel(),
    scanner: ScannerViewModel = hiltViewModel(),
    ports: PortsViewModel = hiltViewModel(),
) {
    val view = LocalView.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isWifiOn by networkStatus.isWifiOn.collectAsState()
    val isBluetoothOn by networkStatus.isBluetoothOn.collectAsState()

    val portsResult by ports.addressPortsModelResult.collectAsState()
    val isLoadingBlt by scanner.isLoadingBlt.collectAsState()
    val isLoadingTcp by scanner.isLoadingTcp.collectAsState()

    val foundServers by serversControl.foundServers.collectAsState()
    val savedServers by serversControl.savedServers.collectAsState()
    val currentSelectedServer by serversControl.currentSelectedServer.collectAsState()

    // ============================================================
    // LOCAL UI STATE
    // ============================================================
    var isVisibleIp by remember { mutableStateOf(false) }
    var isVisiblePorts by remember { mutableStateOf(false) }
    var isVisibleEditPort by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var selectedServer by remember { mutableStateOf<DeviceServerModel?>(null) }
    var showOptionsSettings by remember { mutableStateOf(false) }

    BackHandler(enabled = true) { scope.launch { navController.popBackStack() } }

    // ============================================================
    // BLUETOOTH ENABLE LAUNCHER
    // ============================================================
    val enableBtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { }

// ============================================================
// PERMISSION LAUNCHER — list quyền lấy từ handler
// ============================================================
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            if (isBluetoothOn && !isLoadingBlt) {
                scanner.startScanBlt()
            } else if (!isBluetoothOn) {
                networkStatus.checkBleAndRequest()
            }
        }
    }

    LaunchedEffect(Unit) {
        networkStatus.requestAction.collect { action ->
            when (action) {
                NetworkStatusViewModel.BluetoothAction.REQUEST_PERMISSION -> {
                    // Dùng launcher thay vì ActivityCompat — để nhận kết quả trong Compose
                    permissionLauncher.launch(
                        BluetoothPermissionHandler.getRequiredPermissions()
                    )
                }

                NetworkStatusViewModel.BluetoothAction.REQUEST_ENABLE -> {
                    enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                }

                else -> {}
            }
        }
    }

    // ============================================================
    // COLLECT SCAN RESULTS — MERGE BLE + TCP
    // ============================================================
    LaunchedEffect(Unit) {
        launch {
            scanner.devicesBlt.collectLatest { bleDevices: List<BluetoothDevice> ->
                val currentTcp = scanner.devicesTcp.value
                val filteredBle = bleDevices
                    .filterNot { device ->
                        savedServers.any { saved -> saved.address == device.address }
                    }.toEntity()
                val filteredTcp = currentTcp
                    .filterNot { device ->
                        savedServers.any { saved ->
                            saved.address == device.address && saved.port == device.port
                        }
                    }
                val merged = filteredBle + filteredTcp
                if (merged.isEmpty()) serversControl.clearFoundServers()
                else serversControl.setFoundServers(merged)
            }
        }

        launch {
            scanner.devicesTcp.collectLatest { tcpDevices ->
                val currentBle = scanner.devicesBlt.value
                val filteredTcp = tcpDevices
                    .filterNot { device ->
                        savedServers.any { saved ->
                            saved.address == device.address && saved.port == device.port
                        }
                    }
                val filteredBle = currentBle
                    .filterNot { device ->
                        savedServers.any { saved -> saved.address == device.address }
                    }.toEntity()
                val merged = filteredBle + filteredTcp
                if (merged.isEmpty()) serversControl.clearFoundServers()
                else serversControl.setFoundServers(merged)
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            // Vẫn dùng hasBluetoothPermissions() — không cần đổi:
            // - Android 12+: check đúng BLUETOOTH_SCAN
            // - Android 11-: trả true, stopScanBlt() an toàn (không cần runtime perm)
            if (BluetoothPermissionHandler.hasBluetoothPermissions(context)) {
                scanner.stopScanBlt()
            }
            scanner.stopScanTcp()
            serversControl.clearFoundServers()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.server_title)) },
                navigationIcon = {
                    IconButton(
                        modifier = Modifier
                            .width(70.dp)
                            .height(70.dp),
                        onClick = {
                            scope.launch {
                                if (navController.previousBackStackEntry != null) {
                                    navController.popBackStack()
                                }
                            }
                            view.playSoundClick()
                        }) {
                        Icon(
                            painterResource(R.drawable.baseline_arrow_back),
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            NetworkStatusBar(
                navController, view, connection, networkStatus, isShow = false
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // ===== SAVED SERVERS =====
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    ) {
                        SectionHeader(
                            title = stringResource(R.string.server_saved_title, savedServers.size)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (savedServers.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(70.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.server_click_to_add),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 100.dp, max = 280.dp)
                            ) {
                                itemsIndexed(savedServers) { _, server ->
                                    var isVisibleDrop by remember { mutableStateOf(false) }
                                    ServerItem(
                                        networkStatus = networkStatus,
                                        isWifiOn = isWifiOn,
                                        isBluetoothOn = isBluetoothOn,
                                        server = server,
                                        isSelected = server == currentSelectedServer,
                                        onSelect = {
                                            if (server != currentSelectedServer) {
                                                connection.disconnectAll()
                                                serversControl.setCurrentSelectedServer(server)
                                            }
                                            view.playSoundClick()
                                        },
                                        onLongSelect = {
                                            selectedServer = server
                                            isVisibleDrop = true
                                        }
                                    ) {
                                        MoreVertDropdown(
                                            view = view,
                                            expanded = isVisibleDrop,
                                            onDismiss = { isVisibleDrop = false },
                                            menuItems = listOf(
                                                DropMenuModel(
                                                    text = stringResource(R.string.server_edit_port),
                                                    onClick = {
                                                        selectedServer = server
                                                        isVisibleDrop = false
                                                        isVisibleEditPort = true
                                                    },
                                                    icon = "baseline_edit"
                                                ),
                                                DropMenuModel(
                                                    text = stringResource(R.string.server_remove),
                                                    onClick = {
                                                        selectedServer = server
                                                        isVisibleDrop = false
                                                        showDeleteConfirm = true
                                                    },
                                                    icon = "baseline_delete",
                                                    isDestructive = true
                                                ),
                                            )
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ===== FOUND SERVERS =====
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    ) {
                        SectionHeader(title = stringResource(R.string.server_found_title))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // ============================================================
                            // ✅ NÚT QUÉT BLUETOOTH — đã tích hợp permissionLauncher
                            // ============================================================
                            Button(
                                onClick = {
                                    if (!BluetoothPermissionHandler.hasAllRequiredPermissions(
                                            context
                                        )
                                    ) {
                                        permissionLauncher.launch(
                                            BluetoothPermissionHandler.getRequiredPermissions()
                                        )
                                    } else if (isBluetoothOn) {
                                        if (isLoadingBlt) scanner.stopScanBlt() else scanner.startScanBlt()
                                    } else {
                                        networkStatus.checkBleAndRequest()
                                    }
                                    view.playSoundClick()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isLoadingBlt)
                                        MaterialTheme.colorScheme.error
                                    else
                                        MaterialTheme.colorScheme.primary
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(
                                    painter = painterResource(
                                        if (isLoadingBlt) R.drawable.baseline_stop
                                        else R.drawable.baseline_bluetooth
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isLoadingBlt)
                                        stringResource(R.string.server_scan_ble_stop)
                                    else
                                        stringResource(R.string.server_scan_ble),
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }

                            // Nút QUÉT WI-FI
                            Button(
                                onClick = {
                                    if (isWifiOn) {
                                        if (isLoadingTcp) {
                                            scanner.stopScanTcp()
                                        } else {
                                            scanner.startScanTcp(portsResult)
                                        }
                                    } else {
                                        context.startActivity(
                                            Intent(Settings.ACTION_WIFI_SETTINGS)
                                        )
                                    }
                                    view.playSoundClick()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isLoadingTcp)
                                        MaterialTheme.colorScheme.error
                                    else
                                        MaterialTheme.colorScheme.tertiary
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(
                                    painter = painterResource(
                                        if (isLoadingTcp) R.drawable.baseline_stop
                                        else R.drawable.baseline_wifi
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isLoadingTcp)
                                        stringResource(R.string.server_scan_wifi_stop)
                                    else
                                        stringResource(R.string.server_scan_wifi),
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                        }

                        when {
                            !isBluetoothOn && !isWifiOn -> {
                                EmptyState(
                                    icon = Icons.Default.Warning,
                                    text = stringResource(R.string.server_enable_wifi_bluetooth)
                                )
                            }

                            (isLoadingBlt || isLoadingTcp) && foundServers.isEmpty() -> {
                                LoadingState(
                                    text = when {
                                        isLoadingBlt && isLoadingTcp -> stringResource(R.string.server_scanning_both)
                                        isLoadingBlt -> stringResource(R.string.server_scanning_ble)
                                        else -> stringResource(R.string.server_scanning_wifi)
                                    }
                                )
                            }

                            !isLoadingBlt && !isLoadingTcp && foundServers.isEmpty() -> {
                                EmptyState(
                                    icon = null,
                                    text = stringResource(R.string.server_found_empty_hint)
                                )
                            }

                            foundServers.isNotEmpty() -> {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 80.dp, max = 280.dp)
                                ) {
                                    itemsIndexed(foundServers) { _, server ->
                                        ServerItem(
                                            server = server,
                                            isSelected = server == currentSelectedServer,
                                            onSelect = {
                                                serversControl.addSavedServer(server)
                                                serversControl.removeFoundServer(server)
                                                view.playSoundClick()
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // BOTTOM BAR
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = stringResource(R.string.settings_title),
                            fontStyle = FontStyle.Italic,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FloatingActionButton(
                            onClick = {
                                showOptionsSettings = !showOptionsSettings
                                view.playSoundClick()
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                painterResource(R.drawable.baseline_settings),
                                contentDescription = null
                            )
                            MoreVertDropdown(
                                view = view,
                                expanded = showOptionsSettings,
                                onDismiss = { showOptionsSettings = false },
                                menuItems = listOf(
                                    DropMenuModel(
                                        text = stringResource(R.string.server_set_ports_scanner),
                                        onClick = {
                                            isVisiblePorts = true
                                            showOptionsSettings = false
                                        },
                                        icon = "outline_electrical_services"
                                    ),
                                    DropMenuModel(
                                        text = stringResource(R.string.server_manual_add),
                                        onClick = {
                                            isVisibleIp = true
                                            showOptionsSettings = false
                                        },
                                        icon = "baseline_add"
                                    ),
                                )
                            )
                        }
                    }
                }
            }

            // DIALOGS
            if (isVisibleIp) {
                DialogAddNewServer(
                    context = context,
                    ports = portsResult,
                    onAddNewServer = { server -> serversControl.setFoundServers(listOf(server)) },
                    onDismiss = { isVisibleIp = false }
                )
            }

            if (isVisiblePorts) {
                DialogAddPorts(
                    context = context,
                    ports = ports,
                    onDismiss = { isVisiblePorts = false }
                )
            }

            if (isVisibleEditPort && selectedServer != null) {
                DialogEditServer(
                    context = context,
                    serverControl = serversControl,
                    server = selectedServer!!,
                    onDismiss = {
                        isVisibleEditPort = false
                        selectedServer = null
                    }
                )
            }

            if (showDeleteConfirm && selectedServer != null) {
                ConfirmDialog(
                    show = showDeleteConfirm,
                    title = stringResource(R.string.server_confirm_delete_title),
                    message = stringResource(
                        R.string.server_confirm_delete_message,
                        selectedServer?.name ?: ""
                    ),
                    onConfirm = {
                        serversControl.removeSavedServer(selectedServer!!)
                        connection.disconnectAll()
                        showDeleteConfirm = false
                        selectedServer = null
                    },
                    onDismiss = {
                        showDeleteConfirm = false
                        selectedServer = null
                    }
                )
            }
        }
    }
}

// ============================================================
// SECTION HEADER
// ============================================================
@Composable
private fun SectionHeader(title: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        Divider(thickness = 1.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    }
}

// ============================================================
// LOADING STATE
// ============================================================
@Composable
private fun LoadingState(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DotsLoadingIndicator()
            Text(
                text = text,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

// ============================================================
// EMPTY STATE
// ============================================================
@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    text: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}