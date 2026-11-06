package com.example.entertainment.pos.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.entertainment.pos.domain.ConflictResolver
import com.example.entertainment.pos.domain.PosRepository
import com.example.entertainment.pos.domain.model.RefundResult
import com.example.entertainment.pos.domain.model.Transaction
import com.example.entertainment.pos.domain.model.TransactionEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: PosRepository,
    private val resolver: ConflictResolver,
) : ViewModel() {

    private val id: String = checkNotNull(savedStateHandle[ARG_ID])

    /* Straight off Room, so the screen flips PENDING -> SYNCED live when the worker lands. */
    val transaction: StateFlow<Transaction?> = repository.observeTransaction(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val events: StateFlow<List<TransactionEvent>> = repository.observeEvents(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isResolving = MutableStateFlow(false)
    val isResolving: StateFlow<Boolean> = _isResolving.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun onVoidAndRefund() = resolve { tx ->
        when (val result = resolver.voidAndRefund(tx)) {
            is RefundResult.Refunded -> _message.value = "Refunded (${result.reference})"
            is RefundResult.Error -> _message.value = result.message
        }
    }

    fun onManagerOverride() = resolve { resolver.managerOverride(it) }

    fun onShipToHome() = resolve { resolver.shipToHome(it) }

    private fun resolve(action: suspend (Transaction) -> Unit) {
        val tx = transaction.value ?: return
        if (_isResolving.value) return
        _isResolving.value = true
        _message.value = null
        viewModelScope.launch {
            try {
                action(tx)
            } finally {
                _isResolving.value = false
            }
        }
    }

    companion object {
        const val ARG_ID = "transactionId"
    }
}
