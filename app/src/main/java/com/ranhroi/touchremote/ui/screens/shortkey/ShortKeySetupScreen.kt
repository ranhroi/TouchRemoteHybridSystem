package com.ranhroi.touchremote.ui.screens.shortkey

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.ranhroi.touchremote.R
import com.ranhroi.touchremote.models.DropMenuModel
import com.ranhroi.touchremote.models.ShortKeyModel
import com.ranhroi.touchremote.ui.components.DotsLoadingIndicator
import com.ranhroi.touchremote.ui.components.MoreVertDropdown
import com.ranhroi.touchremote.ui.dialogs.ConfirmDialog
import com.ranhroi.touchremote.ui.dialogs.DialogInsertShortKey
import com.ranhroi.touchremote.ui.dialogs.DialogUpdateShortKey
import com.ranhroi.touchremote.utils.playSoundClick
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortKeySetupScreen(
    navController: NavHostController = rememberNavController(),
    shortKeySetupViewModel: ShortKeySetupViewModel = hiltViewModel()
) {
    val allShortKeys by shortKeySetupViewModel.allShortKeys.collectAsState()
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    val options = remember(allShortKeys) {
        val fromData = allShortKeys.map { it.type }.distinct()
        (fromData).distinct()
    }

    var expanded by remember { mutableStateOf(false) }
    var isDialogInsert by remember { mutableStateOf(false) }

    val textFieldState = rememberTextFieldState("floating")
    var filterShortKeys by remember { mutableStateOf<List<ShortKeyModel>>(emptyList()) }

    LaunchedEffect(allShortKeys, textFieldState.text) {
        val result = allShortKeys.filter { it.type == textFieldState.text.toString() }
        filterShortKeys = result.ifEmpty { allShortKeys }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.shortcut_setup_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        scope.launch {
                            if (navController.previousBackStackEntry != null) {
                                navController.popBackStack()
                            }
                        }
                        view.playSoundClick()
                    }) {
                        Icon(painterResource(R.drawable.baseline_arrow_back), contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isDialogInsert = true
                        view.playSoundClick()
                    }) {
                        Icon(painterResource(R.drawable.baseline_add), contentDescription = "baseline_add")
                    }
                }
            )
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .padding(innerPadding)
        ) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                TextField(
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    readOnly = true,
                    value = textFieldState.text.toString(),
                    onValueChange = { textFieldState.setTextAndPlaceCursorAtEnd(it) },
                    label = { Text(stringResource(R.string.shortcut_setup_select_option)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                )

                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.forEach { selectionOption ->
                        DropdownMenuItem(
                            text = { Text(selectionOption) },
                            onClick = {
                                textFieldState.setTextAndPlaceCursorAtEnd(selectionOption)
                                expanded = false
                            }
                        )
                    }
                }
            }
            ShortKeyListScreen(shortKeys = filterShortKeys, shortKeySetupViewModel)
        }
    }


    if (isDialogInsert) {
        DialogInsertShortKey(
            type = filterShortKeys[0].type,
            shortKeySetupViewModel = shortKeySetupViewModel,
            onDismiss = {
                isDialogInsert = false
            }
        )
    }

}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShortKeyListScreen(
    shortKeys: List<ShortKeyModel> = emptyList(),
    shortKeySetupViewModel: ShortKeySetupViewModel
) {
    val view = LocalView.current
    var clickShortKeyId by remember { mutableStateOf<Int?>(null) }
    var isDialogUpdate by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var selectedShortKey by remember { mutableStateOf<ShortKeyModel?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp)
    ) {
        if (shortKeys.isNotEmpty()) {
            itemsIndexed(shortKeys) { _, shortKey ->
                var isVisibleDrop by remember { mutableStateOf(false) }

                Card(
                    modifier = Modifier
                        .clip(RoundedCornerShape(0.dp))
                        .fillMaxWidth()
                        .heightIn(min = 60.dp, max = 120.dp)
                        .combinedClickable(
                            onClick = {
                                // Xử lý khi click
                                clickShortKeyId = if (clickShortKeyId == shortKey.id) null else shortKey.id
                            },
                            onLongClick = {
                                // Xử lý khi giữ lâu
                                isVisibleDrop = true
                            }
                        ),
                ) {
                    ShortKeyItem(shortKey)
                    MoreVertDropdown(
                        view = view,
                        expanded = isVisibleDrop,
                        onDismiss = { isVisibleDrop = false },
                        menuItems = listOf(
                            DropMenuModel(
                                text = stringResource(R.string.shortcut_setup_edit),
                                onClick = {
                                    clickShortKeyId = null
                                    isDialogUpdate = true
                                    isVisibleDrop = false
                                    selectedShortKey = shortKey
                                },
                                icon = "baseline_launch"
                            ),
                            DropMenuModel(
                                text = stringResource(R.string.shortcut_setup_remove),
                                onClick = {
                                    isVisibleDrop = false
                                    selectedShortKey = shortKey
                                    showDeleteConfirm = true
                                },
                                icon = "baseline_remove",
                                isDestructive = true
                            )
                        )
                    )
                }
                Divider(modifier = Modifier.padding(vertical = 8.dp))
            }
        } else {
            item {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    DotsLoadingIndicator()
                }
            }
        }
    }

    if (isDialogUpdate && selectedShortKey != null) {
        DialogUpdateShortKey(
            selectedShortKey!!,
            shortKeySetupViewModel,
            onDismiss = {
                isDialogUpdate = false
                selectedShortKey = null
            }
        )
    }

    if (showDeleteConfirm && selectedShortKey != null) {
        ConfirmDialog(
            show = showDeleteConfirm,
            title = stringResource(R.string.server_confirm_delete_title),
            message = stringResource(R.string.server_confirm_delete_message, selectedShortKey?.name ?: ""),
            onConfirm = {
                shortKeySetupViewModel.removeShortKey(selectedShortKey!!)
                showDeleteConfirm = false
                selectedShortKey = null
            },
            onDismiss = {
                showDeleteConfirm = false
                selectedShortKey = null
            }
        )
    }
}