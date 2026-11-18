package com.example.popular.domain

import com.example.entertainment.core.domain.Resource
import com.example.entertainment.core.domain.Result
import com.example.entertainment.core.domain.TRPError
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetPopularMovieUseCase @Inject constructor(
    private val popularMoviesRepo: PopularMoviesRepo
) {
     fun observe(): Flow<Resource<PopularMoviesDomainModel>> =
        popularMoviesRepo.observePopularMovies()

    suspend fun refresh(page: Int): Result<PopularMoviesDomainModel, TRPError> =
        popularMoviesRepo.refreshPopularMovies(page)

}