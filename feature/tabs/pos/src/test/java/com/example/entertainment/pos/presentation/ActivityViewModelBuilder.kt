package com.example.entertainment.pos.presentation

import com.example.entertainment.pos.domain.model.Transaction
import com.example.entertainment.pos.fakes.FakePosRepository
import com.example.entertainment.pos.fakes.givenTransactions
import com.example.entertainment.pos.testdata.oneTransactionPerStatus
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope

class ActivityViewModelBuilder {

    val repository = FakePosRepository()

    fun withTransactions(vararg transactions: Transaction) = apply { repository.givenTransactions(*transactions) }

    fun withOneSalePerStatus() = apply { repository.givenTransactions(oneTransactionPerStatus()) }

    /* No background collector: for tests that read the very first emission themselves. */
    fun buildCold() = ActivityViewModel(repository)

    /* Collects uiState in the background like the screen would, so the stateIn flow stays live. */
    fun build(scope: TestScope) = buildCold().also { vm ->
        scope.backgroundScope.launch { vm.uiState.collect {} }
    }
}
