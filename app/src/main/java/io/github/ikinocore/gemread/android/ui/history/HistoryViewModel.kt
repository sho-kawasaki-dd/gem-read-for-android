package io.github.ikinocore.gemread.android.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ikinocore.gemread.android.data.db.history.HistoryEntryEntity
import io.github.ikinocore.gemread.android.domain.repository.HistoryRepository
import io.github.ikinocore.gemread.android.ui.base.UiEffect
import io.github.ikinocore.gemread.android.ui.base.UiEvent
import io.github.ikinocore.gemread.android.ui.base.UiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: HistoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<HistoryUiEffect>()
    val uiEffect: SharedFlow<HistoryUiEffect> = _uiEffect.asSharedFlow()

    private val searchQuery = MutableStateFlow("")
    private val pinnedOnly = MutableStateFlow(false)

    init {
        observeHistory()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeHistory() {
        combine(searchQuery, pinnedOnly) { query, pinned -> query to pinned }
            .flatMapLatest { (query, pinned) ->
                repository.searchHistory(query, pinned)
            }
            .onEach { entries ->
                _uiState.update { it.copy(historyGroups = groupHistory(entries)) }
            }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: HistoryUiEvent) {
        when (event) {
            is HistoryUiEvent.OnSearchQueryChange -> {
                searchQuery.value = event.query
                _uiState.update { it.copy(searchQuery = event.query) }
            }
            is HistoryUiEvent.OnTogglePinnedOnly -> {
                pinnedOnly.update { !it }
                _uiState.update { it.copy(isPinnedOnly = pinnedOnly.value) }
            }
            is HistoryUiEvent.OnTogglePin -> {
                viewModelScope.launch {
                    repository.updateHistory(event.entry.copy(pinned = !event.entry.pinned))
                }
            }
            is HistoryUiEvent.OnDeleteHistory -> {
                viewModelScope.launch {
                    repository.deleteHistory(event.entry.id)
                }
            }
            is HistoryUiEvent.OnReRunHistory -> {
                viewModelScope.launch {
                    _uiEffect.emit(HistoryUiEffect.NavigateToResult(event.entry))
                }
            }
        }
    }

    private fun groupHistory(entries: List<HistoryEntryEntity>): List<HistoryGroup> {
        val formatter = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        return entries.groupBy {
            formatter.format(Date(it.createdAt))
        }.map { (date, items) ->
            HistoryGroup(date = date, entries = items)
        }
    }
}

data class HistoryUiState(
    val historyGroups: List<HistoryGroup> = emptyList(),
    val searchQuery: String = "",
    val isPinnedOnly: Boolean = false,
) : UiState

data class HistoryGroup(
    val date: String,
    val entries: List<HistoryEntryEntity>,
)

sealed interface HistoryUiEvent : UiEvent {
    data class OnSearchQueryChange(val query: String) : HistoryUiEvent
    data object OnTogglePinnedOnly : HistoryUiEvent
    data class OnTogglePin(val entry: HistoryEntryEntity) : HistoryUiEvent
    data class OnDeleteHistory(val entry: HistoryEntryEntity) : HistoryUiEvent
    data class OnReRunHistory(val entry: HistoryEntryEntity) : HistoryUiEvent
}

sealed interface HistoryUiEffect : UiEffect {
    data class NavigateToResult(val entry: HistoryEntryEntity) : HistoryUiEffect
}
