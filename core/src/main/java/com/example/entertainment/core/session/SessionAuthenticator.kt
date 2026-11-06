package com.example.entertainment.core.session

import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

/**
 * **Plan B — reactive.** OkHttp calls this only when a response is `401`.
 *
 * - **First 401:** refresh (through the same Mutex as plan A, passing the id the
 *   request was sent with as stale), re-bind, and retry once. The caller never sees it.
 * - **Second 401** on that retry: a session minted seconds ago was rejected, so the
 *   server is authoritative. Clear the session and return null. The 401 reaches the
 *   caller, which routes to sign-in. Never refresh again here, or it loops.
 *
 * This is the correctness net plan A can't replace: a session revoked server-side,
 * a wrong device clock, or an app opened after the session died.
 */
@Singleton
class SessionAuthenticator @Inject constructor(
    private val store: SessionStore,
    private val refresher: SessionRefresher,
    private val log: SessionEventLog,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        val request = response.request
        val staleId = request.boundSessionId() ?: return null // not a session request

        if (response.priorResponse != null) {
            store.clear()
            log.record(SessionEvent.Kind.SIGNED_OUT, "Retry also got 401, signing out")
            return null
        }

        return when (val result = runBlocking { refresher.refresh(staleId, SessionEvent.Kind.REACTIVE_REFRESH) }) {
            is RefreshResult.Success -> request.bindSession(result.session.id)
            RefreshResult.Transient -> null // surface the 401; the next call tries again
            RefreshResult.Dead -> {
                store.clear()
                null
            }
        }
    }
}
