package com.example.trending.data


import com.example.entertainment.core.data.networking.mappedApiCall
import com.example.entertainment.core.domain.Result
import com.example.entertainment.core.domain.TRPError
import com.example.entertainment.home.HomeScreenApi
import com.example.trending.domain.TrendingMoviesDomainModel
import com.example.trending.domain.TrendingRepo
import javax.inject.Inject

class TrendingRepoImpl @Inject constructor(
    private val api: HomeScreenApi,
    private val mapper: TrendingMapper
) : TrendingRepo {

    override suspend fun getTrendingMovies(): Result<TrendingMoviesDomainModel, TRPError> {
        return mappedApiCall(
            mapper = mapper,
            execute = { api.getTrendingMovies() }
        )
    }
}
