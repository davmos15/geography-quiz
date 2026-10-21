package com.geoquiz.app.di

import com.geoquiz.app.ui.whatsnew.AppInstallInfo
import com.geoquiz.app.ui.whatsnew.PackageManagerAppInstallInfo
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds [AppInstallInfo] for the one-off "What's new" screen (P10-F). */
@Module
@InstallIn(SingletonComponent::class)
abstract class WhatsNewModule {

    @Binds
    @Singleton
    abstract fun bindAppInstallInfo(impl: PackageManagerAppInstallInfo): AppInstallInfo
}
