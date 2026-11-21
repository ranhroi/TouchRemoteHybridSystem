package com.ranhroi.touchremote.ui.weight


import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ranhroi.touchremote.R
import com.ranhroi.touchremote.models.CombineSettingModel
import com.ranhroi.touchremote.models.DropMenuModel
import com.ranhroi.touchremote.models.ShortKeyModel
import com.ranhroi.touchremote.send.InputManager
import com.ranhroi.touchremote.ui.components.ButtonWeight
import com.ranhroi.touchremote.ui.components.DotsLoadingIndicator
import com.ranhroi.touchremote.ui.components.DotsOrientation
import com.ranhroi.touchremote.ui.components.MoreVertDropdown
import com.ranhroi.touchremote.ui.dialogs.DialogInsertPath
import com.ranhroi.touchremote.ui.dialogs.DialogUpdatePath
import com.ranhroi.touchremote.ui.pager.path.PathModel
import com.ranhroi.touchremote.ui.pager.path.PathsViewModel
import com.ranhroi.touchremote.utils.playSoundClick
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds


@Composable
fun WeightShortKeysBrowser(
    view: View,
    config: CombineSettingModel,
    inputManager: InputManager,
    shortKeys: List<ShortKeyModel>,
    pathsViewModel: PathsViewModel = hiltViewModel(),
) {
    val paths = pathsViewModel.allPaths.collectAsState()
    val pathWeb = paths.value.filter { it.type == "browser openurl" }

    var isLoading by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(5000.milliseconds)
        isLoading = false
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
        if (shortKeys.isNotEmpty()) {
            when (config.isRotation) {
                true -> BrowserDrawerLayout(
                    view = view,
                    config = config,
                    paths = pathWeb,
                    shortKeys = shortKeys,
                    inputManager = inputManager,
                    pathsViewModel = pathsViewModel
                )

                false -> {
                    BrowserTopCenterLayout(
                        view = view,
                        config = config,
                        paths = pathWeb,
                        shortKeys = shortKeys,
                        inputManager = inputManager,
                        pathsViewModel = pathsViewModel
                    )
                }
            }
        } else {
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .wrapContentSize(),
                    contentAlignment = Alignment.Center
                ) {
                    DotsLoadingIndicator(orientation = if (config.isRotation) DotsOrientation.VERTICAL else DotsOrientation.HORIZONTAL)
                }
            } else {
                Box(
                    modifier = Modifier
                        .wrapContentSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No Browser ShortKeys")
                }
            }
        }
    }
}

// 🔥 CƠ CHẾ 1: Layout cho drawer bên phải (ngang) - ĐÃ THÊM 2 NÚT SCAN VÀ SELECT
@Composable
fun BrowserDrawerLayout(
    view: View,
    config: CombineSettingModel,
    paths: List<PathModel>,
    shortKeys: List<ShortKeyModel>,
    inputManager: InputManager,
    pathsViewModel: PathsViewModel,
) {
    var isDialogUpdate by remember { mutableStateOf(false) }
    var isDialogInsert by remember { mutableStateOf(false) }
    var selectedPath by remember { mutableStateOf<PathModel?>(null) }

    LazyHorizontalGrid(
        rows = GridCells.Fixed(6), // Chia đều thành 6 cột cố định tràn viền ngang
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        itemsIndexed(shortKeys) { _, shortKey ->
            ButtonWeight(
                modifier = Modifier.width(50.dp),
                colorStroke = Color.Gray.copy(alpha = 0.6f),
                shape = RoundedCornerShape(3.dp),
                onClick = {
                    if (inputManager.executeShortKey(shortKey)) view.playSoundClick()
                },
                iconName = shortKey.icon,
                text = if (config.isButtonText) shortKey.name else null,
                rotationDegrees = 90f,
                aspectRatio = 0.7f
            )
        }

        itemsIndexed(paths) { _, path ->
            var expanded by remember { mutableStateOf(false) }
            ButtonWeight(
                modifier = Modifier.width(50.dp),// Ép ô lấp đầy không gian được chia tự động
                colorStroke = Color.Gray.copy(alpha = 0.6f),
                shape = RoundedCornerShape(3.dp),
                onClick = { if (inputManager.handlePath(path)) view.playSoundClick() },
                onLongClick = { expanded = true },
                iconName = path.icon,
                text = path.name,
                rotationDegrees = 90f,
                aspectRatio = 0.7f
            ) {
                MoreVertDropdown(
                    view = view,
                    expanded = expanded,
                    onDismiss = { expanded = false },
                    menuItems = listOf(
                        DropMenuModel(
                            text = stringResource(R.string.path_edit),
                            onClick = {
                                selectedPath = path
                                isDialogUpdate = true
                            },
                            icon = "baseline_edit"
                        ),
                        DropMenuModel(
                            text = stringResource(R.string.path_create),
                            onClick = {
                                selectedPath = path
                                isDialogInsert = true
                            },
                            icon = "baseline_add"
                        ),
                        DropMenuModel(
                            text = stringResource(R.string.path_remove),
                            onClick = { pathsViewModel.removePath(path) },
                            icon = "baseline_remove",
                            isDestructive = true
                        )
                    )
                )
            }
        }

        // Trường hợp danh sách trống, nút Add Link cũng chiếm trọn ô được chỉ định
        if (paths.isEmpty()) {
            item {
                ButtonWeight(
                    modifier = Modifier.fillMaxSize(),
                    colorStroke = Color.LightGray.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(3.dp),
                    onClick = {
                        isDialogInsert = true
                        selectedPath = PathModel(type = "openurl", path = "")
                    },
                    iconName = "ic_web",
                    text = stringResource(R.string.path_add_link),
                    rotationDegrees = 90f,
                    aspectRatio = 0.7f
                )
            }
        }
    }

    // --- Giữ nguyên logic gọi Dialog ở dưới không thay đổi ---
    if (isDialogInsert && selectedPath != null) {
        DialogInsertPath(
            type = selectedPath!!.type,
            pathsViewModel,
            onDismiss = { isDialogInsert = false; selectedPath = null })
    }
    if (isDialogUpdate && selectedPath != null) {
        DialogUpdatePath(
            selectedPath!!,
            pathsViewModel,
            onDismiss = { isDialogUpdate = false; selectedPath = null })
    }
}


// 🔥 CƠ CHẾ 2: Layout cho top center (dọc)
@Composable
private fun BrowserTopCenterLayout(
    view: View,
    config: CombineSettingModel,
    paths: List<PathModel>,
    shortKeys: List<ShortKeyModel>,
    inputManager: InputManager,
    pathsViewModel: PathsViewModel,
) {
    var isDialogUpdate by remember { mutableStateOf(false) }
    var isDialogInsert by remember { mutableStateOf(false) }
    var selectedPath by remember { mutableStateOf<PathModel?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        itemsIndexed(shortKeys) { _, shortKey ->
            ButtonWeight(
                modifier = Modifier.wrapContentSize(),
                colorStroke = Color.Gray.copy(alpha = 0.6f),
                shape = RoundedCornerShape(3.dp),
                onClick = {
                    if (inputManager.executeShortKey(shortKey)) view.playSoundClick()
                },
                iconName = shortKey.icon,
                text = if (config.isButtonText) shortKey.name.take(4) else null,
            )
        }
    }


    LazyVerticalGrid(
        columns = GridCells.Fixed(6), //cột
        modifier = Modifier.padding(top = 5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        itemsIndexed(paths) { _, path ->
            var expanded by remember { mutableStateOf(false) }
            ButtonWeight(
                modifier = Modifier.height(50.dp),
                colorStroke = Color.LightGray.copy(alpha = 0.7f),
                shape = RoundedCornerShape(3.dp),
                onClick = { if (inputManager.handlePath(path)) view.playSoundClick() },
                onLongClick = { expanded = true },
                iconName = path.icon,
                text = path.name,
                rotationDegrees = 0f,
                aspectRatio = 0.6f
            ) {
                MoreVertDropdown(
                    view = view,
                    expanded = expanded,
                    onDismiss = { expanded = false },
                    menuItems = listOf(
                        DropMenuModel(
                            text = stringResource(R.string.path_edit),
                            onClick = {
                                selectedPath = path
                                isDialogUpdate = true
                            },
                            icon = "baseline_edit"
                        ),
                        DropMenuModel(
                            text = stringResource(R.string.path_create),
                            onClick = {
                                selectedPath = path
                                isDialogInsert = true
                            },
                            icon = "baseline_add"
                        ),
                        DropMenuModel(
                            text = stringResource(R.string.path_remove),
                            onClick = { pathsViewModel.removePath(path) },
                            icon = "baseline_remove",
                            isDestructive = true
                        )
                    )
                )
            }
        }
        if (paths.isEmpty()) {
            item {
                ButtonWeight(
                    modifier = Modifier.wrapContentSize(),
                    colorStroke = Color.LightGray.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(3.dp),
                    onClick = {
                        isDialogInsert = true
                        selectedPath = PathModel(type = "openurl", path = "")
                    },
                    iconName = "ic_web",
                    text = stringResource(R.string.path_add_link),
                    aspectRatio = 0.6f
                )
            }
        }
    }

    // DialogInsertPath
    if (isDialogInsert && selectedPath != null) {
        DialogInsertPath(
            type = selectedPath!!.type,
            pathsViewModel,
            onDismiss = {
                isDialogInsert = false
                selectedPath = null
            }
        )
    }

    // UpdatePathDialog
    if (isDialogUpdate && selectedPath != null) {
        DialogUpdatePath(
            selectedPath!!,
            pathsViewModel,
            onDismiss = {
                isDialogUpdate = false
                selectedPath = null
            }
        )
    }
}
