package com.w3n9.chengying.di

import com.w3n9.chengying.data.repository.CursorRepositoryImpl
import com.w3n9.chengying.domain.repository.CursorRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CursorModule {

    @Binds
    @Singleton
    abstract fun bindCursorRepository(
        impl: CursorRepositoryImpl
    ): CursorRepository
}
