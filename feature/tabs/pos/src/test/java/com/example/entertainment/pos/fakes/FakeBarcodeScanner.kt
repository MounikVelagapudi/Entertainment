package com.example.entertainment.pos.fakes

import com.example.entertainment.pos.domain.BarcodeScanner
import kotlinx.coroutines.CompletableDeferred

class FakeBarcodeScanner : BarcodeScanner {
    var next = ""
    var scans = 0

    /* Set to hold a scan mid-flight. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun scan(): String {
        scans++
        gate?.await()
        return next
    }
}
