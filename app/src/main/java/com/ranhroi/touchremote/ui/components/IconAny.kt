package com.ranhroi.touchremote.ui.components

import android.annotation.SuppressLint
import android.util.TypedValue
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ranhroi.touchremote.R
import com.ranhroi.touchremote.utils.IconsMap


@SuppressLint("LocalContextResourcesRead")
@Composable
fun IconAny(
    text: String? = null,
    icon: Any,
    isRed: Boolean = false,
    color: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier.size(18.dp)
) {
    val context = LocalContext.current
    val targetTint = if (isRed) MaterialTheme.colorScheme.error else color

    when (icon) {
        is ImageVector -> {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = targetTint,
                modifier = modifier
            )
        }
        is Int -> {
            // Xử lý resource ID (R.drawable.*, R.mipmap.*)
            val isValidResource = try {
                context.resources.getResourceTypeName(icon) != null
            } catch (e: Exception) {
                false
            }

            if (!isValidResource) {
                Icon(
                    painterResource(R.drawable.baseline_help),
                    contentDescription = text,
                    tint = targetTint,
                    modifier = modifier
                )
                return
            }

            val isXmlVector = remember(icon) {
                try {
                    val value = TypedValue()
                    context.resources.getValue(icon, value, true)
                    val fileName = value.string?.toString() ?: ""
                    fileName.endsWith(".xml", ignoreCase = true)
                } catch (e: Exception) {
                    false
                }
            }

            Image(
                painter = painterResource(id = icon),
                contentDescription = text,
                modifier = modifier,
                colorFilter = if (isXmlVector) ColorFilter.tint(targetTint) else null
            )
        }
        is String -> {
            // ✅ Xử lý tên icon (ví dụ: "baseline_comment", "ic_comment")
            val resId = IconsMap.getIconOrDefault(icon)
            // Gọi đệ quy với Int để xử lý resource
            IconAny(
                text = text,
                icon = resId,
                isRed = isRed,
                color = color,
                modifier = modifier
            )
        }
        else -> {
            // Fallback an toàn
            Icon(
                painterResource(R.drawable.baseline_help),
                contentDescription = text,
                tint = targetTint,
                modifier = modifier
            )
        }
    }
}