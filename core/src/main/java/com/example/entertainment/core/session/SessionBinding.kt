package com.example.entertainment.core.session

import okhttp3.Request

/** Stand-in value an API declares where the live session id must go. */
const val SESSION_PLACEHOLDER = "__guest_session__"

/** TMDB's query-parameter name for a guest session. */
const val GUEST_SESSION_QUERY = "guest_session_id"

/** Request tag recording which session id a request was sent with. */
internal data class BoundSession(val id: String)

/** The session id this request carries, or null if it isn't a session request. */
internal fun Request.boundSessionId(): String? = tag(BoundSession::class.java)?.id

/**
 * Returns a copy of this request carrying [sessionId] in place of the placeholder,
 * or in place of the stale id it was first sent with.
 *
 * Shared by [SessionInterceptor] (first bind) and [SessionAuthenticator] (re-bind on
 * retry). The authenticator has to re-bind itself: OkHttp's retry runs *below* the
 * application interceptors, so they never see it.
 */
internal fun Request.bindSession(sessionId: String): Request {
    val previous = boundSessionId() ?: SESSION_PLACEHOLDER
    val newUrl = url.newBuilder().apply {
        url.pathSegments.forEachIndexed { index, segment ->
            if (segment == previous) setPathSegment(index, sessionId)
        }
        if (url.queryParameter(GUEST_SESSION_QUERY) != null) {
            setQueryParameter(GUEST_SESSION_QUERY, sessionId)
        }
    }.build()
    return newBuilder()
        .url(newUrl)
        .tag(BoundSession::class.java, BoundSession(sessionId))
        .build()
}
