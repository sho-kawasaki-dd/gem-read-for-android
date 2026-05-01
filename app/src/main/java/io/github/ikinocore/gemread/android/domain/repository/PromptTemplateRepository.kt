package io.github.ikinocore.gemread.android.domain.repository

import io.github.ikinocore.gemread.android.data.db.template.PromptTemplateEntity
import kotlinx.coroutines.flow.Flow

interface PromptTemplateRepository {
    fun getAllTemplates(): Flow<List<PromptTemplateEntity>>
    suspend fun getTemplateById(id: Long): PromptTemplateEntity?
    suspend fun getDefaultTemplate(): PromptTemplateEntity?
    suspend fun insertTemplate(template: PromptTemplateEntity): Long
    suspend fun updateTemplate(template: PromptTemplateEntity)
    suspend fun deleteTemplate(id: Long)
    suspend fun setDefaultTemplate(id: Long)
    suspend fun seedTemplates(templates: List<PromptTemplateEntity>)
    suspend fun getCount(): Int
}
