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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.ranhroi.touchremote.ui.screens.server.PortsViewModel
import com.ranhroi.touchremote.utils.playSoundClick
import com.ranhroi.touchremote.utils.showToast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogAddPorts(
    context: Context,
    ports: PortsViewModel,
    onDismiss: () -> Unit = {},
) {
    val view = LocalView.current
    val portsResult by ports.addressPortsModelResult.collectAsState()
    var state by remember { mutableStateOf(portsResult) }

    var portScanText by remember { mutableStateOf(state.portScan.toString()) }
    var portConnectionText by remember { mutableStateOf(state.portConnection.toString()) }

    AlertDialog(
        modifier = Modifier
            .defaultMinSize()
            .padding(horizontal = 10.dp),
        shape = RoundedCornerShape(8.dp),
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painterResource(R.drawable.baseline_settings_input_svideo),
                    contentDescription = null,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
                Text(
                    text = stringResource(R.string.dialog_ports_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = portScanText,
                    onValueChange = {
                        portScanText = it
                        state = state.copy(portScan = it.toIntOrNull() ?: 0)
                    },
                    label = { Text(stringResource(R.string.dialog_ports_scanner_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = portScanText.isNotBlank() && portScanText.toIntOrNull() == null
                )

                OutlinedTextField(
                    value = portConnectionText,
                    onValueChange = {
                        portConnectionText = it
                        state = state.copy(portConnection = it.toIntOrNull() ?: 0)
                    },
                    label = { Text(stringResource(R.string.dialog_ports_connection_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = portConnectionText.isNotBlank() && portConnectionText.toIntOrNull() == null
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val valid = listOf(
                        portScanText.toIntOrNull(),
                        portConnectionText.toIntOrNull()
                    ).all { it != null }
                    if (valid) {
                        ports.updatePorts(state)
                        onDismiss()
                    } else {
                        context.showToast(context.getString(R.string.dialog_ports_invalid))
                    }
                    view.playSoundClick()
                }, shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.dialog_ports_save))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                view.playSoundClick()
                onDismiss()
            }) {
                Text(stringResource(R.string.dialog_ports_cancel))
            }
        }
    )
}