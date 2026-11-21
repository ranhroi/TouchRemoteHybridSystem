package com.ranhroi.touchremote.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ranhroi.touchremote.R
import com.ranhroi.touchremote.models.InputErrorsModel
import com.ranhroi.touchremote.models.ShortKeyModel
import com.ranhroi.touchremote.ui.components.IconPickerDropdown
import com.ranhroi.touchremote.ui.screens.shortkey.ShortKeySetupViewModel
import com.ranhroi.touchremote.utils.ValidateUtils
import com.ranhroi.touchremote.utils.playSoundClick

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogUpdateShortKey(
    selectedShortKeys: ShortKeyModel,
    shortKeyViewModel: ShortKeySetupViewModel,
    onDismiss: () -> Unit = {},
) {
    val view = LocalView.current
    var isError by remember { mutableStateOf(InputErrorsModel()) }

    var state by remember { mutableStateOf(selectedShortKeys) }

    var rawKeysInput by remember {
        mutableStateOf(state.keys.joinToString(", ") { it.joinToString("+") })
    }

    fun parseKeyInput(input: String): List<List<String>> {
        return input.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { combo ->
                combo.split("+").map { it.trim() }
            }
    }

    fun handleAddHotkeys() {
        state = state.copy(keys = parseKeyInput(rawKeysInput))

        ValidateUtils.validateShortKey(
            state,
            onDismiss,
            { isError = isError.copy(send = true) },
            { isError = isError.copy(name = true) },
            { isError = isError.copy(keys = true) },
            { isError = isError.copy(icon = true) },
            onHandler = {
                shortKeyViewModel.updateShortKey(state)
            }
        )
        view.playSoundClick()
    }

    AlertDialog(
        modifier = Modifier
            .defaultMinSize()
            .padding(horizontal = 10.dp),
        shape = RoundedCornerShape(8.dp),
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = stringResource(R.string.dialog_update_path_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IconPickerDropdown(
                    state.icon,
                    onSetIcon = {
                        state = state.copy(icon = it)
                    },
                    onDone = { handleAddHotkeys() }
                )
                DialogBoxShortKey(
                    isError,
                    state,
                    rawKeysInput = rawKeysInput,
                    onValueChangeSend = { state = state.copy(send = it) },
                    onValueChangeName = { newName ->
                        state = state.copy(name = newName)
                    },
                    onCheckedChangeChar = { state = state.copy(isChar = it) },
                    onValueChangeKeys = { rawKeysInput = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { handleAddHotkeys() },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.dialog_path_update))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    view.playSoundClick()
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.dialog_edit_cancel))
            }
        }
    )
}