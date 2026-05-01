package io.github.ikinocore.gemread.android.data.db.template

import kotlinx.serialization.Serializable

@Serializable
data class PromptTemplateDto(
    val title: String,
    val systemPrompt: String,
    val sortOrder: Int,
    val isDefault: Boolean,
) {
    fun toEntity(): PromptTemplateEntity = PromptTemplateEntity(
        title = title,
        systemPrompt = systemPrompt,
        sortOrder = sortOrder,
        isDefault = isDefault,
    )
}
