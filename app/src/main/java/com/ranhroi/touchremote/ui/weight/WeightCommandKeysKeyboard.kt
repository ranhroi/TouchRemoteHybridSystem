package com.ranhroi.touchremote.ui.weight

import android.content.Context
import android.view.View
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.ranhroi.touchremote.R
import com.ranhroi.touchremote.models.KeyboardModel
import com.ranhroi.touchremote.send.InputManager
import com.ranhroi.touchremote.ui.keyboard.CommandKeysRepository
import com.ranhroi.touchremote.ui.components.IconAny
import com.ranhroi.touchremote.ui.screens.main.SelectAppState
import com.ranhroi.touchremote.ui.theme.ButtonDark
import com.ranhroi.touchremote.ui.theme.NeonCyan
import com.ranhroi.touchremote.ui.theme.SurfaceCardBorder
import com.ranhroi.touchremote.ui.theme.TextMuted
import com.ranhroi.touchremote.ui.theme.TextPrimary
import com.ranhroi.touchremote.ui.theme.TextSecondary
import com.ranhroi.touchremote.utils.playSoundClick
import com.ranhroi.touchremote.utils.showToast

/**
 * Bàn phím tổ hợp — 2 chế độ chuyển đổi được:
 *
 *  - COMBO   : chọn nhiều phím → Preview → bấm "Gửi" → InputManager.sendComboFromCodes.
 *  - INSTANT : chạm phím nào gửi phím đó ngay. Modifier dùng latch của InputManager.
 *
 * Không dùng ShortKeysParser nữa — mọi gửi đi qua InputManager.
 */
@Composable
fun WeightCommandKeysKeyboard(
    view: View,
    context: Context,
    selectState: SelectAppState,
    inputManager: InputManager,
    modifier: Modifier = Modifier
) {
    var selectedGroup by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    // Chế độ: false = Combo, true = Instant
    var isInstantMode by remember { mutableStateOf(false) }

    // State chọn nhiều phím (chỉ dùng ở Combo mode)
    val selectedKeys = remember { mutableStateListOf<String>() }

    // Quan sát modifier đang latch
    val latchedMods by inputManager.latchedModifiers.collectAsState()

    val groups = CommandKeysRepository.allGroups

    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(Color.Black)
    ) {
        Spacer(Modifier.height(8.dp))

        // ============================================================
        // SEARCH BAR + CLOSE
        // ============================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = stringResource(R.string.command_keys_search_hint),
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.command_keys_search_clear),
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp, color = TextPrimary),
                shape = RoundedCornerShape(5.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = SurfaceCardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = NeonCyan
                )
            )

            IconButton(
                onClick = { selectState.resetState() },
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.Red.copy(alpha = 0.1f))
            ) {
                Icon(
                    painterResource(R.drawable.baseline_close),
                    contentDescription = stringResource(R.string.command_keys_close),
                    tint = Color.Red,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // ============================================================
        // MODE TOGGLE — Combo / Instant
        // ============================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModeChip(
                label = stringResource(R.string.command_keys_mode_combo),
                selected = !isInstantMode,
                onClick = {
                    if (isInstantMode) {
                        isInstantMode = false
                        selectedKeys.clear()
                        inputManager.clearModifiers()
                    }
                    view.playSoundClick()
                }
            )
            ModeChip(
                label = stringResource(R.string.command_keys_mode_instant),
                selected = isInstantMode,
                onClick = {
                    if (!isInstantMode) {
                        isInstantMode = true
                        selectedKeys.clear()
                        inputManager.clearModifiers()
                    }
                    view.playSoundClick()
                }
            )
        }

        // Hint cho Instant mode
        if (isInstantMode) {
            Text(
                text = stringResource(R.string.command_keys_instant_hint),
                fontSize = 11.sp,
                color = TextMuted,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )
        }

        // ============================================================
        // TAB CHIPS
        // ============================================================
        LazyVerticalGrid(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            columns = GridCells.Fixed(4)
        ) {
            itemsIndexed(groups) { index, group ->
                FilterChip(
                    selected = selectedGroup == index,
                    onClick = {
                        selectedGroup = index
                        searchQuery = ""
                        view.playSoundClick()
                    },
                    label = {
                        Text(
                            text = stringResource(group.titleRes),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (selectedGroup == index) FontWeight.Bold
                            else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                        selectedLabelColor = NeonCyan,
                        containerColor = Color.Transparent,
                        labelColor = TextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedGroup == index,
                        borderColor = SurfaceCardBorder,
                        selectedBorderColor = NeonCyan
                    )
                )
            }
        }

        // ============================================================
        // PREVIEW (chỉ Combo mode)
        // ============================================================
        if (!isInstantMode && selectedKeys.isNotEmpty()) {
            SelectedKeysPreview(
                selectedKeys = selectedKeys,
                onRemove = { code ->
                    selectedKeys.remove(code)
                    view.playSoundClick()
                },
                onClear = {
                    selectedKeys.clear()
                    view.playSoundClick()
                }
            )
            Spacer(Modifier.height(8.dp))
        }

        // ============================================================
        // KEY GRID
        // ============================================================
        val currentGroup = groups[selectedGroup]
        val items = if (searchQuery.isBlank()) {
            currentGroup.items
        } else {
            CommandKeysRepository.search(searchQuery)
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 64.dp),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            itemsIndexed(
                items = items,
                key = { _, item -> item.code }
            ) { _, key ->
                val isSelected = if (isInstantMode) {
                    isModifierLatched(key.code, latchedMods)
                } else {
                    selectedKeys.contains(key.code)
                }

                KeyButton(
                    item = key,
                    isSelected = isSelected,
                    onClick = {
                        if (isInstantMode) {
                            handleInstantKey(key, inputManager, context)
                        } else {
                            if (selectedKeys.contains(key.code)) {
                                selectedKeys.remove(key.code)
                            } else {
                                selectedKeys.add(key.code)
                            }
                        }
                        view.playSoundClick()
                    }
                )
            }
        }

        // ============================================================
        // ACTION BAR (chỉ Combo mode)
        // ============================================================
        if (!isInstantMode && selectedKeys.isNotEmpty()) {
            ActionBar(
                selectedKeys = selectedKeys,
                onBackspace = {
                    if (selectedKeys.isNotEmpty()) {
                        selectedKeys.removeAt(selectedKeys.lastIndex)
                    }
                    view.playSoundClick()
                },
                onSend = {
                    val ok = inputManager.sendComboFromCodes(selectedKeys.toList())
                    if (ok) {
                        context.showToast(
                            context.getString(
                                R.string.command_keys_sent,
                                selectedKeys.joinToString(" + ")
                            )
                        )
                        selectedKeys.clear()
                    } else {
                        context.showToast(context.getString(R.string.command_keys_invalid))
                    }
                    view.playSoundClick()
                }
            )
        }
    }
}

// ============================================================
// HELPERS — Instant mode
// ============================================================

/** Kiểm tra code có phải modifier và đang latch không. */
private fun isModifierLatched(code: String, latched: Set<String>): Boolean {
    val canonical = normalizeModifierCode(code) ?: return false
    return canonical in latched
}

/** Chuẩn hóa tên modifier (dùng cho UI). */
private fun normalizeModifierCode(code: String): String? = when (code.uppercase()) {
    "CTRL", "CONTROL", "LCTRL", "RCTRL" -> "CTRL"
    "SHIFT", "LSHIFT", "RSHIFT" -> "SHIFT"
    "ALT", "LALT", "RALT", "OPTION" -> "ALT"
    "CMD", "COMMAND" -> "CMD"
    "WIN", "SUPER", "META", "GUI" -> "WIN"
    else -> null
}

/** Xử lý chạm phím ở Instant mode — dùng InputManager. */
private fun handleInstantKey(
    key: KeyboardModel,
    inputManager: InputManager,
    context: Context
) {
    val canonicalMod = normalizeModifierCode(key.code)

    // 1. Modifier → toggle latch
    if (canonicalMod != null) {
        inputManager.toggleModifier(canonicalMod)
        return
    }

    // 2. Phím thường → gửi tap với modifier đang latch
    inputManager.sendKeyTap(key.code)
}

// ============================================================
// MODE CHIP
// ============================================================

@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
            selectedLabelColor = NeonCyan,
            containerColor = Color.Transparent,
            labelColor = TextSecondary
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = SurfaceCardBorder,
            selectedBorderColor = NeonCyan
        )
    )
}

// ============================================================
// PREVIEW
// ============================================================

@Composable
private fun SelectedKeysPreview(
    selectedKeys: List<String>,
    onRemove: (String) -> Unit,
    onClear: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = RoundedCornerShape(12.dp),
        color = ButtonDark,
        border = BorderStroke(1.dp, SurfaceCardBorder)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(
                            R.string.command_keys_selected_count,
                            selectedKeys.size
                        ),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                }
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.command_keys_clear_all),
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(items = selectedKeys, key = { it }) { code ->
                    KeyChip(label = code, onRemove = { onRemove(code) })
                }
            }
        }
    }
}

@Composable
private fun KeyChip(label: String, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = NeonCyan.copy(alpha = 0.2f),
        border = BorderStroke(1.dp, NeonCyan)
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = NeonCyan
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.command_keys_remove_key),
                tint = NeonCyan,
                modifier = Modifier
                    .size(14.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable { onRemove() }
            )
        }
    }
}

// ============================================================
// ACTION BAR
// ============================================================

@Composable
private fun ActionBar(
    selectedKeys: List<String>,
    onBackspace: () -> Unit,
    onSend: () -> Unit
) {
    // Kiểm tra có ít nhất 1 phím không phải modifier
    val hasRealKey = selectedKeys.any { code ->
        normalizeModifierCode(code) == null
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = ButtonDark,
        border = BorderStroke(1.dp, SurfaceCardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackspace,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                Icon(
                    painterResource(R.drawable.baseline_backspace),
                    contentDescription = stringResource(R.string.command_keys_backspace),
                    tint = TextMuted
                )
            }

            Button(
                enabled = hasRealKey,
                onClick = onSend,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonCyan,
                    contentColor = Color.Black,
                    disabledContainerColor = SurfaceCardBorder,
                    disabledContentColor = TextMuted
                )
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (hasRealKey) {
                        stringResource(
                            R.string.command_keys_send_with_keys,
                            selectedKeys.joinToString(" + ")
                        )
                    } else {
                        stringResource(R.string.command_keys_pick_more)
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }
        }
    }
}

// ============================================================
// KEY BUTTON
// ============================================================

@Composable
private fun KeyButton(
    item: KeyboardModel,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .height(50.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        color = when {
            isSelected -> NeonCyan.copy(alpha = 0.3f)
            item.isCommand -> NeonCyan.copy(alpha = 0.12f)
            else -> ButtonDark
        },
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = when {
                isSelected -> NeonCyan
                item.isCommand -> NeonCyan.copy(alpha = 0.6f)
                else -> SurfaceCardBorder
            }
        )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            if (item.icon.isNotBlank()) {
                IconAny(
                    text = item.label,
                    icon = item.icon,
                    color = when {
                        isSelected -> NeonCyan
                        item.isCommand -> NeonCyan
                        else -> TextPrimary
                    },
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = item.label,
                    fontSize = if (item.label.length > 3) 10.sp else 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        isSelected -> NeonCyan
                        item.isCommand -> NeonCyan
                        else -> TextPrimary
                    },
                    maxLines = 1
                )
            }
        }
    }
}