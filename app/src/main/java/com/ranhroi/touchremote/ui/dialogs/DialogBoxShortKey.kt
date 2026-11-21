// dialogs/DialogBoxShortKey.kt
package com.ranhroi.touchremote.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ranhroi.touchremote.R
import com.ranhroi.touchremote.models.InputErrorsModel
import com.ranhroi.touchremote.models.ShortKeyModel
import com.ranhroi.touchremote.utils.ConstantsUtils

@Composable
fun DialogBoxShortKey(
    isError: InputErrorsModel,
    state: ShortKeyModel,
    rawKeysInput: String,
    onValueChangeSend: (Int) -> Unit,
    onCheckedChangeChar: (Boolean) -> Unit,
    onValueChangeName: (String) -> Unit,
    onValueChangeKeys: (String) -> Unit,
) {
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(35.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.hotkey_key_char))
                Checkbox(
                    checked = state.isChar,
                    onCheckedChange = onCheckedChangeChar
                )
            }
        }
        ConstantsUtils.getSendOptions(context).forEach { (text, value) ->
            val isSelected = state.send == value
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(35.dp)
                    .clickable(onClick = { onValueChangeSend(value) }),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = text,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                RadioButton(
                    modifier = Modifier.weight(0.2f),
                    selected = isSelected,
                    onClick = { onValueChangeSend(value) }
                )
            }
        }

        OutlinedTextField(
            isError = isError.name,
            value = state.name,
            onValueChange = { newName ->
                onValueChangeName(newName)
            },
            label = { Text(stringResource(R.string.hotkey_name_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = {
                if (state.name != "") {
                    Text("(Mặc định)")
                }
            }
        )

        OutlinedTextField(
            isError = isError.keys,
            value = rawKeysInput,
            onValueChange = onValueChangeKeys,
            label = { Text(stringResource(R.string.hotkey_ShortKeys_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            maxLines = 3
        )
    }
}