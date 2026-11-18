package com.example.popular.data

import com.example.entertainment.core.data.local.AppDatabaseProvider
import com.example.entertainment.core.data.local.OfflineFirstCache
import com.example.entertainment.core.data.local.StorageHealthMonitor
import com.example.entertainment.core.data.local.popular.PopularMovieEntity
import com.example.entertainment.core.data.local.toprated.TopRatedMovieEntity
import com.example.entertainment.core.data.networking.mappedApiCall
import com.example.entertainment.core.domain.Resource
import com.example.entertainment.core.domain.Result
import com.example.entertainment.core.domain.TRPError
import com.example.entertainment.core.di.IoDispatcher
import com.example.entertainment.core.home.HomeScreenApi
import com.example.popular.domain.PopularMovie
import com.example.popular.domain.PopularMoviesRepo
import javax.inject.Inject
import com.example.popular.domain.PopularMoviesDomainModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PopularMoviesRepoImpl @Inject constructor(
    private val homeScreenApi: HomeScreenApi,
    private val popularMovieDataMapper: PopularMovieDataMapper,
    private val storageHealthMonitor: StorageHealthMonitor,
    private val appDatabaseProvider: AppDatabaseProvider,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
) : PopularMoviesRepo {

    private val cache = OfflineFirstCache<PopularMovieEntity, PopularMovie>(
        appDatabaseProvider = appDatabaseProvider,
        storageHealthMonitor = storageHealthMonitor,
        ioDispatcher = ioDispatcher,
        daoOf = { it.popularMoviesDao() },
        toDomain = { it.toDomain() },
        toEntity = { movie, sortOrder -> movie.toEntity(sortOrder) },
        domainId = { it.id },
    )

    override  fun observePopularMovies(): Flow<Resource<PopularMoviesDomainModel>> =
        cache.observe().map { resource ->
            when (resource) {
                is Resource.Loading -> Resource.Loading
                is Resource.Success -> Resource.Success(
                    PopularMoviesDomainModel(
                        page = 0,
                        results = resource.data,
                        totalPages = 0,
                        totalResults = resource.data.size
                    )
                )

                is Resource.Error -> Resource.Error(resource.message)
            }
        }

    override suspend fun refreshPopularMovies(page: Int): Result<PopularMoviesDomainModel, TRPError> {
        val result = mappedApiCall(
            mapper = popularMovieDataMapper,
            execute = { homeScreenApi.getPopularMovies(page = page) }
        )

        return when (result) {
            is Result.Success -> {
                cache.persist(result.data.results)
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


    override suspend fun getById(id: Int): PopularMovie {
        TODO("Not yet implemented")
    }
}
