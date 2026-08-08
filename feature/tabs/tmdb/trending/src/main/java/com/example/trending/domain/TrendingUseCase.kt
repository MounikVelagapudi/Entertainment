package com.example.trending.domain

import com.example.entertainment.core.domain.Result
import com.example.entertainment.core.domain.TRPError
import com.example.entertainment.core.domain.validate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrendingUseCase @Inject constructor(
    private val trendingRepo: TrendingRepo
) {

    suspend operator fun invoke(): Result<TrendingMoviesDomainModel, TRPError> {
        return trendingRepo.getTrendingMovies().validate {
            true
        }
    }
}
