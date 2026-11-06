package com.example.popular.data

import com.example.entertainment.core.data.local.AppDatabaseProvider
import com.example.entertainment.core.data.local.AppDatabaseTopRated
import com.example.entertainment.core.data.local.StorageHealthMonitor
import com.example.entertainment.core.data.local.popular.PopularMoviesDao
import com.example.entertainment.core.home.HomeScreenApi
import com.example.entertainment.core.home.popularMovies.data.PopularMovieDTO
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import retrofit2.Response

/* Builds PopularMoviesRepoImpl over a mocked API + Room, with a real mapper and health monitor. */
class PopularMoviesRepoBuilder {

    val homeScreenApi = mockk<HomeScreenApi>()
    val popularMoviesDao = mockk<PopularMoviesDao>(relaxUnitFun = true)

    private val appDatabaseProvider = mockk<AppDatabaseProvider> {
        every { current } returns mockk<AppDatabaseTopRated> {
            every { popularMoviesDao() } returns popularMoviesDao
        }
    }

    fun withNetworkPage(page: Int, dto: PopularMovieDTO) = apply {
        coEvery { homeScreenApi.getPopularMovies(page = page) } returns Response.success(dto)
    }

    /* Rows Room reports before a write; the repo uses it as the sortOrder offset. */
    fun withCachedCount(count: Int) = apply {
        coEvery { popularMoviesDao.count() } returns count
    }

    fun build() = PopularMoviesRepoImpl(
        homeScreenApi = homeScreenApi,
        popularMovieDataMapper = PopularMovieDataMapper(),
        storageHealthMonitor = StorageHealthMonitor(),
        appDatabaseProvider = appDatabaseProvider,
        ioDispatcher = Dispatchers.Unconfined,
    )
}
