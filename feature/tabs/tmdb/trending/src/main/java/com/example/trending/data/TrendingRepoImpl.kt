package com.example.trending.data


import com.example.entertainment.core.data.local.AppDatabaseProvider
import com.example.entertainment.core.data.local.OfflineFirstCache
import com.example.entertainment.core.data.local.StorageHealthMonitor
import com.example.entertainment.core.data.local.trending.TrendingMovieEntity
import com.example.entertainment.core.data.networking.mappedApiCall
import com.example.entertainment.core.di.IoDispatcher
import com.example.entertainment.core.domain.Resource
import com.example.entertainment.core.domain.Result
import com.example.entertainment.core.domain.TRPError
import com.example.entertainment.core.home.HomeScreenApi
import com.example.trending.domain.TrendingMovie
import com.example.trending.domain.TrendingMoviesDomainModel
import com.example.trending.domain.TrendingRepo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class TrendingRepoImpl @Inject constructor(
    private val api: HomeScreenApi,
    private val mapper: TrendingDataMapper,
    appDatabaseProvider: AppDatabaseProvider,
    storageHealthMonitor: StorageHealthMonitor,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
) : TrendingRepo {

    private val cache = OfflineFirstCache<TrendingMovieEntity, TrendingMovie>(
        appDatabaseProvider = appDatabaseProvider,
        storageHealthMonitor = storageHealthMonitor,
        ioDispatcher = ioDispatcher,
        daoOf = { it.trendingMoviesDao() },
        toDomain = { it.toDomain() },
        toEntity = { movie, sortOrder -> movie.toEntity(sortOrder) },
        domainId = { it.id },
    )

    override suspend fun refreshTrendingMovie(): Result<TrendingMoviesDomainModel, TRPError> {
        val result = mappedApiCall(
            mapper = mapper,
            execute = { api.getTrendingMovies() }
        )

        return when (result) {
            is Result.Success -> {
                cache.persist(result.data.results ?: emptyList())
                Result.Success(result.data)
            }

            is Result.Error -> {
                cache.lastError.value = result.error
                Result.Error(result.error)
            }

            is Result.Failure -> {
                cache.lastError.value = result.failure
                Result.Failure(result.failure)
            }
        }

    }

    override  fun observe(): Flow<Resource<TrendingMoviesDomainModel>> =
        cache.observe().map { resource ->
            when (resource) {
                is Resource.Loading -> Resource.Loading
                is Resource.Success -> Resource.Success(
                    TrendingMoviesDomainModel(
                        results = resource.data,
                        totalResults = resource.data.size,
                        page = 0,
                        totalPages = 0
                    )
                )

                is Resource.Error -> Resource.Error(resource.message)
            }
        }

    override suspend fun getById(): TrendingMoviesDomainModel? {
        TODO("Not yet implemented")
    }
}


