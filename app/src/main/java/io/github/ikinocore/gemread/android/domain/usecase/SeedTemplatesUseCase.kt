package io.github.ikinocore.gemread.android.domain.usecase

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.ikinocore.gemread.android.data.db.template.PromptTemplateDto
import io.github.ikinocore.gemread.android.domain.repository.PromptTemplateRepository
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Use case to seed initial prompt templates from assets if the database is empty.
 */
class SeedTemplatesUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: PromptTemplateRepository,
) {
    suspend operator fun invoke() {
        if (repository.getCount() > 0) return

        runCatching {
            val jsonString = context.assets.open("prompt_templates_ja.json")
                .bufferedReader()
                .use { it.readText() }
            val dtos = Json.decodeFromString<List<PromptTemplateDto>>(jsonString)
            repository.seedTemplates(dtos.map { it.toEntity() })
        }
    }
}
