package com.example.entertainment.pos.fakes

import com.example.entertainment.pos.data.remote.PosApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import retrofit2.Retrofit

/* Same Retrofit wiring as PosModule, minus logging. */
fun posApi(vararg interceptors: Interceptor): PosApi {
    val client = OkHttpClient.Builder().apply { interceptors.forEach { addInterceptor(it) } }.build()
    return Retrofit.Builder()
        .baseUrl(PosApi.BASE_URL)
        .client(client)
        .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(PosApi::class.java)
}

data class RecordedRequest(val path: String, val idempotencyKey: String?, val body: String)

/* Records every call, then answers from [respond] or, if that returns null, passes it on. */
class RecordingInterceptor(
    var respond: ((RecordedRequest) -> Pair<Int, String>?)? = null,
) : Interceptor {
    val requests = mutableListOf<RecordedRequest>()
    var throwIo = false

    /* Let the real call go through, then pretend the response never came back. */
    var dropNextResponse = false

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val body = Buffer().also { request.body?.writeTo(it) }.readUtf8()
        val recorded = RecordedRequest(request.url.encodedPath, request.header("Idempotency-Key"), body)
        requests += recorded

        if (throwIo) throw IOException("socket timeout")
        respond?.invoke(recorded)?.let { (code, json) ->
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("x")
                .body(json.toResponseBody("application/json".toMediaType()))
                .build()
        }
        val response = chain.proceed(request)
        if (dropNextResponse) {
            dropNextResponse = false
            response.close()
            throw IOException("connection reset")
        }
        return response
    }
}
