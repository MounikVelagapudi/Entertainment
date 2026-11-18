package com.example.entertainment.core.session

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Singleton

/** Interface bindings for the session layer. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SessionBindings {

    /** SharedPreferences is the production store. */
    @Binds
    abstract fun bindSessionStore(impl: SharedPrefsSessionStore): SessionStore
}

/**
 * Builds two clients from the app's base [OkHttpClient]:
 *
 * - **Auth client** (the refresh call): no session interceptor or authenticator, and its
 *   own [Dispatcher], so a refresh can't recurse or deadlock behind blocked requests.
 * - **Session client** (real calls): [SessionInterceptor] at index 0, so the logging
 *   interceptor logs the bound URL, plus [SessionAuthenticator].
 *
 * The plain TMDB movie lists keep the untouched base client.
 */
@Module
@InstallIn(SingletonComponent::class)
object SessionModule {

    private const val TMDB_BASE_URL = "https://api.themoviedb.org/"

    /** System clock in production. */
    @Provides
    fun provideWallClock(): WallClock = WallClock { System.currentTimeMillis() }

    /** Refresh API on the bare auth client. */
    @Provides
    @Singleton
    fun provideGuestSessionAuthApi(base: OkHttpClient, json: Json): GuestSessionAuthApi =
        retrofit(base.newBuilder().dispatcher(Dispatcher()).build(), json)
            .create(GuestSessionAuthApi::class.java)

    /** Session-protected API with proactive + reactive refresh wired in. */
    @Provides
    @Singleton
    fun provideGuestSessionApi(
        base: OkHttpClient,
        json: Json,
        interceptor: SessionInterceptor,
        authenticator: SessionAuthenticator,
    ): GuestSessionApi {
        val client = base.newBuilder()
            .apply { interceptors().add(0, interceptor) }
            .authenticator(authenticator)
            .build()
        return retrofit(client, json).create(GuestSessionApi::class.java)
    }

    private fun retrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(TMDB_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
}
