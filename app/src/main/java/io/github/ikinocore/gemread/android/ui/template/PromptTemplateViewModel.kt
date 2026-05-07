package io.github.ikinocore.gemread.android.ui.template

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ikinocore.gemread.android.data.db.template.PromptTemplateEntity
import io.github.ikinocore.gemread.android.domain.repository.PromptTemplateRepository
import io.github.ikinocore.gemread.android.ui.base.UiEffect
import io.github.ikinocore.gemread.android.ui.base.UiEvent
import io.github.ikinocore.gemread.android.ui.base.UiState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PromptTemplateViewModel @Inject constructor(
    private val repository: PromptTemplateRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PromptTemplateUiState())
    val uiState: StateFlow<PromptTemplateUiState> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<PromptTemplateUiEffect>()
    val uiEffect: SharedFlow<PromptTemplateUiEffect> = _uiEffect.asSharedFlow()

    init {
        repository.getAllTemplates()
            .onEach { templates ->
                _uiState.update { it.copy(templates = templates) }
            }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: PromptTemplateUiEvent) {
        when (event) {
            is PromptTemplateUiEvent.OnAddClick -> {
                _uiState.update { it.copy(editingTemplate = null, isDialogVisible = true) }
            }
            is PromptTemplateUiEvent.OnEditClick -> {
                _uiState.update { it.copy(editingTemplate = event.template, isDialogVisible = true) }
            }
            is PromptTemplateUiEvent.OnDeleteClick -> {
                viewModelScope.launch {
                    repository.deleteTemplate(event.template.id)
                }
            }
            is PromptTemplateUiEvent.OnSetDefaultClick -> {
                viewModelScope.launch {
                    repository.setDefaultTemplate(event.template.id)
                }
            }
            is PromptTemplateUiEvent.OnSaveTemplate -> {
                saveTemplate(event.title, event.systemPrompt)
            }
            is PromptTemplateUiEvent.OnDialogDismiss -> {
                _uiState.update { it.copy(isDialogVisible = false) }
            }
            is PromptTemplateUiEvent.OnMoveUp -> {
                moveTemplate(event.template, -1)
            }
            is PromptTemplateUiEvent.OnMoveDown -> {
                moveTemplate(event.template, 1)
            }
        }
    }

    private fun saveTemplate(title: String, systemPrompt: String) {
        viewModelScope.launch {
            val currentEditing = _uiState.value.editingTemplate
            if (currentEditing == null) {
                // Add new
                val maxSortOrder = _uiState.value.templates.maxOfOrNull { it.sortOrder } ?: 0
                val newTemplate = PromptTemplateEntity(
                    title = title,
                    systemPrompt = systemPrompt,
                    sortOrder = maxSortOrder + 1,
                    isDefault = _uiState.value.templates.isEmpty()
                )
                repository.insertTemplate(newTemplate)
            } else {
                // Update existing
                repository.updateTemplate(
                    currentEditing.copy(
                        title = title,
                        systemPrompt = systemPrompt
                    )
                )
            }
            _uiState.update { it.copy(isDialogVisible = false) }
        }
    }

    private fun moveTemplate(template: PromptTemplateEntity, direction: Int) {
        viewModelScope.launch {
            val templates = _uiState.value.templates
            val currentIndex = templates.indexOfFirst { it.id == template.id }
            val targetIndex = currentIndex + direction

            if (targetIndex in templates.indices) {
                val targetTemplate = templates[targetIndex]

                // Swap sortOrder
                repository.updateTemplate(template.copy(sortOrder = targetTemplate.sortOrder))
                repository.updateTemplate(targetTemplate.copy(sortOrder = template.sortOrder))
            }
        }
    }
}

data class PromptTemplateUiState(
    val templates: List<PromptTemplateEntity> = emptyList(),
    val editingTemplate: PromptTemplateEntity? = null,
    val isDialogVisible: Boolean = false,
) : UiState

sealed interface PromptTemplateUiEvent : UiEvent {
    data object OnAddClick : PromptTemplateUiEvent
    data class OnEditClick(val template: PromptTemplateEntity) : PromptTemplateUiEvent
    data class OnDeleteClick(val template: PromptTemplateEntity) : PromptTemplateUiEvent
    data class OnSetDefaultClick(val template: PromptTemplateEntity) : PromptTemplateUiEvent
    data class OnSaveTemplate(val title: String, val systemPrompt: String) : PromptTemplateUiEvent
    data object OnDialogDismiss : PromptTemplateUiEvent
    data class OnMoveUp(val template: PromptTemplateEntity) : PromptTemplateUiEvent
    data class OnMoveDown(val template: PromptTemplateEntity) : PromptTemplateUiEvent
}

sealed interface PromptTemplateUiEffect : UiEffect
