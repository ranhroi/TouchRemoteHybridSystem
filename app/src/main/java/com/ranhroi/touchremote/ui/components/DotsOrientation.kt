package com.ranhroi.touchremote.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Hướng hiển thị của dots loading.
 */
enum class DotsOrientation {
    HORIZONTAL,  // Ngang (mặc định)
    VERTICAL     // Dọc
}

/**
 * Thanh tiến trình dạng indeterminate dots (chấm chạy) — kiểu Material 3.
 *
 * @param orientation Hướng hiển thị: HORIZONTAL (ngang) hoặc VERTICAL (dọc)
 * @param dotSize     Kích thước mỗi chấm (mặc định 8.dp)
 * @param spacing     Khoảng cách giữa các chấm (mặc định 8.dp)
 * @param dotCount    Số chấm (mặc định 3)
 * @param durationMs  Thời gian animation (mặc định 800ms)
 */
@Composable
fun DotsLoadingIndicator(
    modifier: Modifier = Modifier,
    orientation: DotsOrientation = DotsOrientation.HORIZONTAL,
    dotSize: Dp = 8.dp,
    spacing: Dp = 8.dp,
    dotCount: Int = 3,
    durationMs: Int = 800
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dots_animation"
    )

    // Nội dung: danh sách các chấm
    @Composable
    fun DotsContent() {
        repeat(dotCount) { index ->
            val delay = index * (1f / dotCount)
            val alpha = when {
                progress < delay -> 0.2f
                progress > delay + (1f / dotCount) -> 0.2f
                else -> 1f
            }

            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
            )

            // Spacer giữa các chấm (không thêm sau chấm cuối)
            if (index < dotCount - 1) {
                when (orientation) {
                    DotsOrientation.HORIZONTAL -> Spacer(Modifier.width(spacing))
                    DotsOrientation.VERTICAL -> Spacer(Modifier.height(spacing))
                }
            }
        }
    }

    when (orientation) {
        DotsOrientation.HORIZONTAL -> {
            Row(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DotsContent()
            }
        }

        DotsOrientation.VERTICAL -> {
            Column(
                modifier = modifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                DotsContent()
            }
        }
    }
}