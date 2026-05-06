package io.github.ikinocore.gemread.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ikinocore.gemread.android.data.api.GeminiClient
import io.github.ikinocore.gemread.android.data.prefs.AppPreferences
import io.github.ikinocore.gemread.android.data.prefs.SecurePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val securePreferences: SecurePreferences,
    private val geminiClient: GeminiClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                securePreferences.getApiKey(),
                appPreferences.modelName,
                appPreferences.baseSystemPrompt,
                appPreferences.isImageResizeEnabled,
                appPreferences.isStreamingEnabled,
                appPreferences.historyRetentionCount,
                appPreferences.historyRetentionDays
            ) { apiKey, modelName, systemPrompt, resize, streaming, count, days ->
                SettingsUiState(
                    apiKey = apiKey ?: "",
                    modelName = modelName,
                    baseSystemPrompt = systemPrompt,
                    isImageResizeEnabled = resize,
                    isStreamingEnabled = streaming,
                    historyRetentionCount = count,
                    historyRetentionDays = days
                )
            }.collect { _uiState.value = it }
        }
    }

    fun updateApiKey(apiKey: String) {
        viewModelScope.launch { securePreferences.saveApiKey(apiKey) }
    }

    fun updateModelName(modelName: String) {
        viewModelScope.launch { appPreferences.setModelName(modelName) }
    }

    fun updateBaseSystemPrompt(prompt: String) {
        viewModelScope.launch { appPreferences.setBaseSystemPrompt(prompt) }
    }

    fun updateImageResizeEnabled(enabled: Boolean) {
        viewModelScope.launch { appPreferences.setImageResizeEnabled(enabled) }
    }

    fun updateStreamingEnabled(enabled: Boolean) {
        viewModelScope.launch { appPreferences.setStreamingEnabled(enabled) }
    }

    fun updateHistoryRetentionCount(count: Int) {
        viewModelScope.launch { appPreferences.setHistoryRetentionCount(count) }
    }

    fun updateHistoryRetentionDays(days: Int) {
        viewModelScope.launch { appPreferences.setHistoryRetentionDays(days) }
    }

    fun testConnection() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTestingConnection = true, connectionError = null)
            geminiClient.testConnection()
                .onSuccess { _uiState.value = _uiState.value.copy(isTestingConnection = false) }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isTestingConnection = false,
                        connectionError = error.message
                    )
                }
        }
    }
}

data class SettingsUiState(
    val apiKey: String = "",
    val modelName: String = "gemini-2.5-flash",
    val baseSystemPrompt: String = "",
    val isImageResizeEnabled: Boolean = true,
    val isStreamingEnabled: Boolean = true,
    val historyRetentionCount: Int = 200,
    val historyRetentionDays: Int = 90,
    val isTestingConnection: Boolean = false,
    val connectionError: String? = null
)
