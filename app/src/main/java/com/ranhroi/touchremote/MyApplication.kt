package com.ranhroi.touchremote

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import com.ranhroi.touchremote.utils.IconsMap

@HiltAndroidApp
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        IconsMap.initialize()
    }
}