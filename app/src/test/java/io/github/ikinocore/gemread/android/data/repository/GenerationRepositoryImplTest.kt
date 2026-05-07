package io.github.ikinocore.gemread.android.data.repository

import io.github.ikinocore.gemread.android.data.api.GeminiClient
import io.github.ikinocore.gemread.android.data.db.history.HistoryEntryEntity
import io.github.ikinocore.gemread.android.data.db.history.HistoryType
import io.github.ikinocore.gemread.android.data.db.template.PromptTemplateEntity
import io.github.ikinocore.gemread.android.data.image.ImageDownscaler
import io.github.ikinocore.gemread.android.data.prefs.AppPreferences
import io.github.ikinocore.gemread.android.domain.model.GenerationEvent
import io.github.ikinocore.gemread.android.domain.repository.HistoryRepository
import io.github.ikinocore.gemread.android.domain.repository.PromptTemplateRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationRepositoryImplTest {

    private val geminiClient: GeminiClient = mockk()
    private val imageDownscaler: ImageDownscaler = mockk()
    private val promptTemplateRepository: PromptTemplateRepository = mockk()
    private val historyRepository: HistoryRepository = mockk()
    private val appPreferences: AppPreferences = mockk()

    private val repository = GenerationRepositoryImpl(
        geminiClient = geminiClient,
        imageDownscaler = imageDownscaler,
        promptTemplateRepository = promptTemplateRepository,
        historyRepository = historyRepository,
        appPreferences = appPreferences,
    )

    @Test
    fun `generate should stream text save history and prune after completion`() = runTest {
        val template = PromptTemplateEntity(
            id = 7,
            title = "Translate",
            systemPrompt = "Translate to Japanese",
            sortOrder = 0,
            isDefault = true,
        )
        val insertedEntries = mutableListOf<HistoryEntryEntity>()

        coEvery { promptTemplateRepository.getDefaultTemplate() } returns template
        every { appPreferences.baseSystemPrompt } returns flowOf("Base prompt")
        every { appPreferences.modelName } returns flowOf("gemini-2.5-flash")
        every { appPreferences.historyRetentionCount } returns flowOf(200)
        every { appPreferences.historyRetentionDays } returns flowOf(90)
        every {
            geminiClient.generateContent(
                prompt = "hello",
                systemPrompt = "Base prompt\n\nTranslate to Japanese",
                image = null,
            )
        } returns flowOf("kon", "nichiwa")
        coEvery { historyRepository.insertHistory(any()) } answers {
            insertedEntries += firstArg<HistoryEntryEntity>()
            42L
        }
        coEvery { historyRepository.pruneHistory(any(), any()) } returns Unit

        val events = repository.generate(prompt = "hello", imageUri = null, templateId = null).toList()

        assertEquals(
            listOf(
                GenerationEvent.Chunk("kon"),
                GenerationEvent.Chunk("nichiwa"),
                GenerationEvent.Completed(42L),
            ),
            events,
        )
        assertEquals(1, insertedEntries.size)
        assertEquals(HistoryType.TEXT, insertedEntries.single().type)
        assertEquals("hello", insertedEntries.single().inputText)
        assertEquals("konnichiwa", insertedEntries.single().outputText)
        assertEquals("gemini-2.5-flash", insertedEntries.single().modelName)
        assertEquals(7L, insertedEntries.single().templateId)
        coVerify(exactly = 1) { historyRepository.pruneHistory(200, 90) }
        coVerify(exactly = 0) { imageDownscaler.processIncomingImage(any()) }
        coVerify(exactly = 0) { imageDownscaler.promoteToHistory(any(), any()) }
    }

    @Test
    fun `generate should omit blank prompt sections when template is missing`() = runTest {
        val insertedEntries = mutableListOf<HistoryEntryEntity>()

        coEvery { promptTemplateRepository.getDefaultTemplate() } returns null
        every { appPreferences.baseSystemPrompt } returns flowOf("   ")
        every { appPreferences.modelName } returns flowOf("gemini-2.5-flash")
        every { appPreferences.historyRetentionCount } returns flowOf(200)
        every { appPreferences.historyRetentionDays } returns flowOf(90)
        every { geminiClient.generateContent("plain text", null, null) } returns flowOf("done")
        coEvery { historyRepository.insertHistory(any()) } answers {
            insertedEntries += firstArg<HistoryEntryEntity>()
            5L
        }
        coEvery { historyRepository.pruneHistory(any(), any()) } returns Unit

        val events = repository.generate(prompt = "plain text", imageUri = null, templateId = null).toList()

        assertTrue(events.last() is GenerationEvent.Completed)
        assertEquals(null, insertedEntries.single().templateId)
        coVerify(exactly = 1) { historyRepository.pruneHistory(200, 90) }
    }
}