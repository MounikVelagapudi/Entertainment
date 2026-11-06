package com.example.entertainment.core.session

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import mockwebserver3.MockWebServer
import okhttp3.Dispatcher as OkDispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

/* The real session stack (interceptor = plan A, authenticator = plan B, Mutex refresher)
   wired over real OkHttp and pointed at a MockWebServer. */
class GuestSessionApiBuilder(
    private val server: MockWebServer,
    private val store: SessionStore,
    private val clock: WallClock,
    private val log: SessionEventLog,
) {

    private val json = Json { ignoreUnknownKeys = true }

    fun build(): GuestSessionApi {
        val authApi = retrofit(OkHttpClient.Builder().dispatcher(OkDispatcher()).build())
            .create(GuestSessionAuthApi::class.java)
        val refresher = SessionRefresher(authApi, store, clock, log)
        val client = OkHttpClient.Builder()
            .addInterceptor(SessionInterceptor(store, refresher, clock))
            .authenticator(SessionAuthenticator(store, refresher, log))
            .build()
        return retrofit(client).create(GuestSessionApi::class.java)
    }

    private fun retrofit(client: OkHttpClient) = Retrofit.Builder()
        .baseUrl(server.url("/"))
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
}
