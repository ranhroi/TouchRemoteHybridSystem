package com.ranhroi.touchremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun BadgedIcon(
    badge: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    badgeOffset: Pair<Dp, Dp> = Pair(8.dp, (-8).dp),
    badgeBackgroundColor: Color = Color.Red,
    badgeMinSize: Dp = 16.dp,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        content()
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(18.dp)
                .offset(x = badgeOffset.first, y = badgeOffset.second)
                .wrapContentSize(unbounded = true)
                .defaultMinSize(minWidth = badgeMinSize, minHeight = badgeMinSize)
                .border(width = 1.dp, color = Color.White, shape = CircleShape)
                .background(badgeBackgroundColor, shape = CircleShape)
                .padding(horizontal = 2.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            badge()
        }
    }
}
