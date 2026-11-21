package com.ranhroi.touchremote.ui.screens.main


import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.ranhroi.touchremote.R
import com.ranhroi.touchremote.ui.components.IconAny
import com.ranhroi.touchremote.ui.drawer.DrawerContent
import com.ranhroi.touchremote.ui.keyboard.VirtualKeyboard
import com.ranhroi.touchremote.ui.screens.server.ConnectionManager
import com.ranhroi.touchremote.ui.screens.server.ConnectionViewModel
import com.ranhroi.touchremote.ui.screens.server.HybridConnectionModel
import com.ranhroi.touchremote.ui.screens.server.NetworkStatusViewModel
import com.ranhroi.touchremote.ui.tabs.TabActionPager
import com.ranhroi.touchremote.utils.ConstantsUtils
import com.ranhroi.touchremote.utils.IconsMap
import com.ranhroi.touchremote.utils.playSoundClick
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds


@SuppressLint("UnrememberedGetBackStackEntry")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController: NavHostController,
    connection: ConnectionViewModel = hiltViewModel(),
    networkStatus: NetworkStatusViewModel = hiltViewModel(),
) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val context = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val config by connection.config.collectAsStateWithLifecycle()

    val hasServers by connection.hasServers.collectAsStateWithLifecycle()
    val isLoadingServers by connection.isLoadingServers.collectAsStateWithLifecycle()

    val allConnections by connection.connections.collectAsStateWithLifecycle()
    val hybridState = allConnections[ConnectionManager.ROLE_COMMAND] ?: HybridConnectionModel()
    val inputManager by connection.inputState.collectAsStateWithLifecycle()

    val keyboardController = LocalSoftwareKeyboardController.current
    val selectState = remember { SelectAppState() }


    // ============================================================
    // XỬ LÝ KHI CHƯA CÓ SERVER
    // ============================================================
    if (!hasServers && !isLoadingServers) {
        NoServerScreen(navController)
        return
    }

    BackHandler(enabled = true) {
        scope.launch {
            when {
                drawerState.isOpen -> drawerState.close()
                selectState.bottomIndex == 2 -> keyboardController?.hide()
                else -> selectState.resetState()
            }
        }
    }


    LifecycleResumeEffect(Unit) {
        // Chỉ ping khi đã có kết nối + không đang loading
        val state = connection.getCommandState()
        if (selectState.tabIndex == 0 && state.isConnected && !state.isNeedPassword) {
            connection.pingCommand()
        }
        onPauseOrDispose {
            selectState.resetState()
        }
    }

    // Xử lý keyboard
    LaunchedEffect(selectState.bottomIndex) {
        when (selectState.bottomIndex) {
            2 -> {
                delay(50.milliseconds)
                keyboardController?.show()
            }

            else -> keyboardController?.hide()
        }
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            selectState.resetState()
            scope.launch {
                if (drawerState.isOpen) drawerState.close()
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
        drawerContent = {
            DrawerContent(navController, drawerState, connection)
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { },
                    actions = {
                        IconButton(
                            modifier = Modifier
                                .width(80.dp)
                                .height(70.dp),
                            onClick = {
                                scope.launch {
                                    if (drawerState.isClosed) drawerState.open()
                                    else drawerState.close()
                                }
                                view.playSoundClick()
                            }
                        ) {
                            Icon(
                                painterResource(R.drawable.baseline_menu),
                                contentDescription = "baseline_menu"
                            )
                        }

                        ScrollableTabRow(
                            selectedTabIndex = selectState.tabIndex,
                            edgePadding = 0.dp
                        ) {
                            ConstantsUtils.tabs.forEachIndexed { index, tab ->
                                val isSelected = selectState.tabIndex == index
                                Tab(
                                    selected = isSelected,
                                    onClick = {
                                        selectState.apply {
                                            tabIndex = index
                                            bottomIndex = -1
                                            isCommandKey = false
                                        }
                                        view.playSoundClick()
                                    },
                                    modifier = Modifier
                                        .width(60.dp)
                                        .height(70.dp)
                                        .background(if (isSelected) Color.LightGray.copy(alpha = 0.3f) else Color.Transparent),
                                    icon = {
                                        Icon(
                                            painterResource(if (isSelected) tab.iconFilled else tab.iconOutline),
                                            contentDescription = null
                                        )
                                    }
                                )
                            }
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    ConstantsUtils.bottomNavRepository.forEachIndexed { index, item ->
                        val icon = remember(item.icon) { IconsMap.getIconOrDefault(item.icon) }
                        NavigationBarItem(
                            selected = selectState.bottomIndex == index,
                            onClick = {
                                selectState.bottomIndex =
                                    if (selectState.bottomIndex == index) -1 else index
                                view.playSoundClick()
                            },
                            icon = {
                                IconAny(
                                    text = item.icon,
                                    icon = icon,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(item.nameRes),
                                    maxLines = 1,
                                    textAlign = TextAlign.Center,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        )
                    }
                }

                if (selectState.bottomIndex == 2) {
                    VirtualKeyboard(modifier = Modifier, inputManager)
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                NetworkStatusBar(
                    navController = navController,
                    view = view,
                    connection = connection,
                    networkStatus = networkStatus,
                    isShow = true,
                )
                TabActionPager(
                    navController = navController,
                    view = view,
                    context = context,
                    config = config,
                    connection = connection,
                    hybridState = hybridState,
                    inputManager = inputManager,
                    selectState = selectState,
                )
                HorizontalDivider(
                    Modifier.fillMaxWidth(),
                    thickness = 1.dp,
                    color = Color.Gray.copy(alpha = 0.5f)
                )
            }
        }
    }
}
