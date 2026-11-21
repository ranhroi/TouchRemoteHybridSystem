package com.ranhroi.touchremote.ui.pager.files

import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ranhroi.touchremote.R
import com.ranhroi.touchremote.models.DropMenuModel
import com.ranhroi.touchremote.ui.components.IconAny
import com.ranhroi.touchremote.ui.components.MoreVertDropdown
import com.ranhroi.touchremote.ui.dialogs.ConfirmDialog
import com.ranhroi.touchremote.utils.IconsMap
import com.ranhroi.touchremote.utils.playSoundClick

@Composable
fun FileItem(
    view: View,
    icon: String = "ic_folder",
    node: FileModel,
    isBack: Boolean = false,
    onNode: (() -> Unit)? = null,
    onCopy: (() -> Unit)? = null,
    onCut: (() -> Unit)? = null,
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onDownloadZip: (() -> Unit)? = null,
    onDetails: (() -> Unit)? = null,
    onClick: () -> Unit = {},
    colors: CardColors = CardDefaults.cardColors(),
    content: @Composable (() -> Unit) = {}
) {
    val iconData = remember(icon) { IconsMap.getIconOrDefault(icon) }
    val isDir = remember(node.isDirectory) { node.isDirectory }

    // State cho menu và dialogs
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        // ===== Item content =====
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(0.dp),
            onClick = { onClick(); view.playSoundClick() },
            colors = colors,
            shape = RoundedCornerShape(0.dp),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(0.80f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        IconAny(
                            icon = if (isDir) iconData else R.mipmap.ic_file,
                            color = if (isDir) Color.Blue else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Text(
                            textAlign = TextAlign.Start,
                            text = node.name,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Nút 3 chấm (Dropdown Menu) - chỉ hiển thị khi không phải nút back
                    if (!isBack) {
                        IconButton(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(0.20f),
                            onClick = { showMenu = true }
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.file_more_options)
                            )
                        }
                    }
                }

                content()

                // ===== MENU DROPDOWN =====
                if (showMenu) {
                    MoreVertDropdown(
                        view = view,
                        expanded = showMenu,
                        onDismiss = { showMenu = false },
                        menuItems = listOf(
                            DropMenuModel(
                                text = if (isDir) stringResource(R.string.file_download_folder) else stringResource(
                                    R.string.file_download_file
                                ),
                                onClick = {
                                    showMenu = false
                                    onDownload?.invoke()
                                },
                                isVisible = { onDownload != null },
                                icon = if (isDir) "ic_folder_download" else "baseline_file_download"
                            ),
                            DropMenuModel(
                                text = if (isDir) stringResource(R.string.file_download_zip_folder) else stringResource(
                                    R.string.file_download_zip_file
                                ),
                                onClick = {
                                    showMenu = false
                                    onDownloadZip?.invoke()
                                },
                                isVisible = { onDownloadZip != null && isDir },
                                icon = "baseline_folder_zip"
                            ),
                            DropMenuModel(
                                text = if (isDir) stringResource(R.string.file_open_folder) else stringResource(
                                    R.string.file_open_file
                                ),
                                onClick = {
                                    showMenu = false
                                    onNode?.invoke()
                                },
                                isVisible = { onNode != null },
                                icon = if (isDir) "baseline_folder_open" else "baseline_file_open"
                            ),
                            DropMenuModel(
                                text = if (isDir) stringResource(R.string.file_copy_folder) else stringResource(
                                    R.string.file_copy_file
                                ),
                                onClick = {
                                    showMenu = false
                                    onCopy?.invoke()
                                },
                                isVisible = { onCopy != null },
                                icon = "outline_content_copy"
                            ),
                            DropMenuModel(
                                text = if (isDir) stringResource(R.string.file_cut_folder) else stringResource(
                                    R.string.file_cut_file
                                ),
                                onClick = {
                                    showMenu = false
                                    onCut?.invoke()
                                },
                                isVisible = { onCut != null },
                                icon = "baseline_content_cut"
                            ),
                            DropMenuModel(
                                text = if (isDir) stringResource(R.string.file_rename_folder) else stringResource(
                                    R.string.file_rename_file
                                ),
                                onClick = {
                                    showMenu = false
                                    onRename?.invoke()
                                },
                                isVisible = { onRename != null },
                                icon = "baseline_drive_file_rename_outline"
                            ),
                            DropMenuModel(
                                text = if (isDir) stringResource(R.string.file_details_folder) else stringResource(
                                    R.string.file_details_file
                                ),
                                onClick = {
                                    showMenu = false
                                    onDetails?.invoke()
                                },
                                isVisible = { onDetails != null },
                                icon = "baseline_details"
                            ),
                            DropMenuModel(
                                text = if (isDir) stringResource(R.string.file_delete_folder) else stringResource(
                                    R.string.file_delete_file
                                ),
                                onClick = {
                                    showMenu = false
                                    showDeleteConfirm = true
                                },
                                isVisible = { onDelete != null },
                                isDestructive = true,
                                icon = "baseline_delete"
                            )
                        )
                    )
                }

            }
        }
    }

    // ===== DIALOG XÁC NHẬN XÓA =====
    if (showDeleteConfirm) {
        ConfirmDialog(
            show = showDeleteConfirm,
            title = stringResource(R.string.file_delete_confirm_title),
            message = if (isDir) {
                stringResource(R.string.file_delete_confirm_folder, node.name)
            } else {
                stringResource(R.string.file_delete_confirm_file, node.name)
            },
            onConfirm = {
                showDeleteConfirm = false
                onDelete?.invoke()
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}