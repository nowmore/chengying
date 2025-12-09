package com.w3n9.chengying.di

import com.w3n9.chengying.data.repository.AppRepositoryImpl
import com.w3n9.chengying.data.repository.DisplayRepositoryImpl
import com.w3n9.chengying.data.repository.TaskRepositoryImpl
import com.w3n9.chengying.data.source.DisplayDataSource
import com.w3n9.chengying.data.source.LocalDisplayDataSource
import com.w3n9.chengying.data.source.PreferencesSettingsDataSource
import com.w3n9.chengying.data.source.SettingsDataSource
import com.w3n9.chengying.domain.repository.AppRepository
import com.w3n9.chengying.domain.repository.DisplayRepository
import com.w3n9.chengying.domain.repository.TaskRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindDisplayDataSource(
        localDisplayDataSource: LocalDisplayDataSource
    ): DisplayDataSource

    @Binds
    @Singleton
    abstract fun bindSettingsDataSource(
        preferencesSettingsDataSource: PreferencesSettingsDataSource
    ): SettingsDataSource

    @Binds
    @Singleton
    abstract fun bindDisplayRepository(
        displayRepositoryImpl: DisplayRepositoryImpl
    ): DisplayRepository

    @Binds
    @Singleton
    abstract fun bindAppRepository(
        appRepositoryImpl: AppRepositoryImpl
    ): AppRepository

    @Binds
    @Singleton
    abstract fun bindTaskRepository(
        taskRepositoryImpl: TaskRepositoryImpl
    ): TaskRepository
}
