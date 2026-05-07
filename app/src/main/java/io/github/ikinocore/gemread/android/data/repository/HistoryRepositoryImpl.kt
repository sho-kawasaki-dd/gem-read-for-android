package io.github.ikinocore.gemread.android.data.repository

import androidx.room.withTransaction
import io.github.ikinocore.gemread.android.data.db.AppDatabase
import io.github.ikinocore.gemread.android.data.db.history.HistoryEntryEntity
import io.github.ikinocore.gemread.android.data.history.HistoryImageStore
import io.github.ikinocore.gemread.android.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val historyImageStore: HistoryImageStore,
) : HistoryRepository {
    private val dao = database.historyDao()

    override fun getAllHistory(): Flow<List<HistoryEntryEntity>> = dao.getAllHistory()

    override fun searchHistory(query: String, pinnedOnly: Boolean): Flow<List<HistoryEntryEntity>> = dao.searchHistory(query, pinnedOnly)

    override suspend fun insertHistory(entry: HistoryEntryEntity): Long = dao.insertHistory(entry)

    override suspend fun updateHistory(entry: HistoryEntryEntity) = dao.updateHistory(entry)

    override suspend fun deleteHistory(id: Long) {
        val entry = dao.getHistoryById(id) ?: return
        deleteEntries(listOf(entry))
    }

    override suspend fun getHistoryById(id: Long): HistoryEntryEntity? = dao.getHistoryById(id)

    override suspend fun pruneHistory(maxCount: Int, maxDays: Int) {
        // Prune by count first so retention count is enforced before age-based cleanup.
        deleteEntries(dao.getPrunableByCount(maxCount))

        // Then prune by date using the remaining unpinned entries.
        val threshold = System.currentTimeMillis() - (maxDays.toLong() * 24 * 60 * 60 * 1000)
        deleteEntries(dao.getPrunableByDate(threshold))
    }

    override suspend fun sweepOrphanedImages() {
        historyImageStore.sweepOrphanedImages(dao.getAllImagePaths())
    }

    private suspend fun deleteEntries(entries: List<HistoryEntryEntity>) {
        if (entries.isEmpty()) return

        val ids = entries.map { it.id }
        val imagePaths = entries.mapNotNull { it.imagePath }

        database.withTransaction {
            dao.deleteHistoryByIds(ids)
        }

        // 画像削除は DB transaction に載せられないため、永続状態を壊さない best effort cleanup とする。
        historyImageStore.deleteManagedImages(imagePaths)
    }
}
