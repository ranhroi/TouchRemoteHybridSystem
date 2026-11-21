package com.ranhroi.touchremote.ui.pager.touch

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import com.ranhroi.touchremote.send.InputManager
import com.ranhroi.touchremote.utils.playSoundClick

/**
 * @param type       Loại phím logic (LEFT hoặc RIGHT) — đây là "ý định" của phím.
 * @param swapHands  Nếu true, phím sẽ đảo vai trò: LEFT gửi sự kiện chuột phải và ngược lại.
 *                   Dùng khi người dùng muốn đổi tay trái/phải.
 */
@Composable
fun TouchKey(
    view: View,
    type: TouchKeyType,
    modifier: Modifier = Modifier,
    inputManager: InputManager,
    isLeftHand: Boolean = false,
) {

    var viewAlpha by remember { mutableFloatStateOf(1f) }

    // Xác định phím "hiệu dụng" sau khi áp dụng swapHands
    val effectiveType = if (isLeftHand) {
        if (type == TouchKeyType.LEFT) TouchKeyType.RIGHT else TouchKeyType.LEFT
    } else {
        type
    }


    Box(
        modifier = modifier
            .graphicsLayer { alpha = viewAlpha }
            .background(Color.Gray)
            .pointerInput(inputManager, isLeftHand) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.first()

                        when {
                            change.changedToDown() -> {
                                viewAlpha = 0.7f
                                sendDown(effectiveType, isLeftHand, inputManager)
                            }

                            change.changedToUp() -> {
                                Thread.sleep(50) // Giữ nguyên delay như bản gốc
                                viewAlpha = 1f
                                view.playSoundClick()
                                sendUp(effectiveType, isLeftHand, inputManager)
                            }
                        }
                    }
                }
            }
    )
}

private fun sendDown(
    type: TouchKeyType,
    isLeftHand: Boolean,
    inputManager: InputManager,
) {
    // Giữ nguyên logic gốc: config.leftHand quyết định gửi chuột trái hay phải
    when (type) {
        TouchKeyType.RIGHT -> {
            if (!isLeftHand) inputManager.sendMouseRightDown()
            else inputManager.sendMouseLeftDown()
        }
        TouchKeyType.LEFT -> {
            if (!isLeftHand) inputManager.sendMouseLeftDown()
            else inputManager.sendMouseRightDown()
        }
    }
}

private fun sendUp(
    type: TouchKeyType,
    isLeftHand: Boolean,
    inputManager: InputManager,
) {
    when (type) {
        TouchKeyType.RIGHT -> {
            if (!isLeftHand) inputManager.sendMouseRightUp()
            else inputManager.sendMouseLeftUp()
        }
        TouchKeyType.LEFT -> {
            if (!isLeftHand) inputManager.sendMouseLeftUp()
            else inputManager.sendMouseRightUp()
        }
    }
}