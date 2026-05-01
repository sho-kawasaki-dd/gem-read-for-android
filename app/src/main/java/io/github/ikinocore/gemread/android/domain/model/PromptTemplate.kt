package io.github.ikinocore.gemread.android.domain.model

data class PromptTemplate(
    val id: Long = 0L,
    val title: String,
    val systemPrompt: String,
    val sortOrder: Int,
    val isDefault: Boolean,
)