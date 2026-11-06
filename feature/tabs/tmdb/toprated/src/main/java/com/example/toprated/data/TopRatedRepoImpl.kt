package com.example.toprated.data

import com.example.entertainment.core.data.local.AppDatabaseProvider
import com.example.entertainment.core.data.local.OfflineFirstCache
import com.example.entertainment.core.data.local.StorageHealthMonitor
import com.example.entertainment.core.data.local.toprated.TopRatedMovieEntity
import com.example.entertainment.core.data.networking.mappedApiCall
import com.example.entertainment.core.domain.Resource
import com.example.entertainment.core.domain.Result
import com.example.entertainment.core.domain.TRPError
import com.example.entertainment.core.di.IoDispatcher
import com.example.entertainment.core.home.HomeScreenApi
import com.example.toprated.domain.TopRatedDomainModel
import com.example.toprated.domain.TopRatedMovie
import com.example.toprated.domain.TopRatedRepo
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TopRatedRepoImpl @Inject constructor(
    private val homeScreenApi: HomeScreenApi,
    private val topRatedDataMapper: TopRatedDataMapper,
    appDatabaseProvider: AppDatabaseProvider,
    storageHealthMonitor: StorageHealthMonitor,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
) : TopRatedRepo {

    private val cache = OfflineFirstCache<TopRatedMovieEntity, TopRatedMovie>(
        appDatabaseProvider = appDatabaseProvider,
        storageHealthMonitor = storageHealthMonitor,
        ioDispatcher = ioDispatcher,
        daoOf = { it.topRatedDao() },
        toDomain = { it.toDomain() },
        toEntity = { movie, sortOrder -> movie.toEntity(sortOrder) },
        domainId = { it.id },
    )

    override fun observeTopRated(): Flow<Resource<TopRatedDomainModel>> =
        cache.observe().map { resource ->
            when (resource) {
                is Resource.Loading -> Resource.Loading
                is Resource.Success -> Resource.Success(
                    TopRatedDomainModel(
                        page = 0,
                        results = resource.data,
                        totalPages = 0,
                        totalResults = resource.data.size
                    )
                )

                is Resource.Error -> Resource.Error(resource.message)
            }
        }

    override suspend fun refreshTopRatedApi(page: Int): Result<TopRatedDomainModel, TRPError> {
        val result = mappedApiCall(
            mapper = topRatedDataMapper,
            execute = { homeScreenApi.getTopRatedMovie(page = page) }
        )

        return when (result) {
            is Result.Success -> {
                /* Cache untouched on error/failure below -- whatever's cached stays visible. */
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

    override suspend fun getById(id: Int): TopRatedMovie? = cache.getById(id)
}
