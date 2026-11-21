package com.ranhroi.touchremote.ui.screens.splash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ranhroi.touchremote.ui.components.CircleLoading
import com.ranhroi.touchremote.ui.components.DotsLoadingIndicator

@Composable
fun SplashScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center){
        DotsLoadingIndicator()
    }
}