package com.example.toprated.data

import com.example.entertainment.core.data.networking.mappedApiCall
import com.example.entertainment.core.domain.TRPError
import com.example.entertainment.home.HomeScreenApi
import com.example.toprated.domain.TopRatedDomainModel
import com.example.toprated.domain.TopRatedRepo
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