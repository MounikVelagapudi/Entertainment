package com.example.toprated

import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteFullException
import com.example.entertainment.core.data.local.AppDatabaseTopRated
import com.example.entertainment.core.data.local.AppDatabaseProvider
import com.example.entertainment.core.data.local.StorageHealthMonitor
import com.example.entertainment.core.data.local.toprated.TopRatedDao
import com.example.entertainment.core.data.local.toprated.TopRatedMovieEntity
import com.example.entertainment.core.home.toprated.data.Result as ResultDto
import com.example.entertainment.core.home.toprated.data.TopRatedDTO
import com.example.entertainment.core.domain.Resource
import com.example.entertainment.core.domain.Result
import com.example.entertainment.core.home.HomeScreenApi
import com.example.toprated.data.TopRatedDataMapper
import com.example.toprated.data.TopRatedRepoImpl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

/**
 * Covers the offline-first + storage fail-safe behavior: refresh() writes the
 * network response into Room on success and leaves the cache alone on failure;
 * observeTopRated()/getById() read whatever's currently cached, independent of
 * the network; a corrupt db gets one recreate-and-retry attempt before anything
 * else happens; and once that's exhausted (or the failure wasn't corruption to
 * begin with, e.g. a full disk) StorageHealthMonitor flips and both reroute to
 * an in-memory buffer instead of hanging or crashing.
 */
class TopRatedRepoImplTest {

    private val homeScreenApi = mockk<HomeScreenApi>()
    private val topRatedDao = mockk<TopRatedDao>(relaxUnitFun = true)
    private val appDatabaseTopRated = mockk<AppDatabaseTopRated> {
        every { topRatedDao() } returns topRatedDao
    }
    private val appDatabaseProvider = mockk<AppDatabaseProvider> {
        every { current } returns appDatabaseTopRated
    }
    private val topRatedDataMapper = TopRatedDataMapper()
    private val storageHealthMonitor = StorageHealthMonitor()

    private val repo = TopRatedRepoImpl(
        homeScreenApi = homeScreenApi,
        topRatedDataMapper = topRatedDataMapper,
        appDatabaseProvider = appDatabaseProvider,
        storageHealthMonitor = storageHealthMonitor,
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun topRatedDto() = TopRatedDTO(
        page = 1,
        totalPages = 1,
        totalResults = 1,
        results = listOf(
            ResultDto(
                adult = false,
                backdropPath = "/backdrop.jpg",
                genreIds = listOf(28, 12),
                id = 1,
                originalLanguage = "en",
                originalTitle = "Inception",
                overview = "A thief who steals secrets.",
                popularity = 123.4,
                posterPath = "/poster.jpg",
                releaseDate = "2010-07-16",
                softcore = false,
                title = "Inception",
                video = false,
                voteAverage = 8.8,
                voteCount = 30000
            )
        )
    )

    private fun topRatedEntity(id: Int = 1) = TopRatedMovieEntity(
        id = id,
        title = "Inception",
        originalTitle = "Inception",
        posterPath = "/poster.jpg",
        backdropPath = "/backdrop.jpg",
        releaseDate = "2010-07-16",
        originalLanguage = "en",
        popularity = 123.4,
        voteAverage = 8.8,
        voteCount = 30000,
        isAdult = false,
        isVideo = false,
        genreIds = listOf(28, 12),
        sortOrder = 0
    )

    @Test
    fun `refresh writes the mapped network response into Room on success`() = runTest {
        coEvery { homeScreenApi.getTopRatedMovie() } returns Response.success(topRatedDto())
        coEvery { topRatedDao.count() } returns 0

        val result = repo.refreshTopRatedApi(1)

        assertTrue(result is Result.Success)
        coVerify(exactly = 1) {
            topRatedDao.addDataToCache(match { entities ->
                entities.size == 1 && entities[0].id == 1 && entities[0].sortOrder == 0
            })
        }
        assertTrue(storageHealthMonitor.isCompromised.not())
    }

    @Test
    fun `refresh leaves the Room cache untouched when the network call fails`() = runTest {
        /* safeCall maps this to Result.Error, never even reaching the mapper/DAO. */
        coEvery { homeScreenApi.getTopRatedMovie() } throws UnknownHostException()

        val result = repo.refreshTopRatedApi(1)

        assertTrue(result is Result.Error)
        coVerify(exactly = 0) { topRatedDao.addDataToCache(any()) }
    }

    @Test
    fun `observeTopRated emits Success mapped from whatever Room currently has`() = runTest {
        every { topRatedDao.observeAll() } returns flowOf(listOf(topRatedEntity()))

        val resource = repo.observeTopRated().first()

        assertTrue(resource is Resource.Success)
        assertEquals(1, (resource as Resource.Success).data.results.size)
        assertEquals("Inception", resource.data.results[0].title)
    }

    @Test
    fun `observeTopRated emits Loading when Room is empty and there's no refresh error`() = runTest {
        every { topRatedDao.observeAll() } returns flowOf(emptyList())

        val resource = repo.observeTopRated().first()

        assertEquals(Resource.Loading, resource)
    }

    @Test
    fun `observeTopRated emits Error when Room is empty and the last refresh failed`() = runTest {
        /* Nothing in Room yet -- e.g. first launch, no prior successful sync. */
        every { topRatedDao.observeAll() } returns flowOf(emptyList())
        coEvery { homeScreenApi.getTopRatedMovie() } throws UnknownHostException()

        repo.refreshTopRatedApi(1)
        val resource = repo.observeTopRated().first()

        assertEquals(Resource.Error("NoInternetError"), resource)
    }

    @Test
    fun `refresh reports a Room write failure to the health monitor and falls back to the RAM buffer`() =
        runTest {
            /* The network sync succeeds, but persisting it is what's broken -- e.g. disk full. */
            coEvery { homeScreenApi.getTopRatedMovie() } returns Response.success(topRatedDto())
            coEvery { topRatedDao.count() } returns 0
            coEvery { topRatedDao.addDataToCache(any()) } throws SQLiteFullException("disk full")

            val result = repo.refreshTopRatedApi(1)

            /* Still "succeeds" from the caller's view -- data reached the user via the RAM buffer. */
            assertTrue(result is Result.Success)
            assertTrue(storageHealthMonitor.isCompromised)
            assertEquals(1, repo.getById(1)?.id)
        }

    @Test
    fun `observeTopRated self-heals by recreating Room when a corrupt db recovers cleanly`() = runTest {
        /* Room throws corruption on the first collection, but the recreated db works fine. */
        every { topRatedDao.observeAll() } returns flow<List<TopRatedMovieEntity>> {
            throw SQLiteDatabaseCorruptException("corrupt")
        }
        val recoveredDao = mockk<TopRatedDao>(relaxUnitFun = true)
        every { recoveredDao.observeAll() } returns flowOf(listOf(topRatedEntity()))
        every { appDatabaseProvider.recreate() } returns mockk<AppDatabaseTopRated> {
            every { topRatedDao() } returns recoveredDao
        }

        val resource = repo.observeTopRated().first { it !is Resource.Loading }

        /* Recovered cleanly -- Room stays the source of truth, RAM fallback never involved. */
        assertTrue(resource is Resource.Success)
        assertEquals(1, (resource as Resource.Success).data.results.first().id)
        assertTrue(storageHealthMonitor.isCompromised.not())
    }

    @Test
    fun `observeTopRated falls back to the RAM buffer when Room is corrupt and recreate also fails`() =
        runTest {
            /* Room's flow is broken, and recovery has no fix either -- e.g. the file can't be deleted. */
            every { topRatedDao.observeAll() } returns flow<List<TopRatedMovieEntity>> {
                throw SQLiteDatabaseCorruptException("corrupt")
            }
            every { appDatabaseProvider.recreate() } throws IllegalStateException("delete failed")
            coEvery { homeScreenApi.getTopRatedMovie() } returns Response.success(topRatedDto())

            /* First collection trips the health monitor instead of hanging/crashing. */
            repo.observeTopRated().first()

            assertTrue(storageHealthMonitor.isCompromised)

            /* Once compromised, refresh() writes straight to RAM and observeTopRated() serves from it. */
            repo.refreshTopRatedApi(1)
            val resource = repo.observeTopRated().first { it !is Resource.Loading }

            assertTrue(resource is Resource.Success)
            assertEquals(1, (resource as Resource.Success).data.results.first().id)
            coVerify(exactly = 0) { topRatedDao.addDataToCache(any()) }
        }

    @Test
    fun `getById reads from Room when storage is healthy`() = runTest {
        coEvery { topRatedDao.findById(1) } returns topRatedEntity()

        val result = repo.getById(1)

        assertEquals(1, result?.id)
        assertEquals("Inception", result?.title)
    }

    @Test
    fun `getById returns null when Room is healthy but has no matching row`() = runTest {
        coEvery { topRatedDao.findById(404) } returns null

        val result = repo.getById(404)

        assertNull(result)
    }

    @Test
    fun `getById falls back to the RAM buffer when Room throws`() = runTest {
        coEvery { topRatedDao.findById(1) } throws SQLiteFullException("disk full")
        coEvery { homeScreenApi.getTopRatedMovie() } returns Response.success(topRatedDto())

        /* First call: Room throws, flips the monitor, RAM buffer is still empty. */
        val beforeRefresh = repo.getById(1)
        /* A fail-safe refresh (already compromised) populates the RAM buffer. */
        repo.refreshTopRatedApi(1)
        val afterRefresh = repo.getById(1)

        assertNull(beforeRefresh)
        assertTrue(storageHealthMonitor.isCompromised)
        assertEquals(1, afterRefresh?.id)
    }
}
