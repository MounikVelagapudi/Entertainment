package com.example.toprated

import com.example.entertainment.core.data.local.AppDatabaseProvider
import com.example.entertainment.core.data.local.AppDatabaseTopRated
import com.example.entertainment.core.data.local.StorageHealthMonitor
import com.example.entertainment.core.data.local.toprated.TopRatedDao
import com.example.entertainment.core.data.local.toprated.TopRatedMovieEntity
import com.example.entertainment.core.home.HomeScreenApi
import com.example.entertainment.core.home.toprated.data.TopRatedDTO
import com.example.toprated.data.TopRatedDataMapper
import com.example.toprated.data.TopRatedRepoImpl
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import retrofit2.Response

/* Builds TopRatedRepoImpl over a mocked API + Room, with a real mapper and health monitor. */
class TopRatedRepoBuilder {

    val homeScreenApi = mockk<HomeScreenApi>()
    val topRatedDao = mockk<TopRatedDao>(relaxUnitFun = true)
    val storageHealthMonitor = StorageHealthMonitor()

    private val appDatabaseProvider = mockk<AppDatabaseProvider> {
        every { current } returns databaseWith(topRatedDao)
    }

    fun withNetworkPage(dto: TopRatedDTO) = apply {
        coEvery { homeScreenApi.getTopRatedMovie() } returns Response.success(dto)
    }

    fun withNetworkThrowing(error: Throwable) = apply {
        coEvery { homeScreenApi.getTopRatedMovie() } throws error
    }

    fun withCachedCount(count: Int) = apply {
        coEvery { topRatedDao.count() } returns count
    }

    fun withCachedRows(vararg rows: TopRatedMovieEntity) = apply {
        every { topRatedDao.observeAll() } returns flowOf(rows.toList())
    }

    fun withCachedRow(id: Int, row: TopRatedMovieEntity?) = apply {
        coEvery { topRatedDao.findById(id) } returns row
    }

    fun withCacheWriteThrowing(error: Throwable) = apply {
        coEvery { topRatedDao.addDataToCache(any()) } throws error
    }

    fun withCacheReadThrowing(id: Int, error: Throwable) = apply {
        coEvery { topRatedDao.findById(id) } throws error
    }

    /* Room's flow throws on collection, e.g. a corrupt db file. */
    fun withCacheObserveThrowing(error: Throwable) = apply {
        every { topRatedDao.observeAll() } returns flow<List<TopRatedMovieEntity>> { throw error }
    }

    fun withRecreatedDatabaseHolding(vararg rows: TopRatedMovieEntity) = apply {
        val recoveredDao = mockk<TopRatedDao>(relaxUnitFun = true)
        every { recoveredDao.observeAll() } returns flowOf(rows.toList())
        every { appDatabaseProvider.recreate() } returns databaseWith(recoveredDao)
    }

    fun withRecreateThrowing(error: Throwable) = apply {
        every { appDatabaseProvider.recreate() } throws error
    }

    fun build() = TopRatedRepoImpl(
        homeScreenApi = homeScreenApi,
        topRatedDataMapper = TopRatedDataMapper(),
        appDatabaseProvider = appDatabaseProvider,
        storageHealthMonitor = storageHealthMonitor,
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun databaseWith(dao: TopRatedDao) = mockk<AppDatabaseTopRated> {
        every { topRatedDao() } returns dao
    }
}
