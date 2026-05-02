package io.github.ikinocore.gemread.android.data.db.history

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class HistoryType {
    TEXT,
    IMAGE,
}

@Entity(tableName = "history_entries")
data class HistoryEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: HistoryType,
    val inputText: String,
    val outputText: String,
    val modelName: String,
    val templateId: Long?,
    val createdAt: Long = System.currentTimeMillis(),
    val pinned: Boolean = false,
    val imagePath: String? = null,
)
