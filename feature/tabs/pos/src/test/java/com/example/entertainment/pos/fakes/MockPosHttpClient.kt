package com.example.entertainment.pos.fakes

import com.example.entertainment.pos.data.remote.MockBackend
import com.example.entertainment.pos.data.remote.MockPosInterceptor
import com.example.entertainment.pos.data.remote.PosApi
import com.example.entertainment.pos.testdata.aSaleJson
import kotlin.random.Random
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/* Raw OkHttp client in front of MockPosInterceptor, for testing the mock server itself.
   Each call returns (http code, body). */
class MockPosHttpClient(backend: MockBackend, random: Random = Random(42)) {

    private val client = OkHttpClient.Builder()
        .addInterceptor(MockPosInterceptor(backend, random))
        .build()

    fun post(idempotencyKey: String?, body: String = aSaleJson(), path: String = "v1/transactions"): Pair<Int, String> =
        execute(
            Request.Builder()
                .url(PosApi.BASE_URL + path)
                .post(body.toRequestBody("application/json".toMediaType()))
                .apply { idempotencyKey?.let { header("Idempotency-Key", it) } }
                .build()
        )

    fun get(path: String): Pair<Int, String> =
        execute(Request.Builder().url(PosApi.BASE_URL + path).get().build())

    private fun execute(request: Request) = client.newCall(request).execute().use { it.code to it.body.string() }
}
