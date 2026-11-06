package com.example.entertainment.pos.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.entertainment.pos.domain.PosRepository
import com.example.entertainment.pos.domain.model.SyncStatus
import com.example.entertainment.pos.domain.model.Transaction
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ActivityFilter(val label: String, val statuses: Set<SyncStatus>?) {
    ALL("All", null),
    ATTENTION("Needs attention", setOf(SyncStatus.CONFLICT, SyncStatus.FAILED, SyncStatus.AWAITING_PAYMENT)),
    PENDING("Pending", setOf(SyncStatus.PENDING)),
    SYNCED("Synced", setOf(SyncStatus.SYNCED)),
    RESOLVED("Resolved", setOf(SyncStatus.VOIDED, SyncStatus.SHIP_TO_HOME)),
}

data class ActivityUiState(
    val filter: ActivityFilter = ActivityFilter.ALL,
    val transactions: List<Transaction> = emptyList(),
    val message: String? = null,
)

@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val repository: PosRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(ActivityFilter.ALL)
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ActivityUiState> =
        combine(filter, repository.observeAll(), message) { filter, all, message ->
            ActivityUiState(
                filter = filter,
                transactions = filter.statuses?.let { s -> all.filter { it.status in s } } ?: all,
                message = message,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivityUiState())

    fun onFilterSelected(selected: ActivityFilter) {
        filter.value = selected
    }

    fun onSyncNow() {
        repository.syncPending()
        message.value = "Sync requested -- runs once online"
    }

    fun onPruneSynced() {
        viewModelScope.launch { message.value = "Removed ${repository.pruneSynced()} synced sales" }
    }
}
