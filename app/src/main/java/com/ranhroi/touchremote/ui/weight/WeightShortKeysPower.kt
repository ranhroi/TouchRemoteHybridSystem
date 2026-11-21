package com.ranhroi.touchremote.ui.weight

import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ranhroi.touchremote.R
import com.ranhroi.touchremote.models.CombineSettingModel
import com.ranhroi.touchremote.models.ShortKeyModel
import com.ranhroi.touchremote.send.InputManager
import com.ranhroi.touchremote.ui.components.ButtonWeight
import com.ranhroi.touchremote.ui.components.DotsLoadingIndicator
import com.ranhroi.touchremote.ui.components.DotsOrientation
import com.ranhroi.touchremote.ui.dialogs.ConfirmDialog
import com.ranhroi.touchremote.utils.playSoundClick
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun WeightShortKeysPower(
    view: View,
    config: CombineSettingModel,
    inputManager: InputManager,
    shortKeys: List<ShortKeyModel>,
) {
    var isLoading by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(5000.milliseconds) // chờ 5 giây
        isLoading = false
    }

    // Modifier linh hoạt theo cơ chế
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
                if (config.isRotation) {
                    // 🔥 CƠ CHẾ 1: Drawer bên phải (ngang)
                    PowerDrawerRightLayout(
                        view = view,
                        config = config,
                        shortKeys = shortKeys,
                        inputManager = inputManager
                    )
                } else {
                    // 🔥 CƠ CHẾ 2: Top center (dọc)
                    PowerTopCenterLayout(
                        view = view,
                        config = config,
                        shortKeys = shortKeys,
                        inputManager = inputManager
                    )
                }
        } else {
            if (isLoading) {
                // Loading với kích thước phù hợp
                Box(
                    modifier = Modifier
                        .wrapContentSize(),
                    contentAlignment = Alignment.Center
                ) {
                    DotsLoadingIndicator(orientation = if (config.isRotation) DotsOrientation.VERTICAL else DotsOrientation.HORIZONTAL)
                }
            } else {
                //  Empty state với kích thước phù hợp
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.power_shortcut_title))
                }
            }
        }
    }
}

// 🔥 CƠ CHẾ 1: Layout cho drawer bên phải (power buttons) - ĐÃ SỬA
@Composable
private fun PowerDrawerRightLayout(
    view: View,
    config: CombineSettingModel,
    shortKeys: List<ShortKeyModel>,
    inputManager: InputManager,
) {
    LazyHorizontalGrid(
        rows = GridCells.Fixed(8),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        reverseLayout = true
    ) {
        itemsIndexed(shortKeys) { _, shortKey ->
            var expanded by remember { mutableStateOf(false) }
            ButtonWeight(
                modifier = Modifier.width(50.dp),
                colorStroke = Color.Gray.copy(alpha = 0.6f),
                shape = RoundedCornerShape(3.dp),
                onClick = {
                    if (shortKey.name == "Brightness Up"
                        || shortKey.name == "Brightness Down"
                    ) {
                        inputManager.executeShortKey(shortKey)
                        view.playSoundClick()
                    } else {
                        expanded = true
                    }
                },
                onLongPressRepeat = {
                    if (shortKey.name == "Brightness Up"
                        || shortKey.name == "Brightness Down"
                    ) {
                        inputManager.executeShortKey(shortKey)
                        view.playSoundClick()
                    }
                },
                iconName = shortKey.icon,
                text = if (config.isButtonText) shortKey.name.take(4) else null,
                rotationDegrees = 90f,
                aspectRatio = 0.6f
            ) {
                ConfirmDialog(
                    show = expanded,
                    title = stringResource(
                        R.string.power_confirm_title,
                        shortKey.name.uppercase(LocalLocale.current.platformLocale)
                    ),
                    message = stringResource(
                        R.string.power_confirm_message,shortKey.name
                    ),
                    onConfirm = {
                        inputManager.executeShortKey(shortKey)
                        view.playSoundClick()
                    },
                    onDismiss = {
                        expanded = false
                    }
                )
            }
        }
    }
}

// 🔥 CƠ CHẾ 2: Layout cho top center (power buttons) - ĐÃ SỬA
@Composable
private fun PowerTopCenterLayout(
    view: View,
    config: CombineSettingModel,
    shortKeys: List<ShortKeyModel>,
    inputManager: InputManager,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(8),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        itemsIndexed(shortKeys) { _, shortKey ->
            var expanded by remember { mutableStateOf(false) }
            ButtonWeight(
                modifier = Modifier.wrapContentSize(),
                colorStroke = Color.Gray.copy(alpha = 0.6f),
                shape = RoundedCornerShape(3.dp),
                onClick = {
                    if (shortKey.name == "Brightness Up"
                        || shortKey.name == "Brightness Down"
                    ) {
                        inputManager.executeShortKey(shortKey)
                        view.playSoundClick()
                    } else {
                        expanded = true
                    }
                },
                onLongPressRepeat = {
                    if (shortKey.name == "Brightness Up"
                        || shortKey.name == "Brightness Down"
                    ) {
                        inputManager.executeShortKey(shortKey)
                        view.playSoundClick()
                    }
                },
                iconName = shortKey.icon,
                text = if (config.isButtonText) shortKey.name.take(4) else null,
                rotationDegrees = 0f
            ) {
                ConfirmDialog(
                    show = expanded,
                    title = stringResource(
                        R.string.power_confirm_title, shortKey.name.uppercase(LocalLocale.current.platformLocale)
                    ),
                    message = stringResource(
                        R.string.power_confirm_message, shortKey.name
                    ),
                    onConfirm = {
                        inputManager.executeShortKey(shortKey)
                        view.playSoundClick()
                    },
                    onDismiss = {
                        expanded = false
                    }
                )
            }
        }
    }
}
