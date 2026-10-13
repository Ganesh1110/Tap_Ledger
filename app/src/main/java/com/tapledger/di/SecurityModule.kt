package com.tapledger.di

import android.content.Context
import com.tapledger.core.security.AndroidMpinKeystore
import com.tapledger.core.security.AppLockCoordinator
import com.tapledger.core.security.MpinClock
import com.tapledger.core.security.MpinKeystore
import com.tapledger.core.security.MpinManager
import com.tapledger.core.security.MpinManagerImpl
import com.tapledger.core.security.SystemMpinClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideMpinClock(): MpinClock = SystemMpinClock()

    @Provides
    @Singleton
    fun provideMpinKeystore(): MpinKeystore = AndroidMpinKeystore()

    @Provides
    @Singleton
    fun provideMpinManager(
        @ApplicationContext context: Context,
        keystore: MpinKeystore,
        clock: MpinClock
    ): MpinManager = MpinManagerImpl(context, keystore, clock)

    @Provides
    @Singleton
    fun provideAppLockCoordinator(
        mpinManager: MpinManager,
        clock: MpinClock
    ): AppLockCoordinator = AppLockCoordinator(mpinManager, clock)
}
