package com.example.entertainment.core.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/* Fixed, hand-moved clock so expiry math is deterministic. */
class FakeClock(@Volatile var now: Long = 1_000_000_000_000L) : WallClock {
    override fun nowMs() = now
}

class InMemorySessionStore : SessionStore {
    private val state = MutableStateFlow<GuestSession?>(null)
    override val session: StateFlow<GuestSession?> = state
    override fun save(session: GuestSession) { state.value = session }
    override fun clear() { state.value = null }
}

fun aGuestSession(
    id: String = "seed",
    expiresAtServerMs: Long,
    clockSkewMs: Long = 0L,
) = GuestSession(id = id, expiresAtServerMs = expiresAtServerMs, clockSkewMs = clockSkewMs)
