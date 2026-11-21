package com.ranhroi.touchremote.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ranhroi.touchremote.R
import com.ranhroi.touchremote.ui.pager.files.DetailsFileModel

@Composable
fun FileDetailsDialog(
    details: DetailsFileModel,
    onDismiss: () -> Unit
) {
    if (!details.isShow) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (details.isDirectory) stringResource(R.string.file_details_title_folder)
                else stringResource(R.string.file_details_title_file),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                DetailRow(
                    label = stringResource(R.string.file_details_name),
                    value = details.name
                )

                DetailRow(
                    label = stringResource(R.string.file_details_type),
                    value = if (details.isDirectory) stringResource(R.string.file_details_type_folder)
                    else stringResource(R.string.file_details_type_file)
                )

                DetailRow(
                    label = stringResource(R.string.file_details_size),
                    value = details.size
                )

                DetailRow(
                    label = stringResource(R.string.file_details_date),
                    value = details.date
                )

                DetailRow(
                    label = stringResource(R.string.file_details_path),
                    value = details.path,
                    isPath = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.file_details_close))
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String, isPath: Boolean = false) {
    Row(
        modifier = Modifier.padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = if (isPath) 11.sp else 13.sp,
            fontWeight = if (isPath) FontWeight.Normal else FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            modifier = Modifier.padding(start = 8.dp, top = 2.dp)
        )
    }
}