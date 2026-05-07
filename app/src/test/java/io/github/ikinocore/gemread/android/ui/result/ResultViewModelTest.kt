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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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
}
