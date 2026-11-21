package com.ranhroi.touchremote.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ranhroi.touchremote.R
import com.ranhroi.touchremote.utils.IconsMap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconPickerDropdown(
    icon: String,
    onSetIcon: (String) -> Unit,
    onDone: () -> Unit
) {
    val textFieldState = rememberTextFieldState(icon)
    val icon_1 = remember(icon) { IconsMap.getIconOrDefault(icon) }
    val iconEntries by remember { mutableStateOf(IconsMap.icon.entries.toList()) }
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        TextField(
            value = textFieldState.text.toString(),
            onValueChange = { textFieldState.setTextAndPlaceCursorAtEnd(it) },
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            leadingIcon = {
                IconAny(icon = icon_1, modifier = Modifier.size(24.dp))
            },
            keyboardOptions = KeyboardOptions.Default.copy(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { onDone() }
            )
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (iconEntries.isEmpty()) {
                DropdownMenuItem(
                    leadingIcon = {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    },
                    text = { Text(stringResource(R.string.icon_picker_dropdown_loading_icons)) },
                    onClick = { /* disabled */ }
                )
            } else {
                iconEntries.forEach { (label, iconRes) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onSetIcon(label)
                            textFieldState.setTextAndPlaceCursorAtEnd(label)
                            expanded = false
                        },
                        leadingIcon = {
                            IconAny(icon = iconRes, modifier = Modifier.size(24.dp))
                        }
                    )
                }
            }
        }
    }
}