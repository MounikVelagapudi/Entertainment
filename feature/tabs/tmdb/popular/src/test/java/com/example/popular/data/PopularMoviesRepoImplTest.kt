package com.example.popular.data

import com.example.entertainment.core.data.local.AppDatabaseProvider
import com.example.entertainment.core.data.local.AppDatabaseTopRated
import com.example.entertainment.core.data.local.StorageHealthMonitor
import com.example.entertainment.core.data.local.popular.PopularMovieEntity
import com.example.entertainment.core.data.local.popular.PopularMoviesDao
import com.example.entertainment.core.home.HomeScreenApi
import com.example.entertainment.core.home.popularMovies.data.PopularMovieDTO
import com.example.entertainment.core.home.popularMovies.data.ResultDTO
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

/**
 * Covers the specific bug that was fixed: scrolling to the next page used to
 * restart sortOrder at 0 for every page, colliding with rows already cached
 * from earlier pages instead of appending after them. writeToRoom() in
 * OfflineFirstCache now reads dao.count() as an offset before assigning
 * sortOrder -- this asserts that offset is actually threaded through end to
 * end via the real repo, not just the DAO in isolation.
 */
class PopularMoviesRepoImplTest {

    private val homeScreenApi = mockk<HomeScreenApi>()
    private val popularMoviesDao = mockk<PopularMoviesDao>(relaxUnitFun = true)
    private val appDatabaseTopRated = mockk<AppDatabaseTopRated> {
        every { popularMoviesDao() } returns popularMoviesDao
    }
    private val appDatabaseProvider = mockk<AppDatabaseProvider> {
        every { current } returns appDatabaseTopRated
    }
    private val storageHealthMonitor = StorageHealthMonitor()

    private val repo = PopularMoviesRepoImpl(
        homeScreenApi = homeScreenApi,
        popularMovieDataMapper = PopularMovieDataMapper(),
        storageHealthMonitor = storageHealthMonitor,
        appDatabaseProvider = appDatabaseProvider,
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun popularMovieDto(page: Int, ids: List<Int>) = PopularMovieDTO(
        page = page,
        resultDTOS = ids.map { id -> ResultDTO(id = id, title = "Movie $id") },
        totalPages = 5,
        totalResults = 100
    )

    @Test
    fun `second page's sortOrder continues from Room's current row count instead of restarting at 0`() =
        runTest {
            val capturedBatches = mutableListOf<List<PopularMovieEntity>>()

            /* Page 1: nothing cached yet. */
            coEvery { popularMoviesDao.count() } returns 0
            coEvery { homeScreenApi.getPopularMovies(page = 1) } returns
                Response.success(popularMovieDto(page = 1, ids = listOf(1, 2)))

            repo.refreshPopularMovies(1)

            /* Page 2: Room now reports the 2 rows page 1 wrote -- writeToRoom()'s offset. */
            coEvery { popularMoviesDao.count() } returns 2
            coEvery { homeScreenApi.getPopularMovies(page = 2) } returns
                Response.success(popularMovieDto(page = 2, ids = listOf(3, 4)))

            repo.refreshPopularMovies(2)

            coVerify(exactly = 2) { popularMoviesDao.addDataToCache(capture(capturedBatches)) }

            assertEquals(listOf(1, 2), capturedBatches[0].map { it.id })
            assertEquals(listOf(0, 1), capturedBatches[0].map { it.sortOrder })

            assertEquals(listOf(3, 4), capturedBatches[1].map { it.id })
            assertEquals(listOf(2, 3), capturedBatches[1].map { it.sortOrder })

            /* Neither call clears the table -- page 1's rows should still be there for page 2. */
            coVerify(exactly = 0) { popularMoviesDao.clearAll() }
        }
}
