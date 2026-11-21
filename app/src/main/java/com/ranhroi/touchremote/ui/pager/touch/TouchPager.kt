package com.ranhroi.touchremote.ui.pager.touch

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ranhroi.touchremote.R
import com.ranhroi.touchremote.models.CombineSettingModel
import com.ranhroi.touchremote.models.HeaderMenuModel
import com.ranhroi.touchremote.models.ShortKeyModel
import com.ranhroi.touchremote.send.InputManager
import com.ranhroi.touchremote.ui.components.HeaderHorizontal
import com.ranhroi.touchremote.ui.screens.main.SelectAppState
import com.ranhroi.touchremote.ui.screens.server.ConnectionViewModel
import com.ranhroi.touchremote.ui.screens.server.HybridConnectionModel
import com.ranhroi.touchremote.ui.weight.WeightShortKeysFloating

@Composable
fun TouchPager(
    view: View,
    config: CombineSettingModel,
    shortKeys: List<ShortKeyModel> = emptyList(),
    connection: ConnectionViewModel,
    inputManager: InputManager,
    hybridState: HybridConnectionModel,
    selectState: SelectAppState
) {
    // ===== CONTROL CIRCLE STATE =====
    val width = 52
    val isRotation = config.isRotation

    val color = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)

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
            icon = if (isRotation) "baseline_screen_rotation" else "baseline_screen_lock_rotation",
            onClick = { connection.updateRotation(!isRotation) },
            modifier = Modifier.size(17.dp)
        ),
        HeaderMenuModel(
            icon = "baseline_refresh",
            onClick = { connection.pingCommand() }
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // ===== FLOATING HOTKEYS =====
        if (config.isFloating) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .zIndex(1f)
                    .fillMaxHeight()
                    .padding(bottom = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                WeightShortKeysFloating(
                    view,
                    width,
                    selectState,
                    config,
                    shortKeys,
                    inputManager,
                    color = color,
                )
            }
        }


        // ===== HEADER =====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = if (config.isFloating) 60.dp else 0.dp)
                .zIndex(1f)
        ) {
            HeaderHorizontal(
                view = view,
                modifier = Modifier.fillMaxWidth(),
                leftItems = leftMenuItems,
                rightItems = rightMenuItems
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.2f))
        ) {
            Touchpad(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                inputManager,
            )

            Row(
                modifier = Modifier.fillMaxWidth().height(60.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                TouchKey(
                    view = view,
                    type = TouchKeyType.LEFT,
                    modifier = Modifier
                        .weight(2f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topEnd = 5.dp)),
                    inputManager = inputManager,
                    isLeftHand = config.isLeftHand,
                )
                // ===== NÚT GIỮA =====
                TouchMiddleKey(
                    view = view,
                    modifier = Modifier
                        .weight(1.8f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)),
                    inputManager = inputManager,
                    hybridState = hybridState,
                )
                TouchKey(
                    view = view,
                    type = TouchKeyType.RIGHT,
                    modifier = Modifier
                    .weight(2f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = 5.dp)),
                    inputManager = inputManager,
                    isLeftHand = config.isLeftHand,
                )
            }

        }
    }
}
