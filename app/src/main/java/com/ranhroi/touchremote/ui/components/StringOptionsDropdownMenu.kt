package com.ranhroi.touchremote.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ranhroi.touchremote.R
import com.ranhroi.touchremote.models.LanguageCodeModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StringOptionsDropdownMenu(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String,
    state: String,
    options: List<String>? = null,
    optionsList: List<LanguageCodeModel>? = null,
    showAutoOption: Boolean = false,  // THÊM: hiển thị option Auto
    onSelectedOption: (String) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            enabled = enabled,
            value = state,
            onValueChange = {},
            readOnly = true,
            label = { Text(text) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // Thêm option AUTO nếu cần
            if (showAutoOption) {
                DropdownMenuItem(
                    text = { Text("🌐 ${stringResource(R.string.language_auto)}") },
                    onClick = {
                        onSelectedOption("auto")
                        expanded = false
                    }
                )
            }

            // Danh sách ngôn ngữ
            if (optionsList != null) {
                optionsList.forEach { item ->
                    DropdownMenuItem(
                        text = {
                            Text(text = "${item.flag} ${stringResource(id = item.labelRes)}")
                        },
                        onClick = {
                            onSelectedOption(item.code)
                            expanded = false
                        }
                    )
                }
            } else {
                options?.forEach { selectionOption ->
                    DropdownMenuItem(
                        text = { Text(selectionOption) },
                        onClick = {
                            onSelectedOption(selectionOption)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}