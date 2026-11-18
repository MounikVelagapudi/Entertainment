package com.example.entertainment.home.trendingMovies.domain

import com.example.entertainment.core.domain.Result
import com.example.entertainment.core.domain.TRPError
import com.example.trending.domain.TrendingMoviesDomainModel
import com.example.trending.domain.TrendingRepo
import com.example.trending.domain.TrendingUseCase
import io.mockk.coEvery
import io.mockk.mockk

class TrendingUseCaseBuilder {

    val trendingRepo = mockk<TrendingRepo>()

    fun withRefreshResult(result: Result<TrendingMoviesDomainModel, TRPError>) = apply {
        coEvery { trendingRepo.refreshTrendingMovie() } returns result
    }

    fun build() = TrendingUseCase(trendingRepo)
}
