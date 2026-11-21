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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ranhroi.touchremote.R
import com.ranhroi.touchremote.models.DeviceServerModel
import com.ranhroi.touchremote.ui.screens.server.ServersControlViewModel
import com.ranhroi.touchremote.utils.ConstantsUtils
import com.ranhroi.touchremote.utils.isValidUUID
import com.ranhroi.touchremote.utils.playSoundClick
import com.ranhroi.touchremote.utils.showToast

@Composable
fun DialogEditServer(
    context: Context,
    serverControl: ServersControlViewModel,
    server: DeviceServerModel,
    onDismiss: () -> Unit = {},
) {
    val view = LocalView.current

    // States
    var rememberPass by remember { mutableStateOf(server.rememberPass) }
    var port by remember { mutableStateOf(server.port.toString()) }
    var name by remember { mutableStateOf(server.name) }
    var address by remember { mutableStateOf(server.address) }
    var password by remember { mutableStateOf(server.password) }
    var uuid by remember { mutableStateOf(server.uuid) }

    // Logic kiểm tra nhanh để hiển thị lỗi trên TextField
    val isUuidError = server.bluetooth && uuid.isNotEmpty() && !uuid.isValidUUID()
    val isPortError = port.toIntOrNull() == null

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
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.dialog_edit_server_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Icon(painterResource(if (server.bluetooth) R.drawable.baseline_bluetooth else R.drawable.baseline_wifi),
                    contentDescription = null,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = port,
                    onValueChange = { if (it.all { char -> char.isDigit() }) port = it },
                    label = { Text(stringResource(R.string.dialog_edit_port_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = isPortError,
                    supportingText = { if (isPortError) Text(stringResource(R.string.dialog_edit_port_error)) }
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.dialog_edit_name_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(stringResource(R.string.dialog_edit_address_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (server.bluetooth) {
                    OutlinedTextField(
                        value = uuid,
                        onValueChange = { uuid = ConstantsUtils.formatUuid(it) },
                        label = { Text(stringResource(R.string.dialog_edit_uuid_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = isUuidError,
                        supportingText = {
                            if (isUuidError) Text(stringResource(R.string.dialog_edit_uuid_error))
                        }
                    )
                }

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.dialog_edit_password_label)) },
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
                    Text(text = stringResource(R.string.dialog_edit_remember_password))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val portInt = port.toIntOrNull()

                    when {
                        portInt == null -> context.showToast(context.getString(R.string.dialog_edit_port_invalid))
                        name.isBlank() -> context.showToast(context.getString(R.string.dialog_edit_name_empty))
                        address.isBlank() -> context.showToast(context.getString(R.string.dialog_edit_address_empty))
                        server.bluetooth && !uuid.isValidUUID() -> {
                            context.showToast(context.getString(R.string.dialog_edit_uuid_invalid))
                        }
                        else -> {
                            val editServer = server.copy(
                                port = portInt,
                                name = name,
                                address = address,
                                uuid = uuid,
                                password = password,
                                rememberPass = rememberPass && password.isNotEmpty()
                            )
                            serverControl.setServer(editServer)
                            onDismiss()
                        }
                    }
                    view.playSoundClick()
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.dialog_edit_save))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                view.playSoundClick()
                onDismiss()
            }) {
                Text(stringResource(R.string.dialog_edit_cancel))
            }
        }
    )
}