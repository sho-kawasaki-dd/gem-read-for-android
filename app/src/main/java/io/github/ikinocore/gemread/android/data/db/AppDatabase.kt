package io.github.ikinocore.gemread.android.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import io.github.ikinocore.gemread.android.data.db.template.PromptTemplateDao
import io.github.ikinocore.gemread.android.data.db.template.PromptTemplateEntity

@Database(
    entities = [PromptTemplateEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun promptTemplateDao(): PromptTemplateDao
}
