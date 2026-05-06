package io.github.ikinocore.gemread.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ikinocore.gemread.android.data.api.GeminiClient
import io.github.ikinocore.gemread.android.data.api.GeminiError
import io.github.ikinocore.gemread.android.data.prefs.AppPreferences
import io.github.ikinocore.gemread.android.data.prefs.SecurePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    private val apiKey = MutableStateFlow(securePreferences.getApiKey().orEmpty())
    private val connectionStatus = MutableStateFlow(SettingsConnectionStatus.Idle)

    val uiState: StateFlow<SettingsUiState> = combine(
        apiKey,
        appPreferences.modelName,
        appPreferences.baseSystemPrompt,
        appPreferences.isImageResizeEnabled,
        appPreferences.isStreamingEnabled,
        appPreferences.historyRetentionCount,
        appPreferences.historyRetentionDays,
        connectionStatus,
    ) { args ->
        SettingsUiState(
            apiKey = args[0] as String,
            modelName = args[1] as String,
            baseSystemPrompt = args[2] as String,
            isImageResizeEnabled = args[3] as Boolean,
            isStreamingEnabled = args[4] as Boolean,
            historyRetentionCount = args[5] as Int,
            historyRetentionDays = args[6] as Int,
            connectionStatus = args[7] as SettingsConnectionStatus,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun updateApiKey(apiKey: String) {
        val normalizedApiKey = apiKey.trim()
        if (normalizedApiKey.isEmpty()) {
            securePreferences.clearApiKey()
        } else {
            securePreferences.setApiKey(normalizedApiKey)
        }
        this.apiKey.value = normalizedApiKey
        connectionStatus.value = SettingsConnectionStatus.Idle
    }

    fun updateModelName(modelName: String) {
        viewModelScope.launch {
            appPreferences.setModelName(modelName)
            connectionStatus.value = SettingsConnectionStatus.Idle
        }
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
        if (count <= 0) return
        viewModelScope.launch { appPreferences.setHistoryRetentionCount(count) }
    }

    fun updateHistoryRetentionDays(days: Int) {
        if (days <= 0) return
        viewModelScope.launch { appPreferences.setHistoryRetentionDays(days) }
    }

    fun testConnection() {
        viewModelScope.launch {
            if (apiKey.value.isBlank()) {
                connectionStatus.value = SettingsConnectionStatus.AuthError
                return@launch
            }

            connectionStatus.value = SettingsConnectionStatus.Testing
            geminiClient.testConnection()
                .onSuccess {
                    connectionStatus.value = SettingsConnectionStatus.Success
                }
                .onFailure { error ->
                    connectionStatus.value = error.toConnectionStatus()
                }
        }
    }

    private fun Throwable.toConnectionStatus(): SettingsConnectionStatus {
        return when (this) {
            GeminiError.Auth -> SettingsConnectionStatus.AuthError
            GeminiError.Network -> SettingsConnectionStatus.NetworkError
            GeminiError.RateLimited -> SettingsConnectionStatus.RateLimitedError
            else -> SettingsConnectionStatus.UnknownError
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
    val connectionStatus: SettingsConnectionStatus = SettingsConnectionStatus.Idle,
)

enum class SettingsConnectionStatus {
    Idle,
    Testing,
    Success,
    AuthError,
    NetworkError,
    RateLimitedError,
    UnknownError,
}
