package io.github.ikinocore.gemread.android.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import io.github.ikinocore.gemread.android.data.db.history.HistoryDao
import io.github.ikinocore.gemread.android.data.db.history.HistoryEntryEntity
import io.github.ikinocore.gemread.android.data.db.history.HistoryType
import io.github.ikinocore.gemread.android.data.db.template.PromptTemplateDao
import io.github.ikinocore.gemread.android.data.db.template.PromptTemplateEntity

class Converters {
    @TypeConverter
    fun fromHistoryType(value: HistoryType) = value.name

    @TypeConverter
    fun toHistoryType(value: String) = enumValueOf<HistoryType>(value)
}

@Database(
    entities = [
        PromptTemplateEntity::class,
        HistoryEntryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun promptTemplateDao(): PromptTemplateDao
    abstract fun historyDao(): HistoryDao
}
