package com.example.entertainment.core.session

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicInteger

/* Fake TMDB with the same paths and the same 401 body as the real API.
   Tests flip the knobs below to simulate outages, revoked ids and clock skew. */
class FakeTmdb(private val clock: WallClock) : Dispatcher() {

    /* Ids the fake server accepts. */
    val validIds: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    val sentIds: MutableList<String> = Collections.synchronizedList(mutableListOf())
    val mintCount = AtomicInteger()

    @Volatile var mintStatus = 200
    @Volatile var rejectEverything = false
    @Volatile var serverSkewMs = 0L

    override fun dispatch(request: RecordedRequest): MockResponse {
        val url = request.url
        if (url.encodedPath == "/3/authentication/guest_session/new") return mintSession()

        val id = url.queryParameter(GUEST_SESSION_QUERY) ?: url.pathSegments.getOrNull(2).orEmpty()
        sentIds += id
        if (rejectEverything || id !in validIds) {
            return MockResponse.Builder().code(401)
                .body("""{"success":false,"status_code":3,"status_message":"Authentication failed"}""")
                .build()
        }
        return if (request.method == "POST") {
            MockResponse.Builder().code(201).body("""{"status_code":1,"status_message":"Success."}""").build()
        } else {
            MockResponse.Builder().code(200).body("""{"results":[]}""").build()
        }
    }

    private fun mintSession(): MockResponse {
        if (mintStatus != 200) return MockResponse.Builder().code(mintStatus).build()
        val id = "fresh-${mintCount.incrementAndGet()}"
        validIds += id
        Thread.sleep(50) // widen the race window for the storm test
        val serverNow = clock.nowMs() + serverSkewMs
        return MockResponse.Builder()
            .code(200)
            .addHeader("Date", httpDate(serverNow))
            .body("""{"success":true,"guest_session_id":"$id","expires_at":"${tmdbDate(serverNow + DAY_MS)}"}""")
            .build()
    }

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1_000L

        fun tmdbDate(ms: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(ms))

        fun httpDate(ms: Long): String = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("GMT") }.format(Date(ms))
    }
}
