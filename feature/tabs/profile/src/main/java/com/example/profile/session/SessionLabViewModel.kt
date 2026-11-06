package com.example.profile.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.entertainment.core.domain.Result
import com.example.entertainment.core.session.GuestSessionRepository
import com.example.entertainment.core.session.SessionEvent
import com.example.entertainment.core.session.SessionEventLog
import com.example.entertainment.core.session.SessionInterceptor
import com.example.entertainment.core.session.SessionStore
import com.example.entertainment.core.session.WallClock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Drives the Session Lab: real TMDB calls on a guest session, plus buttons that put
 * the session into the state each refresh path handles.
 */
@HiltViewModel
class SessionLabViewModel @Inject constructor(
    private val repository: GuestSessionRepository,
    private val store: SessionStore,
    private val log: SessionEventLog,
    private val clock: WallClock,
) : ViewModel() {

    private val lastResult = MutableStateFlow("No call made yet")
    private val busy = MutableStateFlow(false)
    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(1_000)
        }
    }

    /** Screen state, recomputed every second for the countdown. */
    val state: StateFlow<SessionLabUiState> =
        combine(store.session, log.events, lastResult, busy, ticker) { session, events, result, isBusy, _ ->
            val remaining = session?.remainingMs(clock.nowMs())
            SessionLabUiState(
                sessionLabel = session?.shortId ?: "None (signed out)",
                remainingLabel = remaining?.let(::formatRemaining) ?: "–",
                insideRefreshWindow = remaining != null && remaining <= SessionInterceptor.PROACTIVE_THRESHOLD_MS,
                lastResult = result,
                busy = isBusy,
                events = events.map { it.toRow() },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionLabUiState())

    /** Real write: rates Fight Club (id 550) 8.5. */
    fun rateMovie() = call("POST rating") {
        when (val r = repository.rateMovie(FIGHT_CLUB_ID, 8.5)) {
            is Result.Success -> "✅ ${r.data.statusMessage}"
            is Result.Error -> "❌ ${r.error}"
            is Result.Failure -> "❌ ${r.failure}"
        }
    }

    /** Real read: movies rated in this session. */
    fun loadRated() = call("GET rated") {
        when (val r = repository.ratedMovies()) {
            is Result.Success -> "✅ ${r.data.size} rated: " + r.data.joinToString { "${it.title} (${it.rating})" }.ifEmpty { "none yet" }
            is Result.Error -> "❌ ${r.error}"
            is Result.Failure -> "❌ ${r.failure}"
        }
    }

    /** Puts the session inside the 60 s window → next call takes plan A. */
    fun expireSoon() = repository.demoExpireIn(45)

    /** Makes TMDB reject the session → next call gets a real 401 → plan B. */
    fun revokeOnServer() = repository.demoRevokeOnServer()

    /** Clears session and log. */
    fun reset() {
        store.clear()
        log.clear()
        lastResult.value = "No call made yet"
    }

    private fun call(label: String, block: suspend () -> String) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            lastResult.value = "$label: ${block()}"
            busy.value = false
        }
    }

    private fun formatRemaining(ms: Long): String {
        if (ms <= 0) return "expired"
        val s = ms / 1_000
        return if (s >= 3_600) "${s / 3_600}h ${(s % 3_600) / 60}m" else "${s / 60}m ${s % 60}s"
    }

    private fun SessionEvent.toRow(): String {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(atMs))
        val label = when (kind) {
            SessionEvent.Kind.CREATED -> "🆕 Created"
            SessionEvent.Kind.PROACTIVE_REFRESH -> "🅰️ Proactive refresh"
            SessionEvent.Kind.REACTIVE_REFRESH -> "🅱️ Reactive refresh (401)"
            SessionEvent.Kind.REUSED_FRESH -> "♻️ Reused"
            SessionEvent.Kind.TRANSIENT_FAILURE -> "⚠️ Transient"
            SessionEvent.Kind.SIGNED_OUT -> "🚪 Signed out"
            SessionEvent.Kind.DEMO -> "🧪 Demo"
        }
        return "$time  $label: $detail"
    }

    private companion object {
        const val FIGHT_CLUB_ID = 550
    }
}

/**
 * UI state for the Session Lab.
 *
 * @property sessionLabel Shortened session id, or a signed-out label.
 * @property remainingLabel Time to expiry on the server clock.
 * @property insideRefreshWindow True when the next call will refresh proactively.
 * @property lastResult Outcome of the last real API call.
 * @property busy A call is in flight.
 * @property events Newest-first session event log.
 */
data class SessionLabUiState(
    val sessionLabel: String = "–",
    val remainingLabel: String = "–",
    val insideRefreshWindow: Boolean = false,
    val lastResult: String = "",
    val busy: Boolean = false,
    val events: List<String> = emptyList(),
)
