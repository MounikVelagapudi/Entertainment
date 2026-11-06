package com.example.entertainment.core.session

/**
 * A TMDB guest session — the "logged-in token" for the session demo.
 *
 * Expiry is kept on the **server's** clock, not the device's: [clockSkewMs] is
 * `serverDate − deviceNow`, captured from the `Date` header when the session was
 * minted. A device clock that is minutes off (or that the user changed) therefore
 * cannot make us refresh too late or too early.
 *
 * @property id The `guest_session_id` TMDB attaches ratings to.
 * @property expiresAtServerMs TMDB's `expires_at`, epoch millis on the server clock.
 * @property clockSkewMs Offset to add to the device clock to get server time.
 */
data class GuestSession(
    val id: String,
    val expiresAtServerMs: Long,
    val clockSkewMs: Long,
) {
    /** Milliseconds left before the server considers this session expired. */
    fun remainingMs(deviceNowMs: Long): Long = expiresAtServerMs - (deviceNowMs + clockSkewMs)

    /** True once the server-clock expiry has passed. */
    fun isExpired(deviceNowMs: Long): Boolean = remainingMs(deviceNowMs) <= 0

    /** Short, log-safe form of the id. */
    val shortId: String get() = id.take(6) + "…"

    /** Never print the full credential. */
    override fun toString(): String = "GuestSession($shortId, expiresAt=$expiresAtServerMs)"
}

/** Device clock, injectable so expiry logic is testable. */
fun interface WallClock {
    /** Current device time in epoch millis. */
    fun nowMs(): Long
}
