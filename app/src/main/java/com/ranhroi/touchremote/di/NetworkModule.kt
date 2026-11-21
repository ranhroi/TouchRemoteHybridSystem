package com.ranhroi.touchremote.di

import android.content.Context
import com.ranhroi.touchremote.scanner.BluetoothScanner
import com.ranhroi.touchremote.send.SenderOutput
import com.ranhroi.touchremote.ui.screens.server.ConnectionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideBluetoothScanner(
        @ApplicationContext context: Context
    ): BluetoothScanner {
        return BluetoothScanner(context)
    }


    @Provides
    @Singleton
    fun provideConnectionManager(
        @ApplicationContext context: Context
    ): ConnectionManager {
        return ConnectionManager(context)
    }

    @Provides
    @Singleton
    fun provideSender(): SenderOutput {
        return SenderOutput()
    }

}
