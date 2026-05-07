package io.github.ikinocore.gemread.android.ui.result

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.ikinocore.gemread.android.data.db.template.PromptTemplateEntity
import io.github.ikinocore.gemread.android.domain.repository.GenerationRepository
import io.github.ikinocore.gemread.android.domain.repository.HistoryRepository
import io.github.ikinocore.gemread.android.domain.repository.PromptTemplateRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResultViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val generationRepository: GenerationRepository = mockk()
    private val promptTemplateRepository: PromptTemplateRepository = mockk()
    private val historyRepository: HistoryRepository = mockk()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Default mocks
        every { promptTemplateRepository.getAllTemplates() } returns flowOf(emptyList())
        coEvery { promptTemplateRepository.getDefaultTemplate() } returns null
        coEvery { generationRepository.generate(any(), any(), any()) } returns emptyFlow()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadInitialData should restore outputText from SavedStateHandle (process death)`() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf(
            ResultViewModel.KEY_INPUT_TEXT to "input",
            "output_text" to "saved output"
        ))

        val template = PromptTemplateEntity(id = 1, title = "T", systemPrompt = "S", sortOrder = 0, isDefault = true)
        every { promptTemplateRepository.getAllTemplates() } returns flowOf(listOf(template))
        coEvery { promptTemplateRepository.getTemplateById(any()) } returns template
        coEvery { promptTemplateRepository.getDefaultTemplate() } returns template

        val viewModel = ResultViewModel(
            savedStateHandle,
            generationRepository,
            promptTemplateRepository,
            historyRepository
        )

        viewModel.uiState.test {
            // Initial state (loading data)
            assertEquals("", awaitItem().outputText)

            // It might skip the intermediate state if updates are fast,
            // but since there's a suspend call in between loadInitialData,
            // we expect at least one state update.

            // In ResultViewModel, first update sets inputText, second update sets outputText.
            // We want the one where outputText is "saved output".

            var lastState = ResultUiState()
            // Collect until we get the expected state or timeout
            while (true) {
                lastState = awaitItem()
                if (lastState.isProcessDeath) break
            }

            assertEquals("saved output", lastState.outputText)
            assertEquals(ResultUiState.Status.Error, lastState.status)
            assertEquals(true, lastState.isProcessDeath)
        }
    }

    @Test
    fun `startGeneration should append chunks and expose history id on success`() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf(ResultViewModel.KEY_INPUT_TEXT to "input"))
        val template = PromptTemplateEntity(id = 1, title = "T", systemPrompt = "S", sortOrder = 0, isDefault = true)

        every { promptTemplateRepository.getAllTemplates() } returns flowOf(listOf(template))
        coEvery { promptTemplateRepository.getDefaultTemplate() } returns template
        every {
            generationRepository.generate(
                prompt = "input",
                imageUri = null,
                templateId = 1L,
            )
        } returns flow {
            emit(io.github.ikinocore.gemread.android.domain.model.GenerationEvent.Chunk("he"))
            emit(io.github.ikinocore.gemread.android.domain.model.GenerationEvent.Chunk("llo"))
            emit(io.github.ikinocore.gemread.android.domain.model.GenerationEvent.Completed(99L))
        }

        val viewModel = ResultViewModel(
            savedStateHandle,
            generationRepository,
            promptTemplateRepository,
            historyRepository,
        )

        viewModel.uiState.test {
            var sawStreaming = false
            var successState: ResultUiState? = null

            while (successState == null) {
                val state = awaitItem()
                if (state.status == ResultUiState.Status.Streaming) {
                    sawStreaming = true
                }
                if (state.status == ResultUiState.Status.Success) {
                    successState = state
                }
            }

            assertTrue(sawStreaming)
            assertEquals("hello", successState?.outputText)
            assertEquals(99L, successState?.historyId)
            assertNull(savedStateHandle.get<String>("output_text"))
        }
    }

    @Test
    fun `onRetry should re-run generation with current input and template`() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf(ResultViewModel.KEY_INPUT_TEXT to "input"))
        val template = PromptTemplateEntity(id = 1, title = "T", systemPrompt = "S", sortOrder = 0, isDefault = true)

        every { promptTemplateRepository.getAllTemplates() } returns flowOf(listOf(template))
        coEvery { promptTemplateRepository.getDefaultTemplate() } returns template
        every {
            generationRepository.generate(
                prompt = "input",
                imageUri = null,
                templateId = 1L,
            )
        } returnsMany listOf(
            flowOf(io.github.ikinocore.gemread.android.domain.model.GenerationEvent.Completed(1L)),
            flowOf(io.github.ikinocore.gemread.android.domain.model.GenerationEvent.Completed(2L)),
        )

        val viewModel = ResultViewModel(
            savedStateHandle,
            generationRepository,
            promptTemplateRepository,
            historyRepository,
        )
        advanceUntilIdle()

        viewModel.onEvent(ResultUiEvent.OnRetry)
        advanceUntilIdle()

        assertEquals(ResultUiState.Status.Success, viewModel.uiState.value.status)
        assertEquals(2L, viewModel.uiState.value.historyId)
        verify(exactly = 2) {
            generationRepository.generate(
                prompt = "input",
                imageUri = null,
                templateId = 1L,
            )
        }
    }
}
