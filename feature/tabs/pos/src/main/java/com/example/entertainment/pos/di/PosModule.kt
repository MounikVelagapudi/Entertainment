package com.example.entertainment.pos.di

import android.content.Context
import androidx.room.Room
import com.example.entertainment.pos.data.OfflineFirstPosRepository
import com.example.entertainment.pos.data.network.AndroidNetworkMonitor
import com.example.entertainment.pos.data.payment.SimulatedPaymentProcessor
import com.example.entertainment.pos.data.local.PosDatabase
import com.example.entertainment.pos.data.local.TransactionDao
import com.example.entertainment.pos.data.remote.MockPosInterceptor
import com.example.entertainment.pos.data.remote.PosApi
import com.example.entertainment.pos.data.scanner.SimulatedBarcodeScanner
import com.example.entertainment.pos.domain.BarcodeScanner
import com.example.entertainment.pos.domain.NetworkMonitor
import com.example.entertainment.pos.domain.PaymentProcessor
import com.example.entertainment.pos.domain.PosRepository
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
abstract class PosBindings {

    @Binds
    abstract fun bindRepository(impl: OfflineFirstPosRepository): PosRepository

    @Binds
    abstract fun bindScanner(impl: SimulatedBarcodeScanner): BarcodeScanner

    @Binds
    abstract fun bindPaymentProcessor(impl: SimulatedPaymentProcessor): PaymentProcessor

    @Binds
    abstract fun bindNetworkMonitor(impl: AndroidNetworkMonitor): NetworkMonitor
}

@Module
@InstallIn(SingletonComponent::class)
object PosModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PosDatabase =
        /* No destructive fallback -- these are unsynced sales, a bad migration should crash. */
        Room.databaseBuilder(context, PosDatabase::class.java, "pos_transactions.db").build()

    @Provides
    fun provideTransactionDao(db: PosDatabase): TransactionDao = db.transactionDao()

    /* Own client, not the app's TMDB one -- the mock must not swallow movie calls.
       Swap MockPosInterceptor out here once there's a real backend. */
    @Provides
    @Singleton
    fun providePosApi(mock: MockPosInterceptor): PosApi {
        val client = OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
            .addInterceptor(mock)
            .build()

        return Retrofit.Builder()
            .baseUrl(PosApi.BASE_URL)
            .client(client)
            .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(PosApi::class.java)
    }
}
