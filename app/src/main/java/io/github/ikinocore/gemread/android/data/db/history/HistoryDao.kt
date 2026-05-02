package io.github.ikinocore.gemread.android.data.db.history

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history_entries ORDER BY createdAt DESC")
    fun getAllHistory(): Flow<List<HistoryEntryEntity>>

    @Query(
        """
        SELECT * FROM history_entries
        WHERE (inputText LIKE '%' || :query || '%' OR outputText LIKE '%' || :query || '%')
        AND (:pinnedOnly = 0 OR pinned = 1)
        ORDER BY createdAt DESC
        """,
    )
    fun searchHistory(query: String, pinnedOnly: Boolean): Flow<List<HistoryEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entry: HistoryEntryEntity): Long

    @Update
    suspend fun updateHistory(entry: HistoryEntryEntity)

    @Delete
    suspend fun deleteHistory(entry: HistoryEntryEntity)

    @Query("DELETE FROM history_entries WHERE id = :id")
    suspend fun deleteHistoryById(id: Long)

    @Query("SELECT * FROM history_entries WHERE id = :id")
    suspend fun getHistoryById(id: Long): HistoryEntryEntity?

    // Pruning: Delete old entries that are not pinned
    @Query(
        """
        DELETE FROM history_entries
        WHERE pinned = 0
        AND (id NOT IN (SELECT id FROM history_entries WHERE pinned = 1 OR 1=1 ORDER BY createdAt DESC LIMIT :maxCount))
        """,
    )
    suspend fun pruneByCount(maxCount: Int)

    @Query("DELETE FROM history_entries WHERE pinned = 0 AND createdAt < :thresholdTimestamp")
    suspend fun pruneByDate(thresholdTimestamp: Long)
}
