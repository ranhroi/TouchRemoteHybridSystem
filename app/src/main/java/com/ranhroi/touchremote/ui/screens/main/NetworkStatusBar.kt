package com.ranhroi.touchremote.ui.screens.main

import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.ranhroi.touchremote.R
import com.ranhroi.touchremote.ui.screens.server.HybridConnectionModel
import com.ranhroi.touchremote.ui.navigation.NavRoute
import com.ranhroi.touchremote.ui.dialogs.DialogVerifyPassword
import com.ranhroi.touchremote.ui.screens.server.ConnectionManager
import com.ranhroi.touchremote.ui.screens.server.ConnectionViewModel
import com.ranhroi.touchremote.ui.screens.server.NetworkStatusViewModel
import com.ranhroi.touchremote.utils.playSoundClick

@Composable
fun NetworkStatusBar(
    navController: NavHostController,
    view: View,
    connection: ConnectionViewModel,
    networkStatus: NetworkStatusViewModel,
    isShow: Boolean = false,
) {
    val uiState by connection.uiState.collectAsStateWithLifecycle()
    val connections by connection.connections.collectAsStateWithLifecycle()

    val isWifiOn by networkStatus.isWifiOn.collectAsStateWithLifecycle()
    val isBluetoothOn by networkStatus.isBluetoothOn.collectAsStateWithLifecycle()
    val currentServer by connection.getSelectedDevice.collectAsStateWithLifecycle()

    val showPasswordDialog = uiState.showPasswordDialog
    val statusMessage = uiState.statusMessage
    val errorMessage = uiState.errorMessage
    val isConnected = uiState.isConnected
    val isLoading = uiState.isLoading

    // ===== LẤY TRẠNG THÁI CÁC ROLE =====
    val commandState = connections[ConnectionManager.ROLE_COMMAND] ?: HybridConnectionModel()
    val streamState = connections[ConnectionManager.ROLE_STREAM] ?: HybridConnectionModel()
    val transferState = connections[ConnectionManager.ROLE_TRANSFER] ?: HybridConnectionModel()

    // ===== STATUS TYPE =====
    val statusType = when {
        errorMessage != null && !isConnected -> StatusType.ERROR
        !isWifiOn && !isBluetoothOn -> StatusType.NO_NETWORK
        isLoading -> StatusType.LOADING
        showPasswordDialog && isConnected -> StatusType.NEED_PASSWORD
        !isConnected -> StatusType.DISCONNECTED
        else -> StatusType.CONNECTED
    }

    // ===== STATUS COLOR =====
    val statusColor = when (statusType) {
        StatusType.ERROR -> Color(0xFFD32F2F)
        StatusType.NO_NETWORK -> Color(0xFFE57373)
        StatusType.NEED_PASSWORD -> Color(0xFFFFA726)
        StatusType.LOADING -> Color(0xFF42A5F5)
        StatusType.DISCONNECTED -> Color(0xFFE57373)
        StatusType.CONNECTED -> Color(0xFF4CAF50)
    }

    // ===== STATUS TEXT CHI TIẾT =====
    val statusText = when (statusType) {
        StatusType.ERROR -> errorMessage.toString()
        StatusType.NO_NETWORK -> stringResource(R.string.network_status_no_network)
        StatusType.NEED_PASSWORD -> stringResource(
            R.string.network_status_need_password,
            currentServer.name
        )

        StatusType.LOADING -> {
            if (statusMessage.isNotEmpty()) statusMessage
            else stringResource(R.string.network_status_loading)
        }

        StatusType.DISCONNECTED -> {
            // Hiển thị role nào đang mất kết nối
            val disconnectedRoles = mutableListOf<String>()
            if (!commandState.isConnected) disconnectedRoles.add("Command")
            if (!streamState.isConnected) disconnectedRoles.add("Stream")
            if (!transferState.isConnected) disconnectedRoles.add("Transfer")

            if (disconnectedRoles.isNotEmpty()) {
                "Mất kết nối: ${disconnectedRoles.joinToString(", ")}"
            } else {
                stringResource(R.string.network_status_disconnected)
            }
        }

        StatusType.CONNECTED -> {
            // Hiển thị role nào đang kết nối
            val connectedRoles = mutableListOf<String>()
            if (commandState.isConnected) connectedRoles.add("Command")
            if (streamState.isConnected) connectedRoles.add("Stream")
            if (transferState.isConnected) connectedRoles.add("Transfer")

            if (connectedRoles.isNotEmpty()) {
                "Đã kết nối: ${connectedRoles.joinToString(", ")}"
            } else {
                stringResource(R.string.network_status_connected)
            }
        }
    }

    // ===== SUB TEXT (chi tiết server) =====
    val subText = when {
        isLoading -> null
        isConnected && commandState.isConnected -> {
            val server = commandState.server
            "${server.name} (${if (commandState.isBluetooth) "Bluetooth" else "WiFi"})"
        }

        else -> null
    }

    // ===== VISIBILITY =====
    val visible = (statusType != StatusType.CONNECTED) && isShow

    // ===== NETWORK CHECK =====
    LaunchedEffect(isShow, isWifiOn, isBluetoothOn) {
        if (isShow) {
            connection.checkNetwork(isWifiOn, isBluetoothOn)
        }
    }

    // ===== ICON =====
    val icon =
        if (uiState.state.isBluetooth) R.drawable.baseline_bluetooth else R.drawable.baseline_wifi

    // ===== STATUS BAR =====
    AnimatedVisibility(
        modifier = Modifier.zIndex(3f),
        visible = visible,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (subText != null) 64.dp else 56.dp)
                .background(statusColor)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ===== LEFT: ICON + TEXT =====
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (statusType == StatusType.LOADING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        painterResource(icon),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color.White
                    )
                }

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = statusText,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (subText != null) {
                        Text(
                            text = subText,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                        )
                    }
                }
            }

            // ===== RIGHT: ACTION BUTTON =====
            when (statusType) {
                StatusType.NO_NETWORK -> {
                    Button(
                        onClick = {
                            networkStatus.checkWifiAndRequest()
                            view.playSoundClick()
                        },
                        shape = RoundedCornerShape(3.dp)
                    ) {
                        Text(stringResource(R.string.network_button_turn_on))
                    }
                }

                StatusType.DISCONNECTED -> {
                    Button(
                        onClick = {
                            navController.navigate(NavRoute.SetServer.route)
                            view.playSoundClick()
                        },
                        shape = RoundedCornerShape(3.dp)
                    ) {
                        Text(stringResource(R.string.network_button_choose_server))
                    }
                }

                StatusType.ERROR -> {
                    Button(
                        onClick = {
                            currentServer.let {
                                if (!isLoading) connection.connectToServer(it)
                            }
                            view.playSoundClick()
                        },
                        shape = RoundedCornerShape(3.dp)
                    ) {
                        Text(stringResource(R.string.network_button_reconnect))
                    }
                }

                StatusType.NEED_PASSWORD -> {
                    Button(
                        onClick = {
                            // Mở dialogs nhập mật khẩu
                            view.playSoundClick()
                        },
                        shape = RoundedCornerShape(3.dp)
                    ) {
                        Text(stringResource(R.string.network_button_enter_password))
                    }
                }

                else -> {}
            }
        }
    }

    // ===== PASSWORD DIALOG =====
    if (showPasswordDialog && isShow) {
        DialogVerifyPassword(
            view = view,
            server = currentServer,
            connection = connection,
            onDismiss = {
                connection.dismissPasswordDialog()
            }
        )
    }
}