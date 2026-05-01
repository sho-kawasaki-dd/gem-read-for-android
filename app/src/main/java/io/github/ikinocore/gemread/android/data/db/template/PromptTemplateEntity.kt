package io.github.ikinocore.gemread.android.data.db.template

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "prompt_templates")
data class PromptTemplateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val systemPrompt: String,
    val sortOrder: Int,
    val isDefault: Boolean,
)
