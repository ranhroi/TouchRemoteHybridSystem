package com.ranhroi.touchremote.ui.screens.quickkey

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.ranhroi.touchremote.R
import com.ranhroi.touchremote.models.KeyboardModel
import com.ranhroi.touchremote.ui.components.IconPickerDropdown
import com.ranhroi.touchremote.ui.keyboard.CommandKeysRepository
import com.ranhroi.touchremote.ui.theme.ButtonDark
import com.ranhroi.touchremote.ui.theme.NeonCyan
import com.ranhroi.touchremote.ui.theme.SurfaceCardBorder
import com.ranhroi.touchremote.ui.theme.TextMuted
import com.ranhroi.touchremote.ui.theme.TextPrimary
import com.ranhroi.touchremote.ui.theme.TextSecondary
import com.ranhroi.touchremote.utils.getStringSafe
import com.ranhroi.touchremote.utils.playSoundClick
import com.ranhroi.touchremote.utils.showToast

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickKeysSetupScreen(
    navController: NavHostController,
    quickKeyId: Int = 0,
    quickKeysSetupViewModel: QuickKeysSetupViewModel = hiltViewModel()
) {
    val view = LocalView.current
    val context = LocalContext.current

    // ===== State từ ViewModel =====
    val editing by quickKeysSetupViewModel.editingQuickKey.collectAsStateWithLifecycle()
    val selectedKeys by quickKeysSetupViewModel.selectedKeys.collectAsStateWithLifecycle()
    val selectedPresetId by quickKeysSetupViewModel.selectedPresetId.collectAsStateWithLifecycle()
    val allQuickKeys by quickKeysSetupViewModel.allQuickKeys.collectAsStateWithLifecycle()

    // ===== State UI local =====
    var selectedGroupIndex by remember { mutableIntStateOf(0) }
    var presetMenuExpanded by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }
    var showAddPresetField by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val groups = CommandKeysRepository.allGroups
    val isEditMode = quickKeyId != 0

    // ===== Preset hiển thị trong dropdown =====
    // Luôn bao gồm preset hiện tại của editing (nếu có) để không bị mất khi mở dropdown
    val availablePresets = remember(allQuickKeys, editing.preset, editing.presetId) {
        val fromData = allQuickKeys
            .filter { it.preset.isNotBlank() }
            .map { it.preset to it.presetId }
            .distinctBy { it.second }

        val extra = if (
            editing.preset.isNotBlank() &&
            fromData.none { it.second == editing.presetId }
        ) {
            listOf(editing.preset to editing.presetId)
        } else emptyList()

        (fromData + extra).sortedBy { it.first }
    }

    // ============================================================
    // LIFECYCLE
    // ============================================================
    LaunchedEffect(quickKeyId, allQuickKeys) {
        if (quickKeyId != 0) {
            val target = allQuickKeys.firstOrNull { it.id == quickKeyId }
            if (target != null && editing.id != target.id) {
                quickKeysSetupViewModel.startEditQuickKey(target)
            }
        } else {
            if (editing.id == 0 && editing.name.isBlank() && selectedKeys.isEmpty()) {
                quickKeysSetupViewModel.startNewQuickKey()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { quickKeysSetupViewModel.cancelEditing() }
    }

    // ===== Save / Cancel =====
    fun handleSave() {
        view.playSoundClick()
        if (editing.name.isBlank()) {
            context.showToast(context.getStringSafe(R.string.quickkey_name_empty))
            return
        }
        if (selectedKeys.isEmpty()) {
            context.showToast(context.getStringSafe(R.string.quickkey_no_keys_found))
            return
        }
        if (editing.preset.isBlank()) {
            context.showToast(context.getStringSafe(R.string.quickkey_preset_choose))
            return
        }
        if (quickKeysSetupViewModel.saveEditingQuickKey()) {
            context.showToast(context.getStringSafe(R.string.quickkey_saved_success))
            navController.popBackStack()
        } else {
            context.showToast(context.getStringSafe(R.string.quickkey_name_empty))
        }
    }

    fun handleCancel() {
        quickKeysSetupViewModel.cancelEditing()
        navController.popBackStack()
        view.playSoundClick()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (isEditMode) R.string.quickkey_title_edit
                            else R.string.quickkey_title_add
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { handleCancel() }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.quickkey_back),
                        )
                    }
                }
            )
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .background(Color.Black)
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // ============================================================
            // NAME + SAVE
            // ============================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = editing.name,
                    onValueChange = { quickKeysSetupViewModel.setName(it) },
                    label = {
                        Text(stringResource(R.string.quickkey_name_label), fontSize = 12.sp)
                    },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.quickkey_name_placeholder),
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { handleSave() }),
                    textStyle = TextStyle(fontSize = 14.sp, color = TextPrimary),
                    shape = RoundedCornerShape(5.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        errorContainerColor = Color.Transparent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = NeonCyan,
                        focusedLabelColor = NeonCyan,
                        unfocusedLabelColor = TextMuted
                    )
                )

                IconButton(
                    onClick = { handleSave() },
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.Green.copy(alpha = 0.1f))
                ) {
                    Icon(
                        painter = painterResource(R.drawable.baseline_save),
                        contentDescription = stringResource(R.string.quickkey_save),
                        tint = Color.Green
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ============================================================
            // ICON PICKER + PRESET SELECTOR
            // ============================================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconPickerDropdown(
                    icon = editing.icon,
                    onSetIcon = { quickKeysSetupViewModel.setIcon(it) },
                    onDone = { handleSave() }
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // ---- Type switch ----
                    Row(
                        modifier = Modifier
                            .wrapContentSize()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.quickkey_type_label), fontSize = 12.sp)
                        Switch(
                            modifier = Modifier
                                .scale(0.7f)
                                .defaultMinSize()
                                .background(Color.Gray),
                            checked = editing.openApp,
                            onCheckedChange = {
                                quickKeysSetupViewModel.setType(it)
                                view.playSoundClick()
                            }
                        )
                        Text(
                            text = if (editing.openApp) stringResource(R.string.quickkey_type_launch)
                            else stringResource(R.string.quickkey_type_shortcut),
                            fontSize = 12.sp
                        )
                    }

                    // ---- Preset selector button ----
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = NeonCyan.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .clickable { presetMenuExpanded = true }
                            .wrapContentSize()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = editing.preset.ifBlank {
                                    stringResource(R.string.quickkey_preset_choose)
                                },
                                fontSize = 12.sp,
                                color = NeonCyan,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                painter = painterResource(R.drawable.baseline_arrow_drop_down),
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = presetMenuExpanded,
                        onDismissRequest = {
                            presetMenuExpanded = false
                            showAddPresetField = false
                            newPresetName = ""
                        },
                    ) {
                        // ---- "All" ----
                        val isAllSelected = selectedPresetId == null && editing.preset.isBlank()
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.quickkey_preset_all),
                                    fontSize = 13.sp,
                                    color = if (isAllSelected) NeonCyan else TextPrimary,
                                    fontWeight = if (isAllSelected) FontWeight.Bold
                                    else FontWeight.Normal
                                )
                            },
                            onClick = {
                                quickKeysSetupViewModel.selectAllPresets()
                                presetMenuExpanded = false
                                view.playSoundClick()
                            }
                        )

                        // ---- Danh sách preset có sẵn ----
                        availablePresets.forEach { (presetName, presetId) ->
                            val isSelected = selectedPresetId == presetId
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = presetName,
                                        fontSize = 13.sp,
                                        color = if (isSelected) NeonCyan else TextPrimary,
                                        fontWeight = if (isSelected) FontWeight.Bold
                                        else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    quickKeysSetupViewModel.selectPreset(presetId, presetName)
                                    presetMenuExpanded = false
                                    view.playSoundClick()
                                }
                            )
                        }

                        HorizontalDivider(color = SurfaceCardBorder, thickness = 1.dp)

                        // ---- Thêm preset mới ----
                        if (!showAddPresetField) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            painter = painterResource(R.drawable.baseline_add),
                                            contentDescription = null,
                                            tint = NeonCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.quickkey_preset_add_new),
                                            fontSize = 13.sp,
                                            color = NeonCyan,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                },
                                onClick = {
                                    showAddPresetField = true
                                    view.playSoundClick()
                                }
                            )
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedTextField(
                                    value = newPresetName,
                                    onValueChange = { newPresetName = it },
                                    placeholder = {
                                        Text(
                                            text = stringResource(R.string.quickkey_preset_name_placeholder),
                                            fontSize = 12.sp,
                                            color = TextMuted
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    singleLine = true,
                                    textStyle = TextStyle(fontSize = 13.sp, color = TextPrimary),
                                    shape = RoundedCornerShape(6.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = NeonCyan,
                                        unfocusedBorderColor = SurfaceCardBorder,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        cursorColor = NeonCyan
                                    )
                                )

                                IconButton(
                                    onClick = {
                                        val name = newPresetName.trim()
                                        if (name.isNotBlank()) {
                                            quickKeysSetupViewModel.addAndSelectPreset(name)
                                            newPresetName = ""
                                            showAddPresetField = false
                                            presetMenuExpanded = false
                                            view.playSoundClick()
                                        }
                                    },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.Green.copy(alpha = 0.15f))
                                ) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = stringResource(R.string.quickkey_preset_add_confirm),
                                        tint = Color.Green,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        newPresetName = ""
                                        showAddPresetField = false
                                        view.playSoundClick()
                                    },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.Red.copy(alpha = 0.15f))
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.quickkey_preset_add_cancel),
                                        tint = Color.Red,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ============================================================
            // SEARCH BAR
            // ============================================================
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = stringResource(R.string.quickkey_search_hint),
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                painterResource(R.drawable.baseline_close),
                                contentDescription = stringResource(R.string.quickkey_search_clear),
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = SurfaceCardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = NeonCyan
                )
            )

            Spacer(Modifier.height(8.dp))

            // ============================================================
            // PREVIEW tổ hợp đã chọn
            // ============================================================
            if (selectedKeys.isNotEmpty()) {
                SelectedKeysPreview(
                    selectedKeys = selectedKeys,
                    onRemove = {
                        quickKeysSetupViewModel.removeKey(it)
                        view.playSoundClick()
                    },
                    onClear = {
                        quickKeysSetupViewModel.clearKeys()
                        view.playSoundClick()
                    }
                )
                Spacer(Modifier.height(8.dp))
            }

            // ============================================================
            // TAB CHIPS — Nhóm phím
            // ============================================================
            LazyVerticalGrid(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                columns = GridCells.Fixed(4)
            ) {
                itemsIndexed(groups) { index, group ->
                    val selected = selectedGroupIndex == index
                    FilterChip(
                        selected = selected,
                        onClick = {
                            selectedGroupIndex = index
                            searchQuery = ""
                            view.playSoundClick()
                        },
                        label = {
                            Text(
                                text = stringResource(group.titleRes),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        shape = RoundedCornerShape(8.dp),
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
            }

            Spacer(Modifier.height(8.dp))

            // ============================================================
            // KEY GRID
            // ============================================================
            val currentGroup = groups[selectedGroupIndex]
            val keyItems = if (searchQuery.isBlank()) {
                currentGroup.items
            } else {
                CommandKeysRepository.search(searchQuery)
            }

            if (keyItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.quickkey_no_keys_found),
                        fontSize = 13.sp,
                        color = TextMuted
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 60.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(items = keyItems, key = { it.code }) { key ->
                        QuickKeyButton(
                            item = key,
                            isSelected = selectedKeys.contains(key.code),
                            onClick = {
                                quickKeysSetupViewModel.toggleKey(key.code)
                                view.playSoundClick()
                            }
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// PREVIEW — Hiển thị các phím đã chọn
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
        color = NeonCyan.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f))
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
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(
                            R.string.quickkey_combination_count,
                            selectedKeys.size
                        ),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan
                    )
                }

                IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.quickkey_clear_all),
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
            Spacer(Modifier.width(2.dp))
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.quickkey_remove_key),
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
// KEY BUTTON
// ============================================================
@Composable
private fun QuickKeyButton(
    item: KeyboardModel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .height(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        color = when {
            isSelected -> NeonCyan.copy(alpha = 0.3f)
            item.isCommand -> NeonCyan.copy(alpha = 0.08f)
            else -> ButtonDark
        },
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = when {
                isSelected -> NeonCyan
                item.isCommand -> NeonCyan.copy(alpha = 0.4f)
                else -> SurfaceCardBorder
            }
        )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
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