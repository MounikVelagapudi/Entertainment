package com.example.trending.domain

import com.example.entertainment.core.domain.Resource
import com.example.entertainment.core.domain.Result
import com.example.entertainment.core.domain.TRPError
import kotlinx.coroutines.flow.Flow

interface TrendingRepo {

    suspend fun refreshTrendingMovie(): Result<TrendingMoviesDomainModel, TRPError>
    fun observe(): Flow<Resource<TrendingMoviesDomainModel>>
    suspend fun getById(): TrendingMoviesDomainModel?
}
