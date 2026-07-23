package com.example.entertainment.home.topRated.topRated.data

import com.example.entertainment.core.data.networking.mappedApiCall
import com.example.entertainment.core.domain.Result
import com.example.entertainment.core.domain.TRPError
import com.example.entertainment.home.HomeScreenApi
import com.example.entertainment.home.topRated.topRated.domain.TopRatedDomainModel
import com.example.entertainment.home.topRated.topRated.domain.TopRatedRepo
import javax.inject.Inject

class TopRatedRepoImpl @Inject constructor(
    private val homeScreenApi: HomeScreenApi,
    private val topRatedDataMapper: TopRatedDataMapper,
) : TopRatedRepo {
    override suspend fun getTopRatedMovies(): Result<TopRatedDomainModel, TRPError> {
        return mappedApiCall(
            mapper = topRatedDataMapper,
            execute = { homeScreenApi.getTopRatedMovie() }
        )
    }
}