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
import com.ranhroi.touchremote.ui.pager.path.PathModel
import com.ranhroi.touchremote.models.InputErrorsModel
import com.ranhroi.touchremote.ui.components.IconPickerDropdown
import com.ranhroi.touchremote.ui.pager.path.PathsViewModel
import com.ranhroi.touchremote.utils.ValidateUtils
import com.ranhroi.touchremote.utils.playSoundClick

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogInsertPath(
    type: String,
    pathsViewModel: PathsViewModel,
    onDismiss: () -> Unit = {},
) {
    val view = LocalView.current
    var isError by remember { mutableStateOf(InputErrorsModel()) }

    var state by remember {
        mutableStateOf(
            PathModel(
                send = 1,
                type = type,
                name = "",
                path = "",
                icon = "baseline_link"
            )
        )
    }

    fun handleAddPath() {
        ValidateUtils.validatePath(
            state,
            onDismiss,
            { isError = isError.copy(send = true) },
            { isError = isError.copy(name = true) },
            { isError = isError.copy(icon = true) },
            onHandler = {
                pathsViewModel.insertPath(state)
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
                text = stringResource(R.string.dialog_add_path_title),
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
                    onDone = { handleAddPath() }
                )
                DialogBoxPath(
                    isError, state,
                    onValueChangeSend = { state = state.copy(send = it) },
                    onValueChangeName = { state = state.copy(name = it) },
                    onValueChangePath = { state = state.copy(path = it) }
                )
            }
        },
        confirmButton = {
            Button(onClick = { handleAddPath() }, shape = RoundedCornerShape(8.dp)) {
                Text(stringResource(R.string.dialog_path_add))
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