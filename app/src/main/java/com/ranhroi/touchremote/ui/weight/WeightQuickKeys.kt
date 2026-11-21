package com.ranhroi.touchremote.ui.weight

import android.view.View
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.ranhroi.touchremote.R
import com.ranhroi.touchremote.models.CombineSettingModel
import com.ranhroi.touchremote.models.DropMenuModel
import com.ranhroi.touchremote.models.QuickKeysModel
import com.ranhroi.touchremote.send.InputManager
import com.ranhroi.touchremote.ui.components.ButtonWeight
import com.ranhroi.touchremote.ui.components.DotsLoadingIndicator
import com.ranhroi.touchremote.ui.components.DotsOrientation
import com.ranhroi.touchremote.ui.components.MoreVertDropdown
import com.ranhroi.touchremote.ui.navigation.NavRoute
import com.ranhroi.touchremote.ui.screens.quickkey.QuickKeysSetupViewModel
import com.ranhroi.touchremote.utils.playSoundClick
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds


@Composable
fun WeightQuickKeys(
    navController: NavHostController,
    config: CombineSettingModel,
    inputManager: InputManager,
    allQuickKeys: List<QuickKeysModel>,
    quickKeysSetupViewModel: QuickKeysSetupViewModel
) {
    val view = LocalView.current
    var isLoading by rememberSaveable { mutableStateOf(true) }
    var showSelector by remember { mutableStateOf(false) }

    // ✅ Đọc presetId đã lưu từ ViewModel
    val selectedPresetId by quickKeysSetupViewModel.selectedPresetId.collectAsStateWithLifecycle()

    // ✅ Load 1 lần khi mở app
    LaunchedEffect(Unit) {
        quickKeysSetupViewModel.loadSelectedPresetId()
        delay(5000.milliseconds)
        isLoading = false
    }

    val launchKeys = remember(allQuickKeys) { allQuickKeys.filter { it.openApp } }
    val softwareKeys = remember(allQuickKeys) { allQuickKeys.filter { !it.openApp } }
    val availablePresets = remember(softwareKeys) {
        softwareKeys.map { it.preset to it.presetId }
            .distinctBy { it.second }
            .sortedBy { it.first }
    }

    val displayedKeys = remember(softwareKeys, selectedPresetId) {
        if (selectedPresetId == null) softwareKeys
        else softwareKeys.filter { it.presetId == selectedPresetId }
    }

    Column(
        modifier = if (config.isRotation) Modifier
            .wrapContentWidth()
            .fillMaxHeight() else Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        if (allQuickKeys.isNotEmpty()) {

            // ========================================================
            //         Phím cuối cùng LUÔN là Select
            // ========================================================
            if (softwareKeys.isNotEmpty()) {
                when (config.isRotation) {
                    true -> SoftwareDrawerLayout(
                        view = view,
                        config = config,
                        launchKeys = launchKeys,
                        quickKeys = displayedKeys,
                        selectedPresetId = selectedPresetId,
                        availablePresets = availablePresets,
                        showSelector = showSelector,
                        onToggleSelector = { showSelector = !showSelector },
                        onPresetSelected = { presetId ->
                            val name = availablePresets
                                .firstOrNull { it.second == presetId }?.first ?: ""
                            quickKeysSetupViewModel.selectPreset(presetId, name)
                            showSelector = false
                        },
                        onCreate = {
                            navController.navigate(NavRoute.SetQuickKeys.createRoute(0))
                        },
                        onEdit = { quickKey ->
                            quickKeysSetupViewModel.startEditQuickKey(quickKey)
                            navController.navigate(NavRoute.SetQuickKeys.createRoute(quickKey.id))
                        },
                        onDelete = { quickKey ->
                            quickKeysSetupViewModel.removeQuickKeys(quickKey)
                        },
                        inputManager = inputManager
                    )

                    false -> SoftwareTopCenterLayout(
                        view = view,
                        config = config,
                        launchKeys = launchKeys,
                        quickKeys = displayedKeys,
                        selectedPresetId = selectedPresetId,
                        availablePresets = availablePresets,
                        showSelector = showSelector,
                        onToggleSelector = { showSelector = !showSelector },
                        onPresetSelected = { presetId ->
                            val name = availablePresets
                                .firstOrNull { it.second == presetId }?.first ?: ""
                            quickKeysSetupViewModel.selectPreset(presetId, name)
                            showSelector = false
                        },
                        onCreate = {
                            navController.navigate(NavRoute.SetQuickKeys.createRoute(0))
                        },
                        onEdit = { quickKey ->
                            navController.navigate(NavRoute.SetQuickKeys.createRoute(quickKey.id))
                        },
                        onDelete = { quickKey ->
                            quickKeysSetupViewModel.removeQuickKeys(quickKey)
                        },
                        inputManager = inputManager,
                    )
                }
            }

        } else {
            if (isLoading) {
                Box(
                    modifier = Modifier.wrapContentSize(),
                    contentAlignment = Alignment.Center
                ) {
                    DotsLoadingIndicator(
                        orientation = if (config.isRotation)
                            DotsOrientation.VERTICAL
                        else DotsOrientation.HORIZONTAL
                    )
                }
            } else {
                Box(
                    modifier = Modifier.wrapContentSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.quickkey_empty))
                }
            }
        }
    }
}


// ============================================================
// HÀNG 2a — SOFTWARE Drawer (rotation) — Select luôn ở vị trí cuối
// ============================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SoftwareDrawerLayout(
    view: View,
    config: CombineSettingModel,
    launchKeys: List<QuickKeysModel>,
    quickKeys: List<QuickKeysModel>,
    selectedPresetId: String?,
    availablePresets: List<Pair<String, String>>,
    showSelector: Boolean,
    onToggleSelector: () -> Unit,
    onPresetSelected: (String?) -> Unit,
    onCreate: () -> Unit,
    onEdit: (QuickKeysModel) -> Unit,
    onDelete: (QuickKeysModel) -> Unit,
    inputManager: InputManager,
) {
    Column(modifier = Modifier.wrapContentSize()) {
        LazyHorizontalGrid(
            rows = GridCells.Fixed(6),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            reverseLayout = true,
            modifier = Modifier.wrapContentSize()
        ) {
            itemsIndexed(launchKeys) { _, quickKey ->
                var expanded by remember { mutableStateOf(false) }
                ButtonWeight(
                    modifier = Modifier
                        .width(50.dp),
                    colorStroke = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(3.dp),
                    onClick = {
                       if (inputManager.executeQuickKeys(quickKey)) view.playSoundClick()
                    },
                    onLongClick = { expanded = true },
                    iconName = quickKey.icon,
                    text = if (config.isButtonText) quickKey.name else null,
                    rotationDegrees = 90f,
                    aspectRatio = 0.6f
                ) {
                    MoreVertDropdown(
                        view = view,
                        expanded = expanded,
                        onDismiss = { expanded = false },
                        menuItems = listOf(
                            DropMenuModel(
                                text = stringResource(R.string.quickkey_edit),
                                onClick = { onEdit(quickKey) },
                                icon = "baseline_edit"
                            ),
                            DropMenuModel(
                                text = stringResource(R.string.quickkey_remove),
                                onClick = {
                                    onDelete(quickKey)
                                },
                                icon = "baseline_remove",
                                isDestructive = true
                            ),
                        )
                    )
                }
            }

            itemsIndexed(quickKeys) { _, quickKey ->
                var expanded by remember { mutableStateOf(false) }
                ButtonWeight(
                    modifier = Modifier.width(50.dp),
                    colorStroke = Color.Gray.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(3.dp),
                    onClick = {
                        if (inputManager.executeQuickKeys(quickKey)) view.playSoundClick()
                    },
                    onLongClick = { expanded = true },
                    iconName = quickKey.icon,
                    text = if (config.isButtonText) quickKey.name else null,
                    rotationDegrees = 90f,
                    aspectRatio = 0.6f
                ) {
                    MoreVertDropdown(
                        view = view,
                        expanded = expanded,
                        onDismiss = { expanded = false },
                        menuItems = listOf(
                            DropMenuModel(
                                text = stringResource(R.string.quickkey_edit),
                                onClick = { onEdit(quickKey) },
                                icon = "baseline_edit"
                            ),
                            DropMenuModel(
                                text = stringResource(R.string.quickkey_remove),
                                onClick = {
                                    onDelete(quickKey)
                                },
                                icon = "baseline_remove",
                                isDestructive = true
                            ),
                        )
                    )
                }
            }

            item {
                ButtonWeight(
                    modifier = Modifier.width(50.dp),
                    colorStroke = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(3.dp),
                    onClick = {
                        onToggleSelector()
                        view.playSoundClick()
                    },
                    iconName = "baseline_arrow_drop_down",
                    text = if (config.isButtonText) {
                        selectedPresetId?.let { id ->
                            availablePresets.firstOrNull { it.second == id }?.first
                        }?.take(4) ?: stringResource(R.string.quickkey_preset_all)
                    } else null,
                    rotationDegrees = 90f,
                    aspectRatio = 0.6f
                ) {
                    if (showSelector) {
                        MoreVertDropdown(
                            view = view,
                            expanded = showSelector,
                            onDismiss = onToggleSelector,
                            menuItems = buildList {
                                add(
                                    DropMenuModel(
                                        text = stringResource(R.string.quickkey_preset_all),
                                        onClick = {
                                            onPresetSelected(null)
                                            onToggleSelector()
                                        },
                                        icon = "baseline_list_alt"
                                    )
                                )
                                availablePresets.forEach { (name, id) ->
                                    add(
                                        DropMenuModel(
                                            text = name,
                                            onClick = {
                                                onPresetSelected(id)
                                                onToggleSelector()
                                            },
                                            icon = "baseline_apps"
                                        )
                                    )
                                }
                                add(
                                    DropMenuModel(
                                        text = stringResource(R.string.quickkey_create),
                                        onClick = onCreate,
                                        icon = "baseline_create"
                                    ),
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// HÀNG 2b — SOFTWARE TopCenter — Select luôn ở vị trí cuối
// ============================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SoftwareTopCenterLayout(
    view: View,
    config: CombineSettingModel,
    launchKeys: List<QuickKeysModel>,
    quickKeys: List<QuickKeysModel>,
    selectedPresetId: String?,
    availablePresets: List<Pair<String, String>>,
    showSelector: Boolean,
    onToggleSelector: () -> Unit,
    onPresetSelected: (String?) -> Unit,
    onCreate: () -> Unit,
    onEdit: (QuickKeysModel) -> Unit,
    onDelete: (QuickKeysModel) -> Unit,
    inputManager: InputManager,
) {
    Column(modifier = Modifier.wrapContentSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {

            itemsIndexed(launchKeys) { _, quickKey ->
                var expanded by remember { mutableStateOf(false) }
                ButtonWeight(
                    modifier = Modifier.wrapContentSize(),
                    colorStroke = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(3.dp),
                    onClick = {
                        if (inputManager.executeQuickKeys(quickKey)) view.playSoundClick()
                    },
                    onLongClick = { expanded = true },
                    iconName = quickKey.icon,
                    text = if (config.isButtonText) quickKey.name else null
                ) {
                    MoreVertDropdown(
                        view = view,
                        expanded = expanded,
                        onDismiss = { expanded = false },
                        menuItems = listOf(
                            DropMenuModel(
                                text = stringResource(R.string.quickkey_edit),
                                onClick = { onEdit(quickKey) },
                                icon = "baseline_edit"
                            ),
                            DropMenuModel(
                                text = stringResource(R.string.quickkey_remove),
                                onClick = {
                                    onDelete(quickKey)
                                    view.playSoundClick()
                                },
                                icon = "baseline_remove",
                                isDestructive = true
                            ),
                        )
                    )
                }
            }

            itemsIndexed(quickKeys) { _, quickKey ->
                var expanded by remember { mutableStateOf(false) }
                ButtonWeight(
                    modifier = Modifier.wrapContentSize(),
                    colorStroke = Color.Gray.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(3.dp),
                    onClick = {
                        if (inputManager.executeQuickKeys(quickKey)) view.playSoundClick()
                    },
                    onLongClick = { expanded = true },
                    iconName = quickKey.icon,
                    text = if (config.isButtonText) quickKey.name else null
                ) {
                    MoreVertDropdown(
                        view = view,
                        expanded = expanded,
                        onDismiss = { expanded = false },
                        menuItems = listOf(
                            DropMenuModel(
                                text = stringResource(R.string.quickkey_edit),
                                onClick = { onEdit(quickKey) },
                                icon = "baseline_edit"
                            ),
                            DropMenuModel(
                                text = stringResource(R.string.quickkey_remove),
                                onClick = {
                                    onDelete(quickKey)
                                },
                                icon = "baseline_remove",
                                isDestructive = true
                            ),
                        )
                    )
                }
            }

            item {
                ButtonWeight(
                    modifier = Modifier.wrapContentSize(),
                    colorStroke = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(3.dp),
                    onClick = {
                        onToggleSelector()
                        view.playSoundClick()
                    },
                    iconName = "baseline_arrow_drop_down",
                    text = if (config.isButtonText) {
                        selectedPresetId?.let { id ->
                            availablePresets.firstOrNull { it.second == id }?.first
                        } ?: stringResource(R.string.quickkey_preset_all)
                    } else null
                ) {
                    if (showSelector) {
                        MoreVertDropdown(
                            view = view,
                            expanded = showSelector,
                            onDismiss = onToggleSelector,
                            menuItems = buildList {
                                add(
                                    DropMenuModel(
                                        text = stringResource(R.string.quickkey_preset_all),
                                        onClick = {
                                            onPresetSelected(null)
                                            onToggleSelector()
                                        },
                                        icon = "baseline_list_alt"
                                    )
                                )
                                availablePresets.forEach { (name, id) ->
                                    add(
                                        DropMenuModel(
                                            text = name,
                                            onClick = {
                                                onPresetSelected(id)
                                                onToggleSelector()
                                            },
                                            icon = "baseline_apps"
                                        )
                                    )
                                }
                                add(
                                    DropMenuModel(
                                        text = stringResource(R.string.quickkey_create),
                                        onClick = onCreate,
                                        icon = "baseline_create"
                                    ),
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}