package com.ranhroi.touchremote.ui.components

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ranhroi.touchremote.models.HeaderMenuModel
import com.ranhroi.touchremote.utils.IconsMap
import com.ranhroi.touchremote.utils.playSoundClick

/**
 * Header dạng hàng ngang với menu items tùy biến
 * 
 * Cách dùng:
 * HeaderHorizontalCustom(
 *     view = view,
 *     leftItems = listOf(
 *         HeaderMenuModel(icon = "stop", onClick = { ... }),
 *         HeaderMenuModel(text = "START", showOnlyText = true, onClick = { ... })
 *     ),
 *     rightItems = listOf(
 *         HeaderMenuModel(icon = "settings", onClick = { ... })
 *     )
 * )
 */
@Composable
fun HeaderHorizontal(
    view: View,
    height: Int = 50,
    itemSpacing: Int = 8,
    rotationAngle: Float = 0f,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    // 🔥 LEFT ITEMS (Hiển thị bên trái)
    leftItems: List<HeaderMenuModel> = emptyList(),
    // 🔥 RIGHT ITEMS (Hiển thị bên phải)
    rightItems: List<HeaderMenuModel> = emptyList(),
    // 🔥 CENTER CONTENT (Nội dung ở giữa)
    centerContent: @Composable (() -> Unit)? = null,
    // 🔥 LEFT CONTENT (Nội dung bên trái, trước leftItems)
    leftContent: @Composable (() -> Unit)? = null,
    // 🔥 RIGHT CONTENT (Nội dung bên phải, sau rightItems)
    rightContent: @Composable (() -> Unit)? = null,
    // 🔥 Extra menu items
    extraLeftItems: @Composable (() -> Unit)? = null,
    extraRightItems: @Composable (() -> Unit)? = null
) {
    val visibleLeftItems = leftItems.filter { it.isVisible() }
    val visibleRightItems = rightItems.filter { it.isVisible() }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp),
        color = color,
        tonalElevation = 3.dp,
    ) {
        val scrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height.dp)
                .horizontalScroll(scrollState)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ===== LEFT SECTION =====
            Row(
                modifier = Modifier
                    .wrapContentWidth()
                    .height(height.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(itemSpacing.dp)
            ) {
                leftContent?.invoke()

                visibleLeftItems.forEach { item ->
                    HeaderMenuItemView(
                        item = item,
                        view = view,
                        rotationAngle = rotationAngle
                    )
                }
                extraLeftItems?.invoke()
            }

            // ===== CENTER SECTION =====
            centerContent?.invoke()

            // ===== RIGHT SECTION =====
            Row(
                modifier = Modifier
                    .wrapContentWidth()
                    .height(height.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy((6+itemSpacing).dp)
            ) {
                visibleRightItems.forEach { item ->
                    HeaderMenuItemView(
                        item = item,
                        view = view,
                        rotationAngle = rotationAngle
                    )
                }
                extraRightItems?.invoke()
                rightContent?.invoke()
            }
        }
    }
}


/**
 * Header dạng cột (vertical) với menu items tùy biến
 *
 * Cách dùng:
 * HeaderHeightCustom(
 *     view = view,
 *     topItems = listOf(
 *         // Chỉ hiển thị icon
 *         HeaderMenuModel(icon = "play_arrow", onClick = { ... }),
 *         // Chỉ hiển thị text
 *         HeaderMenuModel(text = "START", showOnlyText = true, onClick = { ... }),
 *         // Hiển thị cả icon và text
 *         HeaderMenuModel(icon = "settings", text = "Settings", onClick = { ... })
 *     )
 * )
 */
@Composable
fun HeaderVertical(
    view: View,
    width: Int = 50,
    itemSpacing: Int = 8,
    rotationAngle: Float = 90f,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    topItems: List<HeaderMenuModel> = emptyList(),
    bottomItems: List<HeaderMenuModel> = emptyList(),
    middleContent: @Composable (() -> Unit)? = null,
    headerContent: @Composable (() -> Unit)? = null,
    footerContent: @Composable (() -> Unit)? = null,
    extraTopItems: @Composable (() -> Unit)? = null,
    extraBottomItems: @Composable (() -> Unit)? = null,
) {
    val visibleTopItems = topItems.filter { it.isVisible() }
    val visibleBottomItems = bottomItems.filter { it.isVisible() }

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(width.dp),
        color = color,
        tonalElevation = 3.dp,
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(scrollState)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ===== TOP SECTION =====
            Column(
                modifier = Modifier.wrapContentHeight(),
                verticalArrangement = Arrangement.spacedBy(itemSpacing.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                headerContent?.invoke()

                visibleTopItems.forEach { item ->
                    HeaderMenuItemView(
                        item = item,
                        view = view,
                        rotationAngle = rotationAngle
                    )
                }
                extraTopItems?.invoke()
            }

            // ===== MIDDLE SECTION =====
            middleContent?.invoke()

            // ===== BOTTOM SECTION =====
            Column(
                modifier = Modifier.wrapContentHeight(),
                verticalArrangement = Arrangement.spacedBy(itemSpacing.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                visibleBottomItems.forEach { item ->
                    HeaderMenuItemView(
                        item = item,
                        view = view,
                        rotationAngle = rotationAngle
                    )
                }
                extraBottomItems?.invoke()
                footerContent?.invoke()
            }
        }
    }
}

// ===== ITEM VIEW =====

@Composable
private fun HeaderMenuItemView(
    item: HeaderMenuModel,
    view: View,
    rotationAngle: Float
) {
    // 🔥 Lấy icon từ IconsMap
    val icon = remember(item.icon) {
        if (!item.icon.isNullOrEmpty()) IconsMap.getIconOrDefault(item.icon) else null
    }

    // 🔥 Xác định hiển thị
    val hasIcon = icon != null
    val hasText = !item.text.isNullOrEmpty()

    // 🔥 Nếu không có icon và không có text → KHÔNG HIỂN THỊ
    if (!hasIcon && !hasText && !item.isStatusDot) return

    // 🔥 Màu sắc
    val defaultColor = if (item.isDestructive) {
        Color.Red
    } else {
        MaterialTheme.colorScheme.primary
    }
    val textColor = item.textColor ?: defaultColor
    val iconTint = item.iconTint ?: defaultColor

    // ============================================================
    // 1. STATUS DOT (Ưu tiên cao nhất)
    // ============================================================
    if (item.isStatusDot) {
        Box(
            modifier = item.modifier
                .size(12.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(
                    if (item.iconTint != null) item.iconTint
                    else if (item.isConnected) Color(0xFF4CAF50)
                    else Color(0xFFE57373)
                )
        )
        return
    }

    // ============================================================
    // 2. CHỈ HIỂN THỊ ICON (Không có text)
    // ============================================================
    if (hasIcon && !hasText) {
        IconButton(
            enabled = item.enabled,
            onClick = {
                try {
                    item.onClick()
                    view.playSoundClick()
                } catch (_: Exception) {}
            },
            modifier = Modifier
                .size(33.dp)
                .padding(2.dp)
        ) {
            IconAny(
                icon = icon,
                text = item.text,
                color = iconTint,
                modifier = item.modifier.size(18.dp)
                    .graphicsLayer { rotationZ = rotationAngle }
            )
        }
        return
    }

    // ============================================================
    // 3. CHỈ HIỂN THỊ TEXT (Không có icon)
    // ============================================================
    if (!hasIcon && hasText) {
        Text(
            text = item.text,
            fontSize = item.textFontSize.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = item.modifier
                .graphicsLayer { rotationZ = rotationAngle }
        )
        return
    }

    // ============================================================
    // 4. HIỂN THỊ CẢ ICON VÀ TEXT
    // ============================================================
    if (hasIcon && hasText) {
        // ===== TRƯỜNG HỢP XOAY 90° (HEADER DỌC) =====
        if (rotationAngle == 90f) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = item.modifier
            ) {
                IconButton(
                    enabled = item.enabled,
                    onClick = {
                        try {
                            item.onClick()
                            view.playSoundClick()
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier
                        .size(33.dp)
                        .padding(2.dp)
                ) {
                    IconAny(
                        icon = icon,
                        text = item.text,
                        color = iconTint,
                        modifier = item.modifier.size(18.dp)
                            .graphicsLayer { rotationZ = rotationAngle }
                    )
                }
                Text(
                    text = item.text,
                    fontSize = item.textFontSize.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .graphicsLayer { rotationZ = rotationAngle }
                )
            }
            return
        }

        // ===== TRƯỜNG HỢP KHÔNG XOAY (HEADER NGANG) =====
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                enabled = item.enabled,
                onClick = {
                    try {
                        item.onClick()
                        view.playSoundClick()
                    } catch (_: Exception) {}
                },
                modifier = Modifier
                    .size(33.dp)
                    .padding(2.dp)
            ) {
                IconAny(
                    icon = icon,
                    text = item.text,
                    color = iconTint,
                    modifier = item.modifier.size(18.dp)
                        .graphicsLayer { rotationZ = rotationAngle }
                )
            }
            Text(
                text = item.text,
                fontSize = item.textFontSize.sp,
                fontWeight = FontWeight.Medium,
                color = textColor,
                maxLines = 1,
                modifier = Modifier
                    .graphicsLayer { rotationZ = rotationAngle }
            )
        }
        return
    }
}

//
//@Composable
//private fun HeaderMenuItemView(
//    item: HeaderMenuModel,
//    view: View,
//    rotationAngle: Float
//) {
//    val icon = remember(item.icon) {
//        if (!item.icon.isNullOrEmpty()) IconsMap.getIconOrDefault(item.icon) else null
//    }
//
//    val showIcon = icon != null
//    val showText = !item.text.isNullOrEmpty()
//
//    // 🔥 Nếu không có gì để hiển thị thì bỏ qua
////    if (!showIcon && !showText ) return
//
//    // 🔥 Màu sắc
//    val defaultColor = if (item.isDestructive) {
//        MaterialTheme.colorScheme.error
//    } else {
//        MaterialTheme.colorScheme.primary
//    }
//    val textColor = item.textColor ?: defaultColor
//    val iconTint = item.iconTint ?: defaultColor
//
//    if (item.isStatusDot) {
//        Box(
//            modifier = item.modifier
//                .size(10.dp)
//                .clip(RoundedCornerShape(5.dp))
//                .background(
//                    if (item.iconTint != null) item.iconTint else if (item.isConnected) Color(
//                        0xFF4CAF50
//                    ) else Color(0xFFE57373)
//                )
//        )
//    } else {
//        when {
//            // 🔥 Chỉ hiển thị icon
//            showIcon && !showText -> {
//                IconButton(
//                    enabled = item.enabled,
//                    onClick = {
//                        try {
//                            item.onClick()
//                            view.playSoundClick()
//                        } catch (_: Exception) {
//                        }
//                    },
//                    modifier = item.modifier
//                        .size(33.dp)
//                        .padding(2.dp)
//                ) {
//                    IconAny(
//                        icon = icon,
//                        text = item.text,
//                        color = iconTint,
//                        modifier = Modifier
//                            .graphicsLayer {
//                                rotationZ = rotationAngle
//                            },
//                    )
//                }
//            }
//            // 🔥 Chỉ hiển thị text
//            !showIcon && showText -> {
//                Text(
//                    text = item.text,
//                    fontSize = item.textFontSize.sp,
//                    fontWeight = FontWeight.Bold,
//                    color = textColor,
//                    textAlign = TextAlign.Center,
//                    maxLines = 2,
//                    modifier = item.modifier
//                        .padding(horizontal = 2.dp)
//                        .graphicsLayer {
//                            rotationZ = rotationAngle
//                        },
//                )
//            }
//            // 🔥 Hiển thị cả icon và text (xếp dọc)
//            showIcon && showText -> {
//                if (rotationAngle == 90f) {
//                    Column(
//                        horizontalAlignment = Alignment.CenterHorizontally,
//                        verticalArrangement = Arrangement.spacedBy(2.dp)
//                    ) {
//                        IconButton(
//                            enabled = item.enabled,
//                            onClick = {
//                                try {
//                                    item.onClick()
//                                    view.playSoundClick()
//                                } catch (_: Exception) {
//                                }
//                            },
//                            modifier = item.modifier
//                                .size(33.dp)
//                                .padding(2.dp)
//                        ) {
//                            IconAny(
//                                icon = icon,
//                                text = item.text,
//                                color = iconTint,
//                                modifier = Modifier
//                                    .graphicsLayer {
//                                        rotationZ = rotationAngle
//                                    },
//                            )
//                        }
//                        Text(
//                            text = item.text,
//                            fontSize = item.textFontSize.sp,
//                            fontWeight = FontWeight.Medium,
//                            color = textColor,
//                            textAlign = TextAlign.Center,
//                            maxLines = 1,
//                            modifier = item.modifier
//                                .graphicsLayer {
//                                    rotationZ = rotationAngle
//                                },
//                        )
//                    }
//                } else {
//                    Row(
//                        horizontalArrangement = Arrangement.spacedBy(4.dp),
//                        verticalAlignment = Alignment.CenterVertically
//                    ) {
//                        IconButton(
//                            enabled = item.enabled,
//                            onClick = {
//                                try {
//                                    item.onClick()
//                                    view.playSoundClick()
//                                } catch (_: Exception) {
//                                }
//                            },
//                            modifier = item.modifier
//                                .size(33.dp)
//                                .padding(2.dp)
//                        ) {
//                            IconAny(
//                                icon = icon,
//                                text = item.text,
//                                color = iconTint,
//                                modifier = item.modifier
//                                    .graphicsLayer {
//                                        rotationZ = rotationAngle
//                                    },
//                            )
//                        }
//                        Text(
//                            text = item.text,
//                            fontSize = item.textFontSize.sp,
//                            fontWeight = FontWeight.Medium,
//                            color = textColor,
//                            textAlign = TextAlign.Center,
//                            maxLines = 1,
//                            modifier = item.modifier
//                                .graphicsLayer {
//                                    rotationZ = rotationAngle
//                                },
//                        )
//                    }
//                }
//            }
//        }
//    }
//}


// ===== TẠO MENU ITEMS ĐA DẠNG =====
//val leftMenuItems = listOf(
//    // ✅ Chỉ icon
//    HeaderMenuModel(
//        icon = "stop",
//        isDestructive = true,
//        visible = { isConnected },
//        onClick = {
//            inputManager.send_string_message("mirror stop")
//            viewModel.stopStream()
//        }
//    ),
//    // ✅ Chỉ text (nổi bật)
//    HeaderMenuModel(
//        text = "START",
//        showOnlyText = true,
//        visible = { isConnected && !isStreaming },
//        textColor = Color(0xFF4CAF50),
//        onClick = {
//            viewModel.startStream(...)
//        }
//    ),
//    // ✅ Cả icon và text
//    HeaderMenuModel(
//        icon = "settings",
//        text = "Settings",
//        onClick = { toggleControls() }
//    )
//)
//
//val rightMenuItems = listOf(
//    // ✅ Chỉ icon
//    HeaderMenuModel(
//        icon = "list",
//        onClick = { showLogs = !showLogs }
//    ),
//    // ✅ Chỉ icon
//    HeaderMenuModel(
//        icon = "games",
//        onClick = { showControlCircle = !showControlCircle }
//    ),
//    // ✅ Chỉ text (nhỏ gọn)
//    HeaderMenuModel(
//        text = "ZOOM",
//        showOnlyText = true,
//        onClick = { /* zoom action */ }
//    )
//)
//
//// ===== HEADER HÀNG NGANG =====
//HeaderHorizontalCustom(
//view = view,
//leftItems = leftMenuItems,
//rightItems = rightMenuItems,
//modifier = Modifier
//.fillMaxWidth()
//.zIndex(1f),
//color = Color.Black.copy(alpha = 0.6f),
//height = 48,
//itemSpacing = 2,
//centerContent = {
//    // Status indicator
//    Row(
//        verticalAlignment = Alignment.CenterVertically,
//        horizontalArrangement = Arrangement.spacedBy(6.dp)
//    ) {
//        Box(
//            modifier = Modifier
//                .size(8.dp)
//                .clip(CircleShape)
//                .background(
//                    when {
//                        isConnected && isStreaming -> Color(0xFF4CAF50)
//                        isConnected -> Color(0xFFFFA500)
//                        else -> Color(0xFFE57373)
//                    }
//                )
//        )
//        Text(
//            text = when {
//                isConnected && isStreaming -> "LIVE"
//                isConnected -> "READY"
//                else -> "OFF"
//            },
//            fontSize = 10.sp,
//            fontWeight = FontWeight.Bold,
//            color = Color.White.copy(alpha = 0.8f)
//        )
//        // Audio status
//        val stateColor = when (audioState) {
//            ReceiverState.PLAYING -> Color(0xFF00E676)
//            ReceiverState.IDLE -> Color(0xFFFFC107)
//            ReceiverState.SLEEP -> Color(0xFF90A4AE)
//        }
//        Text(
//            text = when (audioState) {
//                ReceiverState.PLAYING -> "🔊"
//                else -> "🔇"
//            },
//            fontSize = 12.sp,
//            color = stateColor
//        )
//    }
//},
//isShowRefresh = true,
//enabledRefresh = !isStreaming,
//onRefreshClick = { connection.pingStreamAndRefresh() }
//)