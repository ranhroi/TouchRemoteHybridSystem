package com.ranhroi.touchremote.di

import android.content.Context
import com.ranhroi.touchremote.utils.DataStoreConfig
import com.ranhroi.touchremote.utils.DataStoreManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun provideSharedDataStore(@ApplicationContext context: Context): DataStoreManager {
        return DataStoreManager(context)
    }


    @Provides
    @Singleton
    fun provideDataStoreConfig(
        manager: DataStoreManager
    ): DataStoreConfig {
        return DataStoreConfig(manager)
    }
}