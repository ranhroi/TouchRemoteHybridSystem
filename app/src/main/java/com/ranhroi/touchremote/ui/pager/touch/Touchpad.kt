package com.ranhroi.touchremote.ui.pager.touch

import android.os.Handler
import android.os.Looper
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import com.ranhroi.touchremote.send.InputManager
import kotlin.math.abs

@Composable
fun Touchpad(
    modifier: Modifier = Modifier,
    inputManager: InputManager,
    isCursor: Boolean = true,
    cursorColor: Color = Color(0xFF0FBEAD),
    cursorDraggingColor: Color = Color(0xFF006259),
    cursorRadius: Float = 70f,
    content: @Composable ((screenSize: Offset) -> Unit) = {}
) {
    val handler = remember { Handler(Looper.getMainLooper()) }

    // Sử dụng Animatable hoặc State để tối ưu vẽ
    var isDragging by remember { mutableStateOf(false) }
    var isShow by remember { mutableStateOf(false) }
    var cX by remember { mutableFloatStateOf(0f) }
    var cY by remember { mutableFloatStateOf(0f) }

    var lastDownTime = remember { 0L }
    var startX = remember { 0f }
    var startY = remember { 0f }
    var hasMoved = remember { false }

    val animatedColor by animateColorAsState(
        targetValue = if (isDragging) cursorDraggingColor else cursorColor,
        label = "CursorColor"
    )

    BoxWithConstraints(
        modifier = modifier
            .defaultMinSize()
            .background(Color.Transparent)
            .pointerInput(inputManager) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.first()

                        when {
                            change.changedToDown() -> {
                                lastDownTime = System.currentTimeMillis()
                                startX = change.position.x
                                startY = change.position.y
                                hasMoved = false
                                isShow = true
                                cX = startX
                                cY = startY
                                handler.postDelayed({
                                    if (!hasMoved) {
                                        isDragging = true
                                        inputManager.sendDragStart()
                                    }
                                }, 500)
                            }

                            change.changedToUp() -> {
                                handler.removeCallbacksAndMessages(null)
                                val currentTime = System.currentTimeMillis()

                                if ((currentTime - lastDownTime) < 180) {
                                    inputManager.onLeftClick()
                                }

                                if (isDragging) {
                                    isDragging = false
                                    inputManager.sendDragEnd()
                                }
                                isShow = false
                            }

                            change.pressed -> {
                                val dx = change.position.x - startX
                                val dy = change.position.y - startY

                                if (abs(dx) > 1f || abs(dy) > 1f) {
                                    hasMoved = true
                                }

                                // Cập nhật tọa độ để vẽ (Sẽ cực mượt vì dùng Canvas bên dưới)
                                cX = change.position.x
                                cY = change.position.y

                                inputManager.onMouseMove(dx.toInt(), dy.toInt())

                                startX = change.position.x
                                startY = change.position.y
                                change.consume() // Báo cho hệ thống đã xử lý xong event này
                            }
                        }
                    }
                }
            }
            .drawWithContent {
                // 1. Vẽ các nội dung khác trong Box trước
                drawContent()

                // 2. Vẽ con trỏ có đổ bóng nếu đang hiển thị
                if (isShow && isCursor) {
                    drawIntoCanvas { canvas ->
                        val paint = Paint().asFrameworkPaint().apply {
                            isAntiAlias = true
                            // Cấu hình đổ bóng: độ mờ, độ lệch x, độ lệch y, màu bóng (đen nhạt)
                            setShadowLayer(
                                15f,
                                0f,
                                5f,
                                android.graphics.Color.argb(100, 0, 0, 0)
                            )
                        }

                        // Vẽ hình tròn với Paint có bóng
                        canvas.nativeCanvas.drawCircle(
                            cX,
                            cY,
                            cursorRadius,
                            paint.apply {
                                color = animatedColor.toArgb()
                            }
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        content(Offset(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()))
    }
}

