package com.example.popular.domain

import com.example.entertainment.core.domain.Resource
import com.example.entertainment.core.domain.Result
import com.example.entertainment.core.domain.TRPError
import com.example.popular.data.PopularMoviesRepoImpl
import kotlinx.coroutines.flow.Flow

interface PopularMoviesRepo {

     fun observePopularMovies(): Flow<Resource<PopularMoviesDomainModel>>

    /* Syncs one page and returns it (with page/totalPages) so the caller can tell if
       there's more to load -- the combined list itself only ever comes from observePopularMovies(). */
    suspend fun refreshPopularMovies(page: Int): Result<PopularMoviesDomainModel, TRPError>

    suspend fun getById(id: Int): PopularMovie

}
