package io.github.ikinocore.gemread.android.data.db.template

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PromptTemplateDao {
    @Query("SELECT * FROM prompt_templates ORDER BY sortOrder ASC")
    fun getAllTemplates(): Flow<List<PromptTemplateEntity>>

    @Query("SELECT * FROM prompt_templates WHERE id = :id")
    suspend fun getTemplateById(id: Long): PromptTemplateEntity?

    @Query("SELECT * FROM prompt_templates WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultTemplate(): PromptTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: PromptTemplateEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplates(templates: List<PromptTemplateEntity>)

    @Update
    suspend fun updateTemplate(template: PromptTemplateEntity)

    @Delete
    suspend fun deleteTemplate(template: PromptTemplateEntity)

    @Query("UPDATE prompt_templates SET isDefault = 0 WHERE isDefault = 1")
    suspend fun clearDefault()

    @Query("SELECT COUNT(*) FROM prompt_templates")
    suspend fun getCount(): Int

    @Query("SELECT * FROM prompt_templates WHERE id != :excludeId ORDER BY sortOrder ASC LIMIT 1")
    suspend fun getFirstAlternative(excludeId: Long): PromptTemplateEntity?
}
