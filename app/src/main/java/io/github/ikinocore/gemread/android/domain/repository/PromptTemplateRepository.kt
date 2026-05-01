package io.github.ikinocore.gemread.android.domain.repository

import io.github.ikinocore.gemread.android.domain.model.PromptTemplate
import kotlinx.coroutines.flow.Flow

/**
 * Repository for prompt templates and default selection state.
 */
interface PromptTemplateRepository {
    fun observeTemplates(): Flow<List<PromptTemplate>>

    suspend fun getDefaultTemplateId(): Long?
}
