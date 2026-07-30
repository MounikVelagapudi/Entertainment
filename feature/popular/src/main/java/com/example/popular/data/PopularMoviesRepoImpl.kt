package com.example.popular.data

import com.example.entertainment.core.data.networking.mappedApiCall
import com.example.entertainment.core.domain.Result
import com.example.entertainment.core.domain.TRPError
import com.example.entertainment.home.HomeScreenApi
import com.example.popular.domain.PopularMoviesRepo
import javax.inject.Inject
import com.example.popular.domain.PopularMoviesDomainModel

class PopularMoviesRepoImpl @Inject constructor(
    private val homeScreenApi: HomeScreenApi,
    private val popularMovieDataMapper: PopularMovieDataMapper
) : PopularMoviesRepo {

    override suspend fun getPopularMovies(): Result<PopularMoviesDomainModel, TRPError> {
        return mappedApiCall(
            mapper = popularMovieDataMapper,
            execute = { homeScreenApi.getPopularMovies() }
        )
    }
}
