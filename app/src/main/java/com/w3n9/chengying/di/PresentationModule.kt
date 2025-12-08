package com.w3n9.chengying.di

import com.w3n9.chengying.data.repository.PresentationRepositoryImpl
import com.w3n9.chengying.domain.repository.PresentationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.scopes.ActivityScoped

@Module
@InstallIn(ActivityComponent::class)
abstract class PresentationModule {

    @Binds
    @ActivityScoped
    abstract fun bindPresentationRepository(
        impl: PresentationRepositoryImpl
    ): PresentationRepository
}
