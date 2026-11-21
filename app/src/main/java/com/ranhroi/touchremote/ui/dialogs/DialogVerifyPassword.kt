package com.ranhroi.touchremote.ui.dialogs

import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ranhroi.touchremote.R
import com.ranhroi.touchremote.models.DeviceServerModel
import com.ranhroi.touchremote.ui.screens.server.ConnectionViewModel
import com.ranhroi.touchremote.utils.getStringSafe
import com.ranhroi.touchremote.utils.playSoundClick

@Composable
fun DialogVerifyPassword(
    view: View,
    server: DeviceServerModel,
    connection: ConnectionViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    // UI states
    var password by remember { mutableStateOf(server.password) }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberPass by remember { mutableStateOf(server.rememberPass) }
    var localError by remember { mutableStateOf<String?>(null) }

    val uiState by connection.uiState.collectAsState()

    LaunchedEffect(uiState.isLoading, uiState.errorMessage) {
        if (uiState.isLoading) return@LaunchedEffect   // đang chờ, không làm gì

        val err = uiState.errorMessage
        if (err == null && uiState.isConnected) {
            // ✅ Verify thành công
            onDismiss()
        } else if (err != null) {
            // ❌ Verify thất bại
            localError = err
            focusRequester.requestFocus()
        }
    }

    // Focus ô nhập ngay khi mở dialog lần đầu
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = { if (!uiState.isLoading) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !uiState.isLoading,
            dismissOnClickOutside = !uiState.isLoading
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.large
        ) {
            Column(modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()) {

                Text(
                    text = stringResource(R.string.dialog_verify_title),
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.dialog_verify_server_label, server.name),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${server.address}:${server.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        localError = null                 // xoá lỗi local khi user gõ lại
                    },
                    label = { Text(stringResource(R.string.dialog_verify_password_label)) },
                    singleLine = true,
                    isError = localError != null,
                    enabled = !uiState.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    visualTransformation = if (passwordVisible)
                        VisualTransformation.None
                    else
                        PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (passwordVisible)
                            R.drawable.baseline_visibility
                        else
                            R.drawable.baseline_visibility_off
                        val description = if (passwordVisible)
                            stringResource(R.string.dialog_verify_hide_password)
                        else
                            stringResource(R.string.dialog_verify_show_password)
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(painterResource(image), contentDescription = description)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )

                if (localError != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = localError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = rememberPass,
                        onCheckedChange = { rememberPass = it },
                        enabled = !uiState.isLoading
                    )
                    Text(stringResource(R.string.dialog_verify_remember_password))
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !uiState.isLoading
                    ) {
                        Text(stringResource(R.string.dialog_verify_cancel))
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (password.isNotBlank()) {
                                localError = null
                                connection.onPasswordEntered(server, password, rememberPass)
                                view.playSoundClick()
                            }
                        },
                        enabled = password.isNotBlank() && !uiState.isLoading
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(stringResource(R.string.dialog_verify_confirm))
                        }
                    }
                }
            }
        }
    }
}