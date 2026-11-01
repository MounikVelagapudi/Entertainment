package com.example.entertainment.core.data.local

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide flag for "Room is no longer trustworthy" (corrupt file, full disk,
 * encryption lock error, ...). Once a repository reports a structural failure,
 * every offline-first repository sharing this monitor should stop touching Room
 * and fall back to an in-memory buffer for the rest of the process lifetime --
 * there's no recovery path for a corrupt/full disk mid-session.
 */
@Singleton
class StorageHealthMonitor @Inject constructor() {

    private val _compromised = MutableStateFlow(false)
    val compromised: StateFlow<Boolean> = _compromised.asStateFlow()
    val isCompromised: Boolean get() = _compromised.value

    /* MutableStateFlow.value= is a CAS-backed atomic write, so no extra locking is needed here. */
    fun reportFailure(error: Throwable) {
        Log.e(TAG, "Room storage compromised, switching to RAM fallback", error)
        _compromised.value = true
    }

    private companion object {
        const val TAG = "StorageHealthMonitor"
    }
}
