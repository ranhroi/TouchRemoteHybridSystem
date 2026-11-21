package com.ranhroi.touchremote.ui.screens.server

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ranhroi.touchremote.R
import com.ranhroi.touchremote.models.DeviceServerModel

/**
 * ServerItem — Composable dùng chung cho cả Saved Servers và Found Servers.
 *
 * Đặc điểm:
 *  - Phân biệt BLE / WiFi tự động qua `server.bluetooth`
 *  - Hiển thị tên + badge + địa chỉ (+ port nếu TCP) + OS
 *  - Có indicator bên phải: check (selected) / arrow (không)
 *  - Hỗ trợ long click (cho dropdown menu)
 *  - Có slot `content` để thêm dropdown, icon, v.v.
 *
 * @param server Device server cần hiển thị
 * @param isSelected Có đang được chọn không
 * @param showOsLabel Hiển thị OS label (dùng cho Saved Servers)
 * @param showCheckIcon Hiển thị check icon khi selected (thay vì arrow)
 * @param onSelect Callback khi nhấn
 * @param onLongSelect Callback khi nhấn giữ
 * @param content Slot để thêm nội dung (dropdown menu, v.v.)
 */
@Composable
fun ServerItem(
    modifier: Modifier = Modifier,
    networkStatus: NetworkStatusViewModel? = null,
    isWifiOn: Boolean? = null,
    isBluetoothOn: Boolean? = null,
    server: DeviceServerModel,
    isSelected: Boolean = false,
    showOsLabel: Boolean = true,
    showCheckIcon: Boolean = true,
    onSelect: () -> Unit = {},
    onLongSelect: () -> Unit = {},
    content: @Composable (() -> Unit) = {},
) {
    val isBle = server.bluetooth

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .combinedClickable(
                onClick = {
                    if (networkStatus == null && isBluetoothOn == null && isWifiOn == null) {
                        onSelect()
                        return@combinedClickable
                    }

                    if (isBle) {
                        if (isBluetoothOn == true) {
                            onSelect()
                        } else {
                            networkStatus?.checkBleAndRequest()
                        }
                    } else {
                        if (isWifiOn == true) {
                            onSelect()
                        } else {
                            networkStatus?.checkWifiAndRequest()
                        }
                    }
                },
                onLongClick = onLongSelect
            ),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        ),
        border = if (isSelected && showCheckIcon) {
            BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ============================================================
            // ICON — Phân biệt BLE / TCP
            // ============================================================
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = if (isBle)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(
                        if (isWifiOn == null && isBluetoothOn == null) {
                            if (isBle) R.drawable.baseline_bluetooth else R.drawable.baseline_wifi
                        } else if (isBle) {
                            if (isBluetoothOn == true) R.drawable.baseline_bluetooth else R.drawable.baseline_bluetooth_disabled
                        } else {
                            if (isWifiOn == true) R.drawable.baseline_wifi else R.drawable.baseline_wifi_off
                        }
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (isBle)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.tertiary
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // ============================================================
            // INFO — Tên + Badge + Địa chỉ + OS
            // ============================================================
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                // Dòng 1: Tên + Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = server.name.ifBlank {
                            stringResource(R.string.server_unknown_name)
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isSelected)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // Badge BLE / WiFi
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isBle)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (isBle)
                                    stringResource(R.string.server_badge_ble)
                                else
                                    stringResource(R.string.server_badge_wifi),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isBle)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Dòng 2: Địa chỉ + OS
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (isBle)
                            server.address
                        else
                            "${server.address}: ${server.port}",
                        fontSize = 11.sp,
                        color = if (isSelected)
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (showOsLabel) {
                        Text(
                            text = "•",
                            fontSize = 11.sp,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )

                        Text(
                            text = server.os,
                            fontSize = 11.sp,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // ============================================================
            // INDICATOR — Check (selected) / Arrow (không)
            // ============================================================
            if (isSelected && showCheckIcon) {
                Icon(
                    painter = painterResource(R.drawable.baseline_check_circle),
                    contentDescription = "Selected",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.baseline_arrow_forward),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }

            // Content slot (dropdown, v.v.)
            content()
        }
    }
}