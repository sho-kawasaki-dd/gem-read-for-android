package io.github.ikinocore.gemread.android.data.db.template

import io.github.ikinocore.gemread.android.domain.model.PromptTemplate
import kotlinx.coroutines.flow.Flow

interface PromptTemplateLocalDataSource {
    fun observeTemplates(): Flow<List<PromptTemplate>>

    suspend fun getDefaultTemplateId(): Long?
}