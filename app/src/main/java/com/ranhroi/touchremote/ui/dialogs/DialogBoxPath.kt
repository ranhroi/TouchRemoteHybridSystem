package com.ranhroi.touchremote.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.ranhroi.touchremote.ui.pager.path.PathModel
import com.ranhroi.touchremote.models.InputErrorsModel
import com.ranhroi.touchremote.utils.ConstantsUtils

@Composable
fun DialogBoxPath(
    isError: InputErrorsModel,
    state: PathModel,
    onValueChangeSend: (Int) -> Unit,
    onValueChangeName: (String) -> Unit,
    onValueChangePath: (String) -> Unit,
) {

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            isError = isError.name,
            value = state.name,
            onValueChange = onValueChangeName,
            label = { Text(stringResource(R.string.path_name_label)) },
            modifier = Modifier
                .fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = state.path,
            onValueChange = onValueChangePath,
            label = { Text(stringResource(R.string.path_path_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            maxLines = 3
        )
    }
}