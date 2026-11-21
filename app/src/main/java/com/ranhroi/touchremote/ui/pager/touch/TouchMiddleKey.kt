package com.ranhroi.touchremote.ui.pager.touch

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.ranhroi.touchremote.ui.screens.server.HybridConnectionModel
import com.ranhroi.touchremote.send.InputManager
import com.ranhroi.touchremote.utils.ConstantsUtils
import com.ranhroi.touchremote.utils.playSoundClick
import kotlin.math.abs

@Composable
fun TouchMiddleKey(
    view: View,
    modifier: Modifier = Modifier,
    inputManager: InputManager,
    hybridState: HybridConnectionModel,
    backgroundColor: Color = Color.Gray,
    macScrollMultiplier: Int = 4,
) {
    var lastDownTime by remember { mutableLongStateOf(0L) }
    var lastPosX by remember { mutableFloatStateOf(0f) }
    var lastPosY by remember { mutableFloatStateOf(0f) }
    var downX by remember { mutableFloatStateOf(0f) }
    var downY by remember { mutableFloatStateOf(0f) }
    var viewAlpha by remember { mutableFloatStateOf(1f) }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(topStart = 5.dp))
            .graphicsLayer { alpha = viewAlpha }
            .background(backgroundColor)
            .pointerInput(inputManager) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()

                        val change = event.changes.first()
                        when {
                            change.changedToDown() -> {
                                viewAlpha = 0.7f
                                downX = change.position.x
                                downY = change.position.y
                                lastPosX = downX
                                lastPosY = downY
                                lastDownTime = System.currentTimeMillis()
                            }

                            change.changedToUp() -> {
                                viewAlpha = 1f
                                val currentTime = System.currentTimeMillis()
                                val deltaX = abs(change.position.x - downX)
                                val deltaY = abs(change.position.y - downY)
                                if ((currentTime - lastDownTime) < 150 && deltaX + deltaY < 2f) {
                                    view.playSoundClick()
                                    inputManager.onMiddleClick()
                                }
                            }

                            change.pressed -> {
                                val deltaX = change.position.x - lastPosX
                                val deltaY = change.position.y - lastPosY

                                fun adjust(value: Float): Int {
                                    var adjusted = (abs(value) / 3).toInt()
                                    if (ConstantsUtils.getSystemInfoInt(hybridState.os) == ConstantsUtils.SERVER_TYPE_MAC) adjusted *= macScrollMultiplier
                                    return adjusted
                                }

                                if (deltaX < -3f) {
                                    inputManager.onMouseScrollHorizontal(false, adjust(deltaX))
                                } else if (deltaX > 3f) {
                                    inputManager.onMouseScrollHorizontal(true, adjust(deltaX))
                                }

                                if (deltaY < -3f) {
                                    inputManager.onMouseScrollVertical(false, adjust(deltaY))
                                } else if (deltaY > 3f) {
                                    inputManager.onMouseScrollVertical(true, adjust(deltaY))
                                }

                                if (abs(deltaX) >= 3f) lastPosX = change.position.x
                                if (abs(deltaY) >= 3f) lastPosY = change.position.y
                            }
                        }
                    }
                }
            }
    )
}


