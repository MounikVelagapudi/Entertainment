package com.example.entertainment.core.session

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the current [GuestSession] and exposes it as a [StateFlow].
 *
 * Read on every request by [SessionInterceptor], so [current] is an in-memory read;
 * disk is only touched on [save]/[clear].
 */
interface SessionStore {
    /** The session as an observable stream (for UI countdowns). */
    val session: StateFlow<GuestSession?>

    /** The current session, or null if signed out. */
    fun current(): GuestSession? = session.value

    /** Replace the session atomically (one assignment — never half-applied). */
    fun save(session: GuestSession)

    /** Sign out locally. */
    fun clear()
}

/**
 * [SessionStore] backed by SharedPreferences, so the session survives process death.
 *
 * A guest session id is low-value. A real access/refresh token would go in
 * Keystore-encrypted storage instead of plain prefs.
 */
@Singleton
class SharedPrefsSessionStore @Inject constructor(
    @ApplicationContext context: Context,
) : SessionStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _session = MutableStateFlow(load())
    override val session: StateFlow<GuestSession?> = _session.asStateFlow()

    @Synchronized
    override fun save(session: GuestSession) {
        prefs.edit()
            .putString(KEY_ID, session.id)
            .putLong(KEY_EXPIRES_AT, session.expiresAtServerMs)
            .putLong(KEY_SKEW, session.clockSkewMs)
            .apply()
        _session.value = session
    }

    @Synchronized
    override fun clear() {
        prefs.edit().clear().apply()
        _session.value = null
    }

    private fun load(): GuestSession? {
        val id = prefs.getString(KEY_ID, null) ?: return null
        return GuestSession(
            id = id,
            expiresAtServerMs = prefs.getLong(KEY_EXPIRES_AT, 0L),
            clockSkewMs = prefs.getLong(KEY_SKEW, 0L),
        )
    }

    private companion object {
        const val PREFS_NAME = "guest_session"
        const val KEY_ID = "id"
        const val KEY_EXPIRES_AT = "expires_at_server_ms"
        const val KEY_SKEW = "clock_skew_ms"
    }
}
