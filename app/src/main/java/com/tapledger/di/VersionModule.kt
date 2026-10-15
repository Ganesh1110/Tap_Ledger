package com.tapledger.di

import android.content.Context
import com.tapledger.core.version.AppVersionManager
import com.tapledger.core.version.AppVersionManagerImpl
import com.tapledger.core.version.ReleaseNotesRepository
import com.tapledger.core.version.ReleaseNotesRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VersionModule {

    @Provides
    @Singleton
    fun provideReleaseNotesRepository(): ReleaseNotesRepository = ReleaseNotesRepositoryImpl()

    @Provides
    @Singleton
    fun provideAppVersionManager(
        @ApplicationContext context: Context,
        releaseNotesRepository: ReleaseNotesRepository
    ): AppVersionManager = AppVersionManagerImpl(context, releaseNotesRepository)
}
