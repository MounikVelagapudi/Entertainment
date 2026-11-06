package com.example.entertainment.core.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory log of what the session machinery did, newest first.
 *
 * Refreshes are silent to the user by design. This log exists so the demo screen
 * (and you, in an interview) can see which path handled each call.
 */
@Singleton
class SessionEventLog @Inject constructor(private val clock: WallClock) {

    private val _events = MutableStateFlow<List<SessionEvent>>(emptyList())

    /** Newest-first event list. */
    val events: StateFlow<List<SessionEvent>> = _events.asStateFlow()

    /** Thread-safe append; called from OkHttp threads. */
    fun record(kind: SessionEvent.Kind, detail: String) {
        _events.update { (listOf(SessionEvent(clock.nowMs(), kind, detail)) + it).take(MAX_EVENTS) }
    }

    /** Clears the log. */
    fun clear() = _events.update { emptyList() }

    private companion object {
        const val MAX_EVENTS = 30
    }
}

/**
 * One entry in [SessionEventLog].
 *
 * @property atMs Device time it happened.
 * @property kind What happened.
 * @property detail Human-readable specifics (ids are shortened).
 */
data class SessionEvent(val atMs: Long, val kind: Kind, val detail: String) {

    /** Event categories, one per branch of the design. */
    enum class Kind {
        CREATED,
        PROACTIVE_REFRESH,
        REACTIVE_REFRESH,
        REUSED_FRESH,
        TRANSIENT_FAILURE,
        SIGNED_OUT,
        DEMO,
    }
}
