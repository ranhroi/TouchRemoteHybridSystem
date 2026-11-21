package com.ranhroi.touchremote.ui.drawer

import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.ranhroi.touchremote.R
import com.ranhroi.touchremote.ui.components.IconAny
import com.ranhroi.touchremote.ui.navigation.NavRoute
import com.ranhroi.touchremote.ui.screens.server.ConnectionViewModel
import com.ranhroi.touchremote.utils.IconsMap
import com.ranhroi.touchremote.utils.playSoundClick
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun DrawerContent(
    mainNavController: NavHostController,
    drawerState: DrawerState,
    connection: ConnectionViewModel,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val drawerModels = listOf(
        DrawerModel(
            title = stringResource(R.string.drawer_servers_manager),
            route = NavRoute.SetServer.route,
            icon = "baseline_list_alt"
        ),
        DrawerModel(
            title = stringResource(R.string.drawer_shortkey_manager),
            route = NavRoute.SetShortKey.route,
            icon = "baseline_shortcut"
        ),
        DrawerModel(
            title = "Fujitsu Remote",
            route = NavRoute.FujitsuRemote.route,
            icon = "baseline_settings_remote"
        ),
        DrawerModel(
            title = stringResource(R.string.drawer_quickkey_manager),
            route = NavRoute.SetQuickKeys.route,
            icon = "baseline_keyboard_command_key"
        ),
        DrawerModel(
            title = stringResource(R.string.drawer_help_install),
            route = NavRoute.Onboarding.route,
            icon = "baseline_help"
        ),
        DrawerModel(
            title = stringResource(R.string.drawer_settings_app),
            route = NavRoute.Setting.route,
            icon = "baseline_settings"
        ),
        DrawerModel(
            title = stringResource(R.string.drawer_close),
            route = "",
            icon = "baseline_close"
        ),
    )

    ModalDrawerSheet(
        modifier = Modifier
            .width(300.dp)
            .padding(vertical = 0.dp),
        drawerShape = RoundedCornerShape(topEnd = 0.dp, bottomEnd = 0.dp)
    ) {
        SpinnerLikeDropdown(view, scope, drawerState, connection)

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(drawerModels) { _, item ->
                val icon = remember(item.icon) { IconsMap.getIconOrDefault(item.icon) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch {
                                if (item.route.isNotEmpty()) {
                                    if (item.route == NavRoute.Onboarding.route) {
                                        connection.resetOnboarding()
                                        mainNavController.navigate(item.route)
                                        drawerState.close()
                                    } else {
                                        mainNavController.navigate(item.route)
                                        drawerState.close()
                                    }
                                } else {
                                    drawerState.close()
                                }
                            }
                            view.playSoundClick()
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconAny(
                        text = item.title,
                        icon = icon,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = item.title)
                }
                Divider(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpinnerLikeDropdown(
    view: View, scope: CoroutineScope, drawerState: DrawerState, connection: ConnectionViewModel
) {
    val uiState by connection.uiState.collectAsState()
    val currentServer by connection.getSelectedDevice.collectAsState()

    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        TextField(
            value = currentServer.let { "${it.name}:${it.port}" },
            onValueChange = {},
            readOnly = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                textAlign = TextAlign.Start,
                lineHeight = 24.sp
            ),
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            leadingIcon = {
                Icon(
                    painterResource(if (currentServer.bluetooth) R.drawable.baseline_bluetooth else R.drawable.baseline_wifi),
                    contentDescription = null,
                    tint = if (currentServer.selected) {
                        Color.Green
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            },
            modifier = Modifier
                .menuAnchor()
                .height(65.dp)
                .fillMaxWidth(),
            singleLine = true
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.shadow(5.dp)
        ) {
            uiState.servers.forEach { server ->
                DropdownMenuItem(
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                        ) {
                            Text(
                                text = server.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (server.selected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = "${server.address}${if (server.port != 0) ":${server.port}" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (server.selected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    leadingIcon = {
                        Icon(
                            painterResource(if (server.bluetooth) R.drawable.baseline_bluetooth else R.drawable.baseline_wifi),
                            contentDescription = stringResource(R.string.drawer_device_type),
                            modifier = Modifier.size(30.dp),
                            tint = if (server.selected) {
                                Color.Green
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    },
                    onClick = {
                        expanded = false
                        if (server != currentServer) {
                            connection.disconnectAll()
                            connection.connectToServer(server)
                        }
                        scope.launch { drawerState.close() }
                        view.playSoundClick()
                    }
                )
                Divider(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )
            }
        }
    }
}