package com.raulh.randomreminders.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulh.randomreminders.data.db.HistoryDao
import com.raulh.randomreminders.data.db.HistoryEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Estado imutável do histórico. */
data class HistoryUiState(
    val entries: List<HistoryEntry> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(historyDao: HistoryDao) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = historyDao.observeRecent(HISTORY_LIMIT)
        .map { entries -> HistoryUiState(entries = entries, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HistoryUiState(),
        )

    private companion object {
        const val HISTORY_LIMIT = 100
    }
}
