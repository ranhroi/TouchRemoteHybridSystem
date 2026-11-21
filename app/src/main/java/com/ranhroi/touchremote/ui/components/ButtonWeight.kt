package com.ranhroi.touchremote.ui.components

import android.os.Handler
import android.os.Looper
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ranhroi.touchremote.utils.IconsMap
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ButtonWeight(
    modifier: Modifier = Modifier,
    text: String? = null,
    iconName: String = "Help",
    colorStroke: Color = Color.DarkGray,
    shape: Shape = CircleShape,
    color: Color = MaterialTheme.colorScheme.primary,
    textStyle: TextStyle = MaterialTheme.typography.labelSmall,
    aspectRatio: Float = 0f,
    iconScale: Float = 0.9f,
    speedPressed: Long = 100,
    rotationDegrees: Float = 0f,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onLongPressRepeat: (() -> Unit)? = null,
    content: @Composable () -> Unit = {}
) {
    val icon = remember(iconName) { IconsMap.getIconOrDefault(iconName) }

    val handler = remember { Handler(Looper.getMainLooper()) }
    var isKeyPressed by remember { mutableStateOf(false) }
    var isActiveKey by remember { mutableStateOf(false) }
    var lastDownTime by remember { mutableLongStateOf(0L) }

    // 🟢 HIỆU ỨNG MÀU KHI NHẤN
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isKeyPressed -> Color.Yellow.copy(alpha = 0.3f)   // Màu vàng khi long press
            isActiveKey -> Color.LightGray.copy(alpha = 0.4f)     // Màu khi nhấn thường
            else -> Color.Transparent                           // Màu bình thường
        },
        animationSpec = tween(durationMillis = 150)
    )

    // 🟢 HIỆU ỨNG SCALE KHI NHẤN
    val scale by animateFloatAsState(
        targetValue = if (isActiveKey) 0.85f else 1f,
        animationSpec = tween(durationMillis = 150)
    )

    LaunchedEffect(isKeyPressed) {
        if (isKeyPressed && onLongPressRepeat != null) {
            delay(80.milliseconds)
            while (isKeyPressed) {
                onLongPressRepeat()
                delay(speedPressed.milliseconds)
            }
        }
    }


    Card(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .border(1.dp, colorStroke, shape)
            .pointerInput(onClick, onLongClick, onLongPressRepeat) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.first()

                        when {
                            // 🟢 Người dùng ấn xuống
                            change.changedToDown() -> {
                                isActiveKey = true
                                lastDownTime = System.currentTimeMillis()

                                handler.removeCallbacksAndMessages(null)

                                // Nếu có repeat → chờ 500ms rồi bắt đầu repeat
                                if (onLongPressRepeat != null) {
                                    handler.postDelayed({
                                        if (change.pressed) {
                                            isKeyPressed = true
                                        }
                                    }, 500)
                                }
                            }

                            // 🔴 Người dùng thả tay
                            change.changedToUp() -> {
                                val upTime = System.currentTimeMillis()
                                handler.removeCallbacksAndMessages(null)

                                val pressDuration = upTime - lastDownTime

                                when {
                                    // 🟡 Long Click xảy ra (chỉ chạy nếu có callback)
                                    pressDuration >= 500 && onLongClick != null -> {
                                        onLongClick.invoke()
                                    }

                                    // 🟢 Click thường
                                    pressDuration < 190 && onClick != null -> {
                                        onClick.invoke()
                                    }
                                }

                                // Reset
                                isKeyPressed = false
                                isActiveKey = false
                            }
                        }
                    }
                }
            }
        ,
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(0.dp),
        shape = shape
    ) {
        Box(
            modifier = if (aspectRatio > 0f) Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio) else Modifier,
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = if (rotationDegrees != 0f) {
                    Modifier
                        .graphicsLayer {
                            rotationZ = rotationDegrees
                            transformOrigin = TransformOrigin.Center
                        }
                        .fillMaxSize()
                        .padding(8.dp)
                } else {
                    Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                IconAny(
                    text = iconName,
                    icon = icon,
                    isRed = isKeyPressed,
                    color = color,
                    modifier = Modifier.scale(iconScale).size(18.dp)
                )

                if (!text.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = if (isKeyPressed) "🔄" else text,
                        style = textStyle,
                        color = if (isKeyPressed) Color.Red else color,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
            }
        }
        content()
    }
}