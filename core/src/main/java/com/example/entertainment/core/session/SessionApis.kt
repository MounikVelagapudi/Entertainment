package com.example.entertainment.core.session

import com.example.core.BuildConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The "refresh" endpoint. Served by a **bare** client (no [SessionInterceptor], no
 * [SessionAuthenticator], its own Dispatcher), so a refresh can never recurse into
 * itself or wait on the threads that are waiting for it.
 *
 * TMDB has no refresh-token grant, so refreshing means minting a new guest session.
 */
interface GuestSessionAuthApi {

    /** `GET /3/authentication/guest_session/new` → `{guest_session_id, expires_at}` (24 h). */
    @GET("3/authentication/guest_session/new")
    suspend fun createGuestSession(
        @Query("api_key") apiKey: String = BuildConfig.TMDB_API_KEY,
    ): Response<GuestSessionDto>
}

/**
 * Real TMDB calls that need a valid guest session. TMDB answers both with a real
 * `401 {status_code: 3}` when the session is invalid or expired.
 *
 * Callers never pass the session. They leave [SESSION_PLACEHOLDER] in place and
 * [SessionInterceptor] binds the live id before the request leaves.
 */
interface GuestSessionApi {

    /** `POST /3/movie/{id}/rating?guest_session_id=…` — a write. */
    @POST("3/movie/{movieId}/rating")
    suspend fun rateMovie(
        @Path("movieId") movieId: Int,
        @Body body: RatingRequest,
        @Query(GUEST_SESSION_QUERY) sessionId: String = SESSION_PLACEHOLDER,
        @Query("api_key") apiKey: String = BuildConfig.TMDB_API_KEY,
    ): Response<TmdbStatusDto>

    /** `GET /3/guest_session/{id}/rated/movies` — a read. 404 until something is rated. */
    @GET("3/guest_session/{sessionId}/rated/movies")
    suspend fun ratedMovies(
        @Path("sessionId") sessionId: String = SESSION_PLACEHOLDER,
        @Query("api_key") apiKey: String = BuildConfig.TMDB_API_KEY,
    ): Response<RatedMoviesDto>
}

/** Response of `guest_session/new`. `expires_at` looks like `2026-11-02 23:39:45 UTC`. */
@Serializable
data class GuestSessionDto(
    val success: Boolean = false,
    @SerialName("guest_session_id") val guestSessionId: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
)

/** Body of a rating write; TMDB accepts 0.5–10.0 in 0.5 steps. */
@Serializable
data class RatingRequest(val value: Double)

/** TMDB's generic `{status_code, status_message}` envelope. */
@Serializable
data class TmdbStatusDto(
    @SerialName("status_code") val statusCode: Int = 0,
    @SerialName("status_message") val statusMessage: String = "",
)

/** Page of movies this guest session has rated. */
@Serializable
data class RatedMoviesDto(val results: List<RatedMovieDto> = emptyList())

/** One rated movie. */
@Serializable
data class RatedMovieDto(
    val id: Int,
    val title: String = "",
    val rating: Double = 0.0,
)
