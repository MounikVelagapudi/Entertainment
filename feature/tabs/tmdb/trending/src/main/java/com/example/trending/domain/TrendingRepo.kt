package com.example.trending.domain

import com.example.entertainment.core.domain.Result
import com.example.entertainment.core.domain.TRPError

interface TrendingRepo {

    suspend fun getTrendingMovies(): Result<TrendingMoviesDomainModel, TRPError>
}
