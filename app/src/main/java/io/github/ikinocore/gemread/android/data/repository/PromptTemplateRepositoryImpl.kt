package io.github.ikinocore.gemread.android.data.repository

import androidx.room.withTransaction
import io.github.ikinocore.gemread.android.data.db.AppDatabase
import io.github.ikinocore.gemread.android.data.db.template.PromptTemplateEntity
import io.github.ikinocore.gemread.android.domain.repository.PromptTemplateRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PromptTemplateRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
) : PromptTemplateRepository {
    private val dao = database.promptTemplateDao()

    override fun getAllTemplates(): Flow<List<PromptTemplateEntity>> = dao.getAllTemplates()

    override suspend fun getTemplateById(id: Long): PromptTemplateEntity? = dao.getTemplateById(id)

    override suspend fun getDefaultTemplate(): PromptTemplateEntity? = dao.getDefaultTemplate()

    override suspend fun insertTemplate(template: PromptTemplateEntity): Long = database.withTransaction {
        if (template.isDefault) {
            dao.clearDefault()
        }
        dao.insertTemplate(template)
    }

    override suspend fun updateTemplate(template: PromptTemplateEntity) = database.withTransaction {
        if (template.isDefault) {
            dao.clearDefault()
        }
        dao.updateTemplate(template)
    }

    override suspend fun deleteTemplate(id: Long) = database.withTransaction {
        val template = dao.getTemplateById(id) ?: return@withTransaction
        val count = dao.getCount()
        if (count <= 1) {
            // Plan: "件数が 1 件のときは削除不可"
            return@withTransaction
        }

        dao.deleteTemplate(template)

        if (template.isDefault) {
            // Plan: "デフォルト指定中のテンプレを削除する場合は、削除前に sortOrder 最小の他テンプレを default に昇格させる"
            val alternative = dao.getFirstAlternative(id)
            alternative?.let {
                dao.updateTemplate(it.copy(isDefault = true))
            }
        }
    }

    override suspend fun setDefaultTemplate(id: Long) = database.withTransaction {
        val template = dao.getTemplateById(id) ?: return@withTransaction
        dao.clearDefault()
        dao.updateTemplate(template.copy(isDefault = true))
    }

    override suspend fun seedTemplates(templates: List<PromptTemplateEntity>) {
        dao.insertTemplates(templates)
    }

    override suspend fun getCount(): Int = dao.getCount()
}
