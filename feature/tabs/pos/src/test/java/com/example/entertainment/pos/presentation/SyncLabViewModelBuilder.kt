package com.example.entertainment.pos.presentation

import com.example.entertainment.pos.data.scanner.ScanSimulatorSettings
import com.example.entertainment.pos.data.sync.PosSyncScheduler
import com.example.entertainment.pos.domain.ProductCatalog
import com.example.entertainment.pos.fakes.FakeNetworkMonitor
import com.example.entertainment.pos.testdata.aMockBackend
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle

/* Real mock backend, scan settings and catalog; WorkManager scheduler mocked with a controllable status. */
class SyncLabViewModelBuilder {

    val network = FakeNetworkMonitor()
    val workStatus = MutableStateFlow("No sync scheduled yet")
    val scheduler = mockk<PosSyncScheduler>(relaxed = true) {
        every { observeStatus() } returns workStatus
    }

    /* Same as the app's injected MockBackend defaults. */
    val backend = aMockBackend(latencyMs = 800, failureRatePercent = 20)
    val scanSettings = ScanSimulatorSettings()
    val catalog = ProductCatalog()

    /* Collects uiState in the background and settles the first emission. */
    fun build(scope: TestScope) = SyncLabViewModel(network, scheduler, backend, scanSettings, catalog).also { vm ->
        scope.backgroundScope.launch { vm.uiState.collect {} }
        scope.advanceUntilIdle()
    }
}
