package com.example.entertainment.core.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The **one** refresh function. The proactive path ([SessionInterceptor]) and the
 * reactive path ([SessionAuthenticator]) both go through it.
 *
 * - **Mutex:** N concurrent callers make one network refresh.
 * - **Check-before-refresh:** a caller passes the id it holds as stale. If the store
 *   already holds a different id, someone else refreshed while it waited, so that id
 *   is returned and nothing is minted. Without this, the second caller would replace
 *   the first caller's fresh session.
 * - **Classify, don't swallow:** transient (offline, 5xx, 429) keeps the session;
 *   only a definitive 401/403 from the refresh endpoint is [RefreshResult.Dead].
 */
@Singleton
class SessionRefresher @Inject constructor(
    private val authApi: GuestSessionAuthApi,
    private val store: SessionStore,
    private val clock: WallClock,
    private val log: SessionEventLog,
) {
    private val mutex = Mutex()

    /**
     * Ensures the store holds a session newer than [staleId].
     *
     * @param staleId The id the caller already has and wants replaced; null if it has none.
     * @param trigger Which path is asking, for the event log.
     * @return [RefreshResult.Success] with the session to use, or a classified failure.
     */
    suspend fun refresh(staleId: String?, trigger: SessionEvent.Kind): RefreshResult =
        mutex.withLock {
            store.current()?.takeIf { it.id != staleId }?.let { fresh ->
                log.record(SessionEvent.Kind.REUSED_FRESH, "${fresh.shortId} already refreshed by another call")
                return@withLock RefreshResult.Success(fresh)
            }
            mintNewSession(staleId, trigger)
        }

    private suspend fun mintNewSession(staleId: String?, trigger: SessionEvent.Kind): RefreshResult =
        try {
            val response = authApi.createGuestSession()
            val body = response.body()
            when {
                response.isSuccessful && body?.guestSessionId != null && body.expiresAt != null -> {
                    val deviceNow = clock.nowMs()
                    val serverNow = response.headers().getDate("Date")?.time ?: deviceNow
                    val session = GuestSession(
                        id = body.guestSessionId,
                        expiresAtServerMs = parseTmdbExpiry(body.expiresAt),
                        clockSkewMs = serverNow - deviceNow,
                    )
                    store.save(session)
                    val from = staleId?.let { "${it.take(6)}… → " }.orEmpty()
                    log.record(trigger, "$from${session.shortId} (skew ${session.clockSkewMs} ms)")
                    RefreshResult.Success(session)
                }
                response.code() == 401 || response.code() == 403 -> {
                    log.record(SessionEvent.Kind.SIGNED_OUT, "Refresh endpoint said ${response.code()}")
                    RefreshResult.Dead
                }
                else -> transient("HTTP ${response.code()}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            transient(e.javaClass.simpleName)
        } catch (e: SerializationException) {
            transient("Malformed body")
        } catch (e: ParseException) {
            transient("Unparseable expires_at")
        }

    private fun transient(reason: String): RefreshResult {
        log.record(SessionEvent.Kind.TRANSIENT_FAILURE, "Refresh failed ($reason), keeping current session")
        return RefreshResult.Transient
    }

    private fun parseTmdbExpiry(raw: String): Long =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .parse(raw)
            ?.time
            ?: throw ParseException(raw, 0)
}

/** Outcome of [SessionRefresher.refresh]. */
sealed interface RefreshResult {
    /** A usable session (new, or one another caller just minted). */
    data class Success(val session: GuestSession) : RefreshResult

    /** Offline / timeout / 5xx / 429. Retry later and keep the current session. */
    data object Transient : RefreshResult

    /** Refresh endpoint said 401/403. Authoritative, so sign out. */
    data object Dead : RefreshResult
}
