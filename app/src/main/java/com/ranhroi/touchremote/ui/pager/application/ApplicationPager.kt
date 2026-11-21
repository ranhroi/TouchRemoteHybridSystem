package com.ranhroi.touchremote.ui.pager.application

import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ranhroi.touchremote.R
import com.ranhroi.touchremote.models.DropMenuModel
import com.ranhroi.touchremote.models.HeaderMenuModel
import com.ranhroi.touchremote.ui.components.HeaderHorizontal
import com.ranhroi.touchremote.ui.components.MoreVertDropdown
import com.ranhroi.touchremote.ui.pager.path.PathsViewModel
import com.ranhroi.touchremote.ui.screens.server.ConnectionViewModel
import com.ranhroi.touchremote.ui.screens.server.HybridConnectionModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ApplicationPager(
    view: View,
    connection: ConnectionViewModel,
    hybridState: HybridConnectionModel,
    software: ApplicationViewModel = viewModel(),
    pathsViewModel: PathsViewModel = hiltViewModel(),
) {
    val paths by pathsViewModel.allPaths.collectAsState()
    paths.filter { it.type == "launch" }

    val apps = software.apps
    val isRefreshing by software.isLoading

    // Tự động tải dữ liệu lần đầu khi vào màn hình
    LaunchedEffect(Unit) {
        software.setSocket(hybridState)
        if (apps.isEmpty()) {
            software.loadDockInfo()
        }
    }

    val leftMenuItems = listOf(
        HeaderMenuModel(
            isStatusDot = true,
            isConnected = hybridState.isConnected,
            isVisible = { true }
        ),
        HeaderMenuModel(
            text = if (hybridState.isConnected)
                stringResource(R.string.app_connected)
            else
                stringResource(R.string.app_disconnected),
            textColor = if (hybridState.isConnected) Color(0xFF4CAF50) else Color(0xFFE57373),
            isVisible = { true }
        ),
        HeaderMenuModel(
            text = hybridState.os,
            textColor = Color.Gray,
            isVisible = { true }
        )
    )

    val rightMenuItems = listOf(
        HeaderMenuModel(
            icon = "baseline_import_export",
            onClick = { software.loadDockInfo() },
            enabled = hybridState.isConnected
        ),
        HeaderMenuModel(
            icon = "baseline_refresh",
            onClick = { connection.pingCommand() }
        )
    )


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HeaderHorizontal(
            view = view,
            leftItems = leftMenuItems,
            rightItems = rightMenuItems
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Danh sách chính
        LazyVerticalGrid(
            columns = GridCells.Fixed(1),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // TRƯỜNG HỢP: Danh sách trống và không phải đang tải (Lỗi hoặc chưa có data)
            if (apps.isEmpty() && !isRefreshing) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight(0.7f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.app_empty_icon),
                            textAlign = TextAlign.Center,
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                // TRƯỜNG HỢP: Có dữ liệu (Hiện danh sách bình thường)
                itemsIndexed(apps, key = { _, item -> item.bundleID }) { _, app ->
                    AppViewItem(view, app, software)
                }
            }
        }
    }
}

@Composable
fun AppViewItem(
    view: View,
    app: DockInfoModel,
    software: ApplicationViewModel
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .height(60.dp)
            .fillMaxWidth()
            .border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
            .clip(RoundedCornerShape(3.dp)),
        onClick = { expanded = true },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 10.dp, horizontal = 15.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(0.9f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                AppIconView(app.icon)
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    app.name,
                    maxLines = 1,
                    textAlign = TextAlign.Start,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Text(
                text = if (app.running) stringResource(R.string.app_on) else stringResource(R.string.app_off),
                color = if (app.running) Color.Green else Color.Red,
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(0.1f)
            )
        }

        MoreVertDropdown(
            view = view,
            expanded = expanded,
            onDismiss = { expanded = false },
            menuItems = listOf(
                DropMenuModel(
                    text = stringResource(R.string.app_launch_action),
                    onClick = { software.openApp(app.bundleID) },
                    icon = "baseline_launch"
                ),
                DropMenuModel(
                    text = stringResource(R.string.app_close_action),
                    onClick = { software.closeApp(app.bundleID) },
                    icon = "baseline_close",
                    isDestructive = true
                )
            ),
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
fun AppIconView(icon: Bitmap?) {
    if (icon != null) {
        Image(
            bitmap = icon.asImageBitmap(),
            contentDescription = null,
            alignment = Alignment.Center,
            modifier = Modifier.size(50.dp)
        )
    } else {
        // fallback hoặc placeholder nếu icon null
        Icon(
            painterResource(R.drawable.baseline_apps), contentDescription = null,
            modifier = Modifier.size(50.dp)
        )
    }
}