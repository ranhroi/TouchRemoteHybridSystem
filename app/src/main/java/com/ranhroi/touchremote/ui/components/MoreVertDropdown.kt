package com.ranhroi.touchremote.ui.components

import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ranhroi.touchremote.models.DropMenuModel
import com.ranhroi.touchremote.utils.IconsMap
import com.ranhroi.touchremote.utils.playSoundClick

@Composable
fun MoreVertDropdown(
    view: View,
    expanded: Boolean,
    onDismiss: () -> Unit,
    menuItems: List<DropMenuModel>,
    modifier: Modifier = Modifier,
    extraContent: @Composable () -> Unit = { }
) {
    if (expanded) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
            modifier = modifier.widthIn(min = 180.dp)
        ) {
            // Hiển thị nội dung mở rộng chung nếu có
            extraContent()

            // Lọc danh sách: Chỉ lấy những item thỏa mãn điều kiện hiển thị
            val visibleItems = menuItems.filter { it.isVisible() }

            visibleItems.forEachIndexed { index, item ->
                val icon = remember(item.icon) { IconsMap.getIconOrDefault(item.icon) }

                DropdownMenuItem(
                    enabled = item.enabled,
                    text = {
                        Column {
                            Text(
                                text = item.text,
                                color = if (item.isDestructive) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                            // Hiển thị Custom Content riêng của từng item (nếu có)
                            item.content()
                        }
                    },
                    onClick = {
                        try {
                            item.onClick()
                            onDismiss()
                            view.playSoundClick()
                        } catch (e: Exception) {
                            // Xử lý lỗi nếu cần
                        }
                    },
                    leadingIcon = {
                        IconAny(
                            text = item.text,
                            icon = icon,
                            isRed = item.isDestructive,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                )

                // Divider thông minh: Không hiển thị nếu là item cuối cùng
                if (index < visibleItems.size - 1) {
                    Divider(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}
