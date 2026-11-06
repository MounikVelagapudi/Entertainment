package com.example.trending.domain

import com.example.entertainment.core.domain.Resource
import com.example.entertainment.core.domain.Result
import com.example.entertainment.core.domain.TRPError
import com.example.entertainment.core.domain.validate
import kotlinx.coroutines.flow.Flow
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrendingUseCase @Inject constructor(
    private val trendingRepo: TrendingRepo
) {
    fun observe(): Flow<Resource<TrendingMoviesDomainModel>> =
        trendingRepo.observe()

    suspend fun refresh(): Result<TrendingMoviesDomainModel, TRPError> {
        return trendingRepo.refreshTrendingMovie()
    }
}
