package io.github.ikinocore.gemread.android.domain.repository

import io.github.ikinocore.gemread.android.data.db.history.HistoryEntryEntity
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    fun getAllHistory(): Flow<List<HistoryEntryEntity>>
    fun searchHistory(query: String, pinnedOnly: Boolean): Flow<List<HistoryEntryEntity>>
    suspend fun insertHistory(entry: HistoryEntryEntity): Long
    suspend fun updateHistory(entry: HistoryEntryEntity)
    suspend fun deleteHistory(id: Long)
    suspend fun getHistoryById(id: Long): HistoryEntryEntity?
    suspend fun pruneHistory(maxCount: Int, maxDays: Int)
}
