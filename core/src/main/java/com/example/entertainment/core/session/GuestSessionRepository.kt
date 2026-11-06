package com.example.entertainment.core.session

import com.example.entertainment.core.data.networking.apiCall
import com.example.entertainment.core.domain.APIFailure
import com.example.entertainment.core.domain.Result
import com.example.entertainment.core.domain.TRPError
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real TMDB calls that need a session, plus the demo controls that drive each path.
 *
 * Callers never touch the session id; [SessionInterceptor] and [SessionAuthenticator]
 * handle it below this layer.
 */
@Singleton
class GuestSessionRepository @Inject constructor(
    private val api: GuestSessionApi,
    private val store: SessionStore,
    private val clock: WallClock,
    private val log: SessionEventLog,
) {

    /** Rates [movieId] (0.5–10.0) for the current guest session. */
    suspend fun rateMovie(movieId: Int, value: Double): Result<TmdbStatusDto, TRPError> =
        apiCall { api.rateMovie(movieId, RatingRequest(value)) }

    /** Movies rated in the current session. TMDB's 404 for "none yet" becomes an empty list. */
    suspend fun ratedMovies(): Result<List<RatedMovieDto>, TRPError> =
        when (val result = apiCall { api.ratedMovies() }) {
            is Result.Success -> Result.Success(result.data.results)
            is Result.Failure ->
                if (result.failure is APIFailure.NotFoundFailure) Result.Success(emptyList()) else Result.Failure(result.failure)
            is Result.Error -> Result.Error(result.error)
        }

    /**
     * Demo: moves the stored expiry to [seconds] from now on the server clock, so the
     * next call is inside the 60 s window and plan A fires. TMDB's real TTL is 24 h.
     */
    fun demoExpireIn(seconds: Long) {
        val session = store.current() ?: return
        val serverNow = clock.nowMs() + session.clockSkewMs
        store.save(session.copy(expiresAtServerMs = serverNow + seconds * 1_000))
        log.record(SessionEvent.Kind.DEMO, "Expiry moved to ${seconds}s from now")
    }

    /**
     * Demo: swaps in an id TMDB doesn't know, with the expiry left as is. Plan A sees
     * plenty of time, so the next call goes out and TMDB returns a real 401, which plan B handles.
     */
    fun demoRevokeOnServer() {
        val session = store.current() ?: return
        store.save(session.copy(id = REVOKED_ID))
        log.record(SessionEvent.Kind.DEMO, "Session id replaced with one TMDB will reject")
    }

    private companion object {
        const val REVOKED_ID = "deadbeef00000000000000000000beef"
    }
}
