package com.ranhroi.touchremote.ui.tabs

import android.content.Context
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.ranhroi.touchremote.models.CombineSettingModel
import com.ranhroi.touchremote.send.InputManager
import com.ranhroi.touchremote.ui.pager.application.ApplicationPager
import com.ranhroi.touchremote.ui.pager.files.FilesScreenPager
import com.ranhroi.touchremote.ui.pager.mirror.ScreenMirrorPager
import com.ranhroi.touchremote.ui.pager.task.TaskManagerPager
import com.ranhroi.touchremote.ui.pager.terminal.RunCommandPager
import com.ranhroi.touchremote.ui.pager.touch.TouchPager
import com.ranhroi.touchremote.ui.screens.main.SelectAppState
import com.ranhroi.touchremote.ui.screens.quickkey.QuickKeysSetupViewModel
import com.ranhroi.touchremote.ui.screens.server.ConnectionViewModel
import com.ranhroi.touchremote.ui.screens.server.HybridConnectionModel
import com.ranhroi.touchremote.ui.screens.shortkey.ShortKeySetupViewModel
import com.ranhroi.touchremote.ui.weight.WeightCommandKeysKeyboard
import com.ranhroi.touchremote.ui.weight.WeightQuickKeys
import com.ranhroi.touchremote.ui.weight.WeightShortKeysBrowser
import com.ranhroi.touchremote.ui.weight.WeightShortKeysMedia
import com.ranhroi.touchremote.ui.weight.WeightShortKeysPower


@Composable
fun TabActionPager(
    navController: NavHostController,
    view: View,
    context: Context,
    config: CombineSettingModel,
    connection: ConnectionViewModel,
    hybridState: HybridConnectionModel,
    inputManager: InputManager,
    selectState: SelectAppState,
    quickKeysSetupViewModel: QuickKeysSetupViewModel = hiltViewModel(),
    shortKeySetupViewModel: ShortKeySetupViewModel = hiltViewModel()
) {

    val allQuickKeys by quickKeysSetupViewModel.allQuickKeys.collectAsState()

    val allShortKeys by shortKeySetupViewModel.allShortKeys.collectAsState()

    val browserShortKeys = remember(allShortKeys) {
        allShortKeys.filter { it.type == "browser" }
    }
    val powerShortKeys = remember(allShortKeys) {
        allShortKeys.filter { it.type == "power" }
    }
    val floatingShortKeys = remember(allShortKeys) {
        allShortKeys.filter { it.type == "floating" }
    }

    val isDrawerMode = config.isRotation && !selectState.isCommandKey
    val isVisible =
        selectState.isCommandKey || (selectState.bottomIndex != -1 && selectState.bottomIndex != 2)

    LaunchedEffect(selectState.bottomIndex) {
        selectState.isCommandKey = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(5f)
    ) {
        AnimatedVisibility(
            modifier = Modifier
                .align(
                    if (isDrawerMode) Alignment.CenterEnd
                    else Alignment.TopCenter
                ),
            visible = isVisible,
            enter = if (isDrawerMode) {
                slideInHorizontally(
                    initialOffsetX = { it }, // Trượt từ biên phải
                    animationSpec = tween(durationMillis = 400)
                ) + fadeIn()
            } else {
                slideInVertically(
                    initialOffsetY = { -it },
                    animationSpec = tween(durationMillis = 400)
                ) + fadeIn()
            },
            exit = if (isDrawerMode) {
                slideOutHorizontally(
                    targetOffsetX = { it }, //  Trượt ra biên phải
                    animationSpec = tween(durationMillis = 400)
                ) + fadeOut()
            } else {
                slideOutVertically(
                    targetOffsetY = { -it },
                    animationSpec = tween(durationMillis = 400)
                ) + fadeOut()
            }
        ) {

            Card(
                modifier = if (isDrawerMode) {
                    // DRAWER: Chiều cao full, chiều rộng theo content
                    Modifier
                        .fillMaxHeight()
                        .wrapContentWidth()
                        .widthIn(min = 0.dp, max = 330.dp)
                } else {
                    // TOP CENTER: Chiều rộng full, chiều cao theo content
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                },
                shape = if (isDrawerMode) RoundedCornerShape(
                    topStart = 5.dp,
                    bottomStart = 5.dp
                ) else RoundedCornerShape(bottomStart = 5.dp, bottomEnd = 5.dp)
            ) {
                when {
                    selectState.bottomIndex == 1 -> WeightQuickKeys(
                        navController,
                        config,
                        inputManager,
                        allQuickKeys,
                        quickKeysSetupViewModel
                    )

                    selectState.isCommandKey -> WeightCommandKeysKeyboard(
                        view,
                        context,
                        selectState,
                        inputManager,
                    )

                    selectState.bottomIndex == 3 -> WeightShortKeysMedia(
                        view,
                        config,
                        inputManager,
                        allShortKeys,
                        hybridState,
                        shortKeySetupViewModel
                    )

                    selectState.bottomIndex == 4 -> WeightShortKeysBrowser(
                        view,
                        config,
                        inputManager,
                        browserShortKeys,
                    )

                    selectState.bottomIndex == 5 -> WeightShortKeysPower(
                        view,
                        config,
                        inputManager,
                        powerShortKeys
                    )
                }
            }
        }
    }

    when (selectState.tabIndex) {
        0 -> TouchPager(view, config, floatingShortKeys, connection, inputManager, hybridState, selectState)
        1 -> ApplicationPager(view, connection, hybridState)
        2 -> TaskManagerPager(view, config, connection, hybridState)
        3 -> FilesScreenPager(selectState, allShortKeys, connection)
        4 -> RunCommandPager(view, connection, hybridState)
        5 -> ScreenMirrorPager(view, config, connection)
    }

    if (selectState.bottomIndex == 0 && selectState.tabIndex != 0) TouchPager(
        view,
        config,
        floatingShortKeys,
        connection,
        inputManager,
        hybridState,
        selectState
    )
}
