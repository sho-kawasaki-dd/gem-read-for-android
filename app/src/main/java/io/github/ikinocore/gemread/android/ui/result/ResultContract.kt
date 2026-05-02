package io.github.ikinocore.gemread.android.ui.result

import android.net.Uri
import io.github.ikinocore.gemread.android.data.api.GeminiError
import io.github.ikinocore.gemread.android.data.db.template.PromptTemplateEntity
import io.github.ikinocore.gemread.android.ui.base.UiEffect
import io.github.ikinocore.gemread.android.ui.base.UiEvent
import io.github.ikinocore.gemread.android.ui.base.UiState

data class ResultUiState(
    val status: Status = Status.Preparing,
    val inputText: String = "",
    val imageUri: Uri? = null,
    val selectedTemplate: PromptTemplateEntity? = null,
    val templates: List<PromptTemplateEntity> = emptyList(),
    val outputText: String = "",
    val error: GeminiError? = null,
    val isProcessDeath: Boolean = false,
    val source: String? = null,
    val isMultipleImages: Boolean = false,
    /** 生成完了後に確定する履歴レコード ID。ピン留めなどの操作に使用する */
    val historyId: Long? = null,
) : UiState {
    sealed class Status {
        data object Preparing : Status()
        data object Loading : Status()
        data object Streaming : Status()
        data object Success : Status()
        data object Error : Status()
    }
}

sealed class ResultUiEvent : UiEvent {
    data class OnTemplateSelected(val template: PromptTemplateEntity) : ResultUiEvent()
    data object OnRetry : ResultUiEvent()
    data object OnDismiss : ResultUiEvent()
    data object OnCopy : ResultUiEvent()
    data object OnPin : ResultUiEvent()
    data object OnSettings : ResultUiEvent()
}

sealed class ResultUiEffect : UiEffect {
    data class CopyToClipboard(val text: String) : ResultUiEffect()
    data object NavigateToSettings : ResultUiEffect()
    data object ShowPinnedMessage : ResultUiEffect()
}
