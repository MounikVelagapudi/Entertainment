package com.example.entertainment.home.popularMovies.domain

import com.example.entertainment.core.domain.Result
import com.example.entertainment.core.domain.TRPError

interface PopularMoviesRepo {

    suspend fun getPopularMovies(): Result<PopularMoviesDomainModel, TRPError>

}
