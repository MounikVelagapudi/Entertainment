package com.example.entertainment.core.session

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * End-to-end over real OkHttp: interceptor (plan A) + authenticator (plan B) +
 * Mutex refresher, against a fake TMDB that behaves like the real one
 * (unknown session → 401 `{status_code: 3}`).
 */
class SessionRefreshFlowTest {

    private val clock = FakeClock()
    private val tmdb = FakeTmdb(clock)
    private val server = MockWebServer()
    private val store = InMemorySessionStore()
    private val log = SessionEventLog(clock)

    private lateinit var api: GuestSessionApi

    @Before
    fun setUp() {
        server.dispatcher = tmdb
        server.start()
        api = GuestSessionApiBuilder(server, store, clock, log).build()
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun `no session - mints one silently before the first call`() = runBlocking {
        val response = api.rateMovie(550, RatingRequest(8.0))

        assertEquals(201, response.code())
        assertEquals(1, tmdb.mintCount.get())
        assertEquals(store.current()?.id, tmdb.sentIds.single())
    }

    @Test
    fun `plenty of time left - no refresh, stored session is used`() = runBlocking {
        val session = storedSession(remainingMs = 10 * 60_000)

        api.rateMovie(550, RatingRequest(8.0))

        assertEquals(0, tmdb.mintCount.get())
        assertEquals(session.id, tmdb.sentIds.single())
    }

    @Test
    fun `plan A - under 60s left refreshes before the call, server never sees a 401`() = runBlocking {
        val old = storedSession(remainingMs = 30_000)

        val response = api.rateMovie(550, RatingRequest(8.0))

        assertEquals(201, response.code())
        assertEquals(1, tmdb.mintCount.get())
        assertNotEquals(old.id, tmdb.sentIds.single())
        assertEquals(SessionEvent.Kind.PROACTIVE_REFRESH, log.events.value.first().kind)
    }

    @Test
    fun `plan A transient failure - keeps the still-valid session instead of failing`() = runBlocking {
        val old = storedSession(remainingMs = 30_000)
        tmdb.mintStatus = 503

        val response = api.rateMovie(550, RatingRequest(8.0))

        assertEquals(201, response.code())
        assertEquals(old.id, tmdb.sentIds.single())
        assertEquals(old, store.current())
    }

    @Test
    fun `plan B - first 401 refreshes and retries once with the new id`() = runBlocking {
        val revoked = storedSession(remainingMs = 10 * 60_000, acceptedByServer = false)

        val response = api.rateMovie(550, RatingRequest(8.0))

        assertEquals(201, response.code())
        assertEquals(1, tmdb.mintCount.get())
        assertEquals(listOf(revoked.id, store.current()?.id), tmdb.sentIds.toList())
        assertEquals(SessionEvent.Kind.REACTIVE_REFRESH, log.events.value.first().kind)
    }

    @Test
    fun `plan B - 401 again on the retry signs out instead of looping`() = runBlocking {
        storedSession(remainingMs = 10 * 60_000, acceptedByServer = false)
        tmdb.rejectEverything = true

        val response = api.rateMovie(550, RatingRequest(8.0))

        assertEquals(401, response.code())
        assertEquals(1, tmdb.mintCount.get())
        assertEquals(2, tmdb.sentIds.size)
        assertNull(store.current())
        assertEquals(SessionEvent.Kind.SIGNED_OUT, log.events.value.first().kind)
    }

    @Test
    fun `refresh storm - many concurrent 401s mint exactly one session`() = runBlocking {
        storedSession(remainingMs = 10 * 60_000, acceptedByServer = false)

        val codes = (1..8).map { async(Dispatchers.IO) { api.ratedMovies().code() } }.awaitAll()

        assertTrue(codes.all { it == 200 })
        assertEquals(1, tmdb.mintCount.get())
    }

    @Test
    fun `expiry uses the server clock, not the device clock`() = runBlocking {
        tmdb.serverSkewMs = 5 * 60_000L // device clock is 5 min behind the server

        api.rateMovie(550, RatingRequest(8.0))

        val skew = store.current()?.clockSkewMs ?: error("no session")
        assertTrue("skew was $skew", skew in (5 * 60_000L - 1_000)..(5 * 60_000L + 1_000))
    }

    /* Saves a session expiring remainingMs from now; acceptedByServer = false simulates a revoked id. */
    private fun storedSession(remainingMs: Long, acceptedByServer: Boolean = true): GuestSession {
        val session = aGuestSession(
            id = "seed-$remainingMs-$acceptedByServer",
            expiresAtServerMs = clock.nowMs() + remainingMs,
        )
        if (acceptedByServer) tmdb.validIds += session.id
        store.save(session)
        return session
    }
}
