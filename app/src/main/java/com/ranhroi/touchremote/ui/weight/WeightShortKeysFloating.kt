package com.ranhroi.touchremote.ui.weight

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.ranhroi.touchremote.models.CombineSettingModel
import com.ranhroi.touchremote.models.ShortKeyModel
import com.ranhroi.touchremote.send.InputManager
import com.ranhroi.touchremote.ui.components.ButtonWeight
import com.ranhroi.touchremote.ui.screens.main.SelectAppState
import com.ranhroi.touchremote.utils.playSoundClick


@Composable
fun WeightShortKeysFloating(
    view: View,
    width: Int,
    state: SelectAppState,
    config: CombineSettingModel,
    shortKeys: List<ShortKeyModel>,
    inputManager: InputManager,
    color: Color = MaterialTheme.colorScheme.surface,
    ) {
    var containerH by remember { mutableStateOf(0.dp) }

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .onGloballyPositioned { coordinates ->
                containerH = coordinates.size.height.dp
            }
    ) {
        Surface(
            modifier = Modifier
                .width(width.dp)
                .height(containerH),
            tonalElevation = 3.dp,
            color = color
        ) {
            LazyHorizontalGrid(
                rows = GridCells.Fixed(10),
                modifier = Modifier
                    .height(containerH),
                contentPadding = PaddingValues(vertical = 3.dp),
            ) {
                if (shortKeys.isEmpty()) return@LazyHorizontalGrid

                itemsIndexed(shortKeys) { index, shortKey ->
                    ButtonWeight(
                        modifier = Modifier.size(width.dp),
                        onClick = {
                            view.playSoundClick()
                            when (index) {
                                5 -> {
                                    state.isCommandKey = !state.isCommandKey
                                }

                                else -> inputManager.executeShortKey(shortKey)
                            }
                        },
                        onLongPressRepeat = {
                            when (index) {
                                8, 9 -> {
                                    inputManager.executeShortKey(shortKey)
                                    view.playSoundClick()
                                }
                            }
                        },
                        shape = RoundedCornerShape(0.dp),
                        colorStroke = Color.Transparent,
                        iconName = shortKey.icon,
                        iconScale = 0.7f,
                        text = if (config.isButtonText) shortKey.name.take(4) else null,
                        rotationDegrees = if (config.isRotation) 90f else 0f,
                        aspectRatio = 1f
                    )
                }
            }
        }
    }
}