package com.example.entertainment.core.session

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * **Plan A — proactive.** Before each request, compares the session's server-clock
 * expiry with now. Under [PROACTIVE_THRESHOLD_MS] left, it refreshes silently, then
 * attaches the session. The user never sees a 401 for a session we knew was ending.
 *
 * Proactive refresh is only an optimization. If it fails transiently, the request
 * goes out with the current session, and [SessionAuthenticator] (plan B) handles any 401.
 *
 * `runBlocking` is safe here. OkHttp runs interceptors on its own worker threads,
 * never the main thread. The refresh call uses a separate client and Dispatcher, so
 * it never waits for a slot held by the requests blocked on it.
 */
@Singleton
class SessionInterceptor @Inject constructor(
    private val store: SessionStore,
    private val refresher: SessionRefresher,
    private val clock: WallClock,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val session = sessionForRequest()
        return chain.proceed(chain.request().bindSession(session.id))
    }

    private fun sessionForRequest(): GuestSession {
        val current = store.current()
        val now = clock.nowMs()
        if (current != null && current.remainingMs(now) > PROACTIVE_THRESHOLD_MS) return current

        val trigger = if (current == null) SessionEvent.Kind.CREATED else SessionEvent.Kind.PROACTIVE_REFRESH
        return when (val result = runBlocking { refresher.refresh(current?.id, trigger) }) {
            is RefreshResult.Success -> result.session
            // Still valid for a few seconds: send it and let plan B handle any 401.
            RefreshResult.Transient -> current?.takeUnless { it.isExpired(now) }
                ?: throw IOException("No usable session and refresh failed")
            RefreshResult.Dead -> {
                store.clear()
                throw SessionExpiredException()
            }
        }
    }

    companion object {
        /** Refresh when less than this much session time remains. */
        const val PROACTIVE_THRESHOLD_MS = 60_000L
    }
}

/** The server definitively rejected the session; route the user to sign in. */
class SessionExpiredException : IOException("Session expired, sign in again")
