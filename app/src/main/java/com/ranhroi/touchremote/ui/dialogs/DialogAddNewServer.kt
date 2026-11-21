package com.ranhroi.touchremote.ui.dialogs

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ranhroi.touchremote.R
import com.ranhroi.touchremote.models.DeviceServerModel
import com.ranhroi.touchremote.ui.screens.server.AddressPortsModel
import com.ranhroi.touchremote.utils.ConstantsUtils
import com.ranhroi.touchremote.utils.isValidUUID
import com.ranhroi.touchremote.utils.playSoundClick
import com.ranhroi.touchremote.utils.showToast

@Composable
fun DialogAddNewServer(
    context: Context,
    ports: AddressPortsModel,
    onAddNewServer: (DeviceServerModel) -> Unit,
    onDismiss: () -> Unit = {},
) {
    val view = LocalView.current

    // States
    var port by remember { mutableStateOf(ports.portConnection.toString()) }
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var uuid by remember { mutableStateOf(ConstantsUtils.DEFAULT_SERVER_UUID) }
    var rememberPass by remember { mutableStateOf(false) }
    var isBluetooth by remember { mutableStateOf(false) }

    // Validation States để hiển thị lỗi trực quan (Red Border)
    val isPortError = port.toIntOrNull() == null
    val isUuidError = isBluetooth && uuid.isNotEmpty() && !uuid.isValidUUID()

    AlertDialog(
        modifier = Modifier
            .defaultMinSize()
            .padding(horizontal = 10.dp),
        shape = RoundedCornerShape(8.dp),
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(if (isBluetooth) R.drawable.baseline_bluetooth else R.drawable.baseline_wifi),
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = stringResource(R.string.dialog_add_server_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        modifier = Modifier.scale(0.7f),
                        checked = isBluetooth,
                        onCheckedChange = { isBluetooth = it }
                    )
                    Text(
                        text = if (isBluetooth) stringResource(R.string.dialog_add_server_bt) else stringResource(R.string.dialog_add_server_wifi),
                        fontSize = 10.sp,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = port,
                    onValueChange = { if (it.all { c -> c.isDigit() }) port = it },
                    label = { Text(stringResource(R.string.dialog_port_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = isPortError,
                    supportingText = { if (isPortError) Text(stringResource(R.string.dialog_port_error)) }
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.dialog_name_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(if (isBluetooth) stringResource(R.string.dialog_mac_label) else stringResource(R.string.dialog_address_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (isBluetooth) {
                    OutlinedTextField(
                        value = uuid,
                        onValueChange = { uuid = ConstantsUtils.formatUuid(it) },
                        label = { Text(stringResource(R.string.dialog_uuid_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = isUuidError,
                        supportingText = {
                            if (isUuidError) Text(stringResource(R.string.dialog_uuid_error))
                        }
                    )
                }

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.dialog_password_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = rememberPass,
                        onCheckedChange = { rememberPass = it }
                    )
                    Text(text = stringResource(R.string.dialog_remember_password), fontSize = 14.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val portInt = port.toIntOrNull()

                    when {
                        portInt == null -> context.showToast(context.getString(R.string.dialog_port_invalid))
                        name.isBlank() -> context.showToast(context.getString(R.string.dialog_name_empty))
                        address.isBlank() -> context.showToast(context.getString(R.string.dialog_address_empty))
                        isBluetooth && !uuid.isValidUUID() -> {
                            context.showToast(context.getString(R.string.dialog_uuid_invalid))
                        }
                        else -> {
                            onAddNewServer(
                                DeviceServerModel(
                                    port = portInt,
                                    name = name,
                                    uuid = uuid,
                                    address = address,
                                    password = password,
                                    bluetooth = isBluetooth,
                                    rememberPass = rememberPass && password.isNotEmpty()
                                )
                            )
                            onDismiss()
                        }
                    }
                    view.playSoundClick()
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.dialog_button_add))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                view.playSoundClick()
                onDismiss()
            }) {
                Text(stringResource(R.string.dialog_button_cancel))
            }
        }
    )
}