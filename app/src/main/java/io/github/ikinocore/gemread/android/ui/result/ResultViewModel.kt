package io.github.ikinocore.gemread.android.ui.result

import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ikinocore.gemread.android.data.api.GeminiError
import io.github.ikinocore.gemread.android.domain.model.GenerationEvent
import io.github.ikinocore.gemread.android.domain.repository.GenerationRepository
import io.github.ikinocore.gemread.android.domain.repository.HistoryRepository
import io.github.ikinocore.gemread.android.domain.repository.PromptTemplateRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ResultViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val generationRepository: GenerationRepository,
    private val promptTemplateRepository: PromptTemplateRepository,
    private val historyRepository: HistoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResultUiState())
    val uiState: StateFlow<ResultUiState> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<ResultUiEffect>()
    val uiEffect: SharedFlow<ResultUiEffect> = _uiEffect.asSharedFlow()

    private var generationJob: Job? = null

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        val uriString: String? = savedStateHandle[KEY_IMAGE_URI]
        val inputText: String? = savedStateHandle[KEY_INPUT_TEXT]
        val inputTextPath: String? = savedStateHandle[KEY_INPUT_TEXT_PATH]
        val templateId: Long? = savedStateHandle[KEY_TEMPLATE_ID]
        val source: String? = savedStateHandle[KEY_SOURCE]
        val isMultiple: Boolean = savedStateHandle[KEY_IS_MULTIPLE] ?: false

        viewModelScope.launch {
            val templates = promptTemplateRepository.getAllTemplates().first { it.isNotEmpty() }
            val selectedTemplate = if (templateId != null) {
                promptTemplateRepository.getTemplateById(templateId)
            } else {
                promptTemplateRepository.getDefaultTemplate()
            }

            val finalInputText = when {
                inputText != null -> inputText
                inputTextPath != null -> {
                    runCatching { File(inputTextPath).readText() }.getOrDefault("")
                }
                else -> ""
            }

            _uiState.update {
                it.copy(
                    inputText = finalInputText,
                    imageUri = uriString?.toUri(),
                    templates = templates,
                    selectedTemplate = selectedTemplate,
                    source = source,
                    isMultipleImages = isMultiple,
                )
            }

            // Check for process death: if we have outputText in savedState, it means we were killed.
            val savedOutput: String? = savedStateHandle[KEY_OUTPUT_TEXT]
            if (savedOutput != null) {
                _uiState.update {
                    it.copy(
                        status = ResultUiState.Status.Error,
                        outputText = savedOutput,
                        isProcessDeath = true,
                    )
                }
            } else {
                startGeneration()
            }
        }
    }

    fun onEvent(event: ResultUiEvent) {
        when (event) {
            is ResultUiEvent.OnTemplateSelected -> {
                _uiState.update { it.copy(selectedTemplate = event.template) }
                startGeneration()
            }

            ResultUiEvent.OnRetry -> startGeneration()
            ResultUiEvent.OnDismiss -> {
                generationJob?.cancel()
            }

            ResultUiEvent.OnCopy -> {
                viewModelScope.launch {
                    _uiEffect.emit(ResultUiEffect.CopyToClipboard(_uiState.value.outputText))
                }
            }

            ResultUiEvent.OnPin -> {
                // 履歴 ID が確定している場合のみピン留めをトグルする
                val historyId = _uiState.value.historyId ?: return
                viewModelScope.launch {
                    val entry = historyRepository.getHistoryById(historyId) ?: return@launch
                    historyRepository.updateHistory(entry.copy(pinned = !entry.pinned))
                    _uiEffect.emit(ResultUiEffect.ShowPinnedMessage)
                }
            }

            ResultUiEvent.OnSettings -> {
                viewModelScope.launch {
                    _uiEffect.emit(ResultUiEffect.NavigateToSettings)
                }
            }
        }
    }

    private fun startGeneration() {
        generationJob?.cancel()
        val currentState = _uiState.value

        generationJob = viewModelScope.launch {
            generationRepository.generate(
                prompt = currentState.inputText,
                imageUri = currentState.imageUri,
                templateId = currentState.selectedTemplate?.id,
            ).onStart {
                _uiState.update {
                    it.copy(
                        status = ResultUiState.Status.Loading,
                        outputText = "",
                        error = null,
                        isProcessDeath = false,
                        historyId = null,
                    )
                }
            }.catch { e ->
                val geminiError = (e as? GeminiError) ?: GeminiError.Unknown(e)
                _uiState.update { it.copy(status = ResultUiState.Status.Error, error = geminiError) }
            }.collect { event ->
                when (event) {
                    is GenerationEvent.Chunk -> _uiState.update {
                        val newOutput = it.outputText + event.text
                        savedStateHandle[KEY_OUTPUT_TEXT] = newOutput
                        it.copy(status = ResultUiState.Status.Streaming, outputText = newOutput)
                    }
                    is GenerationEvent.Completed -> _uiState.update {
                        // 完了時は savedState の部分出力をクリアし、process death 誤検知を防ぐ
                        savedStateHandle.remove<String>(KEY_OUTPUT_TEXT)
                        it.copy(
                            status = ResultUiState.Status.Success,
                            historyId = event.historyId,
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val KEY_IMAGE_URI = "image_uri"
        const val KEY_INPUT_TEXT = "input_text"
        const val KEY_INPUT_TEXT_PATH = "input_text_path"
        const val KEY_TEMPLATE_ID = "template_id"
        const val KEY_SOURCE = "source"
        const val KEY_IS_MULTIPLE = "is_multiple"
        private const val KEY_OUTPUT_TEXT = "output_text"
    }
}
