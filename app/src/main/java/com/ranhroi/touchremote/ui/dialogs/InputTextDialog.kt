package com.ranhroi.touchremote.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ranhroi.touchremote.R
import com.ranhroi.touchremote.utils.getStringSafe

@Composable
fun InputTextDialog(
    show: Boolean,
    title: String,
    hint: String = "",
    initialText: String = "",
    confirmText: String = stringResource(R.string.input_dialog_confirm),
    dismissText: String = stringResource(R.string.input_dialog_cancel),
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (!show) return
    val context = LocalContext.current
    var text by remember { mutableStateOf(initialText) }
    var isError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        isError = false
                        errorMessage = ""
                    },
                    label = { Text(hint) },
                    placeholder = { Text(stringResource(R.string.input_dialog_placeholder)) },
                    isError = isError,
                    supportingText = {
                        if (isError) {
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (text.isNotBlank()) {
                                onConfirm(text)
                            } else {
                                isError = true
                                errorMessage = context.getStringSafe(R.string.input_dialog_error_empty)
                            }
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (text.isNotBlank()) {
                        onConfirm(text)
                    } else {
                        isError = true
                        errorMessage = context.getStringSafe(R.string.input_dialog_error_empty)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText)
            }
        },
        modifier = Modifier.padding(16.dp)
    )
}