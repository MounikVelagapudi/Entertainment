package com.example.entertainment.pos.presentation

import com.example.entertainment.pos.domain.ProductCatalog
import com.example.entertainment.pos.domain.model.PaymentResult
import com.example.entertainment.pos.fakes.FakeBarcodeScanner
import com.example.entertainment.pos.fakes.FakeNetworkMonitor
import com.example.entertainment.pos.fakes.FakePaymentProcessor
import com.example.entertainment.pos.fakes.FakePosRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope

/* PosViewModel over fakes plus the real catalog. Fakes stay reachable so tests can change them mid-flow. */
class PosViewModelBuilder {

    val catalog = ProductCatalog()
    val scanner = FakeBarcodeScanner()
    val repository = FakePosRepository()
    val payments = FakePaymentProcessor()
    val network = FakeNetworkMonitor()

    fun withNetworkDown() = apply { network.goOffline() }

    fun withNextPayment(result: PaymentResult) = apply { payments.nextResult = result }

    fun withFailingLocalSave() = apply { repository.failCreate = true }

    /* Collects uiState in the background like the screen would, so the stateIn flow stays live. */
    fun build(scope: TestScope) = PosViewModel(scanner, catalog, repository, payments, network).also { vm ->
        scope.backgroundScope.launch { vm.uiState.collect {} }
    }
}
