package io.github.ikinocore.gemread.android.data.repository

import io.github.ikinocore.gemread.android.data.db.AppDatabase
import io.github.ikinocore.gemread.android.data.db.history.HistoryEntryEntity
import io.github.ikinocore.gemread.android.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepositoryImpl @Inject constructor(
    database: AppDatabase,
) : HistoryRepository {
    private val dao = database.historyDao()

    override fun getAllHistory(): Flow<List<HistoryEntryEntity>> = dao.getAllHistory()

    override fun searchHistory(query: String, pinnedOnly: Boolean): Flow<List<HistoryEntryEntity>> = dao.searchHistory(query, pinnedOnly)

    override suspend fun insertHistory(entry: HistoryEntryEntity): Long = dao.insertHistory(entry)

    override suspend fun updateHistory(entry: HistoryEntryEntity) = dao.updateHistory(entry)

    override suspend fun deleteHistory(id: Long) = dao.deleteHistoryById(id)

    override suspend fun getHistoryById(id: Long): HistoryEntryEntity? = dao.getHistoryById(id)

    override suspend fun pruneHistory(maxCount: Int, maxDays: Int) {
        // Prune by count first
        dao.pruneByCount(maxCount)

        // Prune by date
        val threshold = System.currentTimeMillis() - (maxDays.toLong() * 24 * 60 * 60 * 1000)
        dao.pruneByDate(threshold)
    }
}
