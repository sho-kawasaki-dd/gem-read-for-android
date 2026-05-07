package io.github.ikinocore.gemread.android.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ikinocore.gemread.android.data.db.AppDatabase
import io.github.ikinocore.gemread.android.data.db.history.HistoryEntryEntity
import io.github.ikinocore.gemread.android.data.db.history.HistoryType
import io.github.ikinocore.gemread.android.data.db.template.PromptTemplateEntity
import io.github.ikinocore.gemread.android.data.history.HistoryImageStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RepositoryIntegrationTest {

    private lateinit var database: AppDatabase
    private lateinit var promptTemplateRepository: PromptTemplateRepositoryImpl
    private lateinit var historyRepository: HistoryRepositoryImpl
    private lateinit var filesDir: File

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        filesDir = context.filesDir
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        promptTemplateRepository = PromptTemplateRepositoryImpl(database)
        historyRepository = HistoryRepositoryImpl(
            database,
            HistoryImageStore(context, kotlinx.coroutines.Dispatchers.IO),
        )
        File(filesDir, "history").deleteRecursively()
    }

    @After
    fun tearDown() {
        database.close()
        File(filesDir, "history").deleteRecursively()
    }

    @Test
    fun deleteTemplateShouldPromoteAlternativeWhenDeletingDefault() = runBlocking {
        val firstId = promptTemplateRepository.insertTemplate(
            PromptTemplateEntity(
                title = "Default",
                systemPrompt = "default",
                sortOrder = 0,
                isDefault = true,
            ),
        )
        promptTemplateRepository.insertTemplate(
            PromptTemplateEntity(
                title = "Alternative",
                systemPrompt = "alt",
                sortOrder = 1,
                isDefault = false,
            ),
        )

        promptTemplateRepository.deleteTemplate(firstId)

        val templates = promptTemplateRepository.getAllTemplates().first()
        assertEquals(1, templates.size)
        assertEquals("Alternative", templates.single().title)
        assertTrue(templates.single().isDefault)
    }

    @Test
    fun pruneHistoryShouldIgnorePinnedEntriesAndDeleteManagedFiles() = runBlocking {
        val historyDir = File(filesDir, "history").apply { mkdirs() }
        val prunableImage = File(historyDir, "10.jpg").apply { writeText("old") }

        historyRepository.insertHistory(
            HistoryEntryEntity(
                id = 10,
                type = HistoryType.IMAGE,
                inputText = "old",
                outputText = "old",
                modelName = "gemini-2.5-flash",
                templateId = null,
                createdAt = 1,
                pinned = false,
                imagePath = prunableImage.absolutePath,
            ),
        )
        historyRepository.insertHistory(
            HistoryEntryEntity(
                id = 11,
                type = HistoryType.TEXT,
                inputText = "middle",
                outputText = "middle",
                modelName = "gemini-2.5-flash",
                templateId = null,
                createdAt = 2,
                pinned = false,
            ),
        )
        historyRepository.insertHistory(
            HistoryEntryEntity(
                id = 12,
                type = HistoryType.TEXT,
                inputText = "new",
                outputText = "new",
                modelName = "gemini-2.5-flash",
                templateId = null,
                createdAt = 3,
                pinned = false,
            ),
        )
        historyRepository.insertHistory(
            HistoryEntryEntity(
                id = 13,
                type = HistoryType.TEXT,
                inputText = "pinned",
                outputText = "pinned",
                modelName = "gemini-2.5-flash",
                templateId = null,
                createdAt = 4,
                pinned = true,
            ),
        )

        historyRepository.pruneHistory(maxCount = 2, maxDays = 365)

        val remaining = historyRepository.getAllHistory().first()
        assertEquals(setOf(11L, 12L, 13L), remaining.map { it.id }.toSet())
        assertFalse(prunableImage.exists())
    }
}