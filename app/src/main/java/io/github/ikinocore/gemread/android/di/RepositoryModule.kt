package io.github.ikinocore.gemread.android.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.ikinocore.gemread.android.data.repository.GenerationRepositoryImpl
import io.github.ikinocore.gemread.android.data.repository.HistoryRepositoryImpl
import io.github.ikinocore.gemread.android.data.repository.PromptTemplateRepositoryImpl
import io.github.ikinocore.gemread.android.domain.repository.GenerationRepository
import io.github.ikinocore.gemread.android.domain.repository.HistoryRepository
import io.github.ikinocore.gemread.android.domain.repository.PromptTemplateRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindPromptTemplateRepository(
        impl: PromptTemplateRepositoryImpl,
    ): PromptTemplateRepository

    @Binds
    @Singleton
    abstract fun bindGenerationRepository(
        impl: GenerationRepositoryImpl,
    ): GenerationRepository

    @Binds
    @Singleton
    abstract fun bindHistoryRepository(
        impl: HistoryRepositoryImpl,
    ): HistoryRepository
}
