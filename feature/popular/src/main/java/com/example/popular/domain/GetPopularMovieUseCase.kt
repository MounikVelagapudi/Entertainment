package com.example.entertainment.home.popularMovies.domain

import com.example.entertainment.core.domain.Result
import com.example.entertainment.core.domain.TRPError
import javax.inject.Inject

class GetPopularMovieUseCase @Inject constructor(
    private val popularMoviesRepo: PopularMoviesRepo
) {
    suspend fun invoke(): Result<PopularMoviesDomainModel, TRPError> {
        return popularMoviesRepo.getPopularMovies()
    }
}