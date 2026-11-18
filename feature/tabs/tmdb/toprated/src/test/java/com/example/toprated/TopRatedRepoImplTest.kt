package com.example.toprated

import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteFullException
import com.example.entertainment.core.domain.Resource
import com.example.entertainment.core.domain.Result
import com.example.toprated.testdata.aTopRatedDto
import com.example.toprated.testdata.aTopRatedEntity
import io.mockk.coVerify
import java.net.UnknownHostException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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

    private val given = TopRatedRepoBuilder()

    @Test
    fun `refresh writes the mapped network response into Room on success`() = runTest {
        val repo = given.withNetworkPage(aTopRatedDto()).withCachedCount(0).build()

        val result = repo.refreshTopRatedApi(1)

        assertTrue(result is Result.Success)
        coVerify(exactly = 1) {
            given.topRatedDao.addDataToCache(match { entities ->
                entities.size == 1 && entities[0].id == 1 && entities[0].sortOrder == 0
            })
        }
        assertFalse(given.storageHealthMonitor.isCompromised)
    }

    @Test
    fun `refresh leaves the Room cache untouched when the network call fails`() = runTest {
        /* safeCall maps this to Result.Error before the mapper/DAO. */
        val repo = given.withNetworkThrowing(UnknownHostException()).build()

        val result = repo.refreshTopRatedApi(1)

        assertTrue(result is Result.Error)
        coVerify(exactly = 0) { given.topRatedDao.addDataToCache(any()) }
    }

    @Test
    fun `observeTopRated emits Success mapped from whatever Room currently has`() = runTest {
        val repo = given.withCachedRows(aTopRatedEntity()).build()

        val resource = repo.observeTopRated().first()

        assertTrue(resource is Resource.Success)
        assertEquals(1, (resource as Resource.Success).data.results.size)
        assertEquals("Inception", resource.data.results[0].title)
    }

    @Test
    fun `observeTopRated emits Loading when Room is empty and there's no refresh error`() = runTest {
        val repo = given.withCachedRows().build()

        val resource = repo.observeTopRated().first()

        assertEquals(Resource.Loading, resource)
    }

    @Test
    fun `observeTopRated emits Error when Room is empty and the last refresh failed`() = runTest {
        /* First launch, no successful sync yet. */
        val repo = given.withCachedRows().withNetworkThrowing(UnknownHostException()).build()

        repo.refreshTopRatedApi(1)
        val resource = repo.observeTopRated().first()

        assertEquals(Resource.Error("NoInternetError"), resource)
    }

    @Test
    fun `refresh reports a Room write failure to the health monitor and falls back to the RAM buffer`() = runTest {
        /* Network works, persisting is what's broken, e.g. disk full. */
        val repo = given
            .withNetworkPage(aTopRatedDto())
            .withCachedCount(0)
            .withCacheWriteThrowing(SQLiteFullException("disk full"))
            .build()

        val result = repo.refreshTopRatedApi(1)

        /* Still a success for the caller, data reached the user via RAM. */
        assertTrue(result is Result.Success)
        assertTrue(given.storageHealthMonitor.isCompromised)
        assertEquals(1, repo.getById(1)?.id)
    }

    @Test
    fun `observeTopRated self-heals by recreating Room when a corrupt db recovers cleanly`() = runTest {
        val repo = given
            .withCacheObserveThrowing(SQLiteDatabaseCorruptException("corrupt"))
            .withRecreatedDatabaseHolding(aTopRatedEntity())
            .build()

        val resource = repo.observeTopRated().first { it !is Resource.Loading }

        /* Recovered cleanly, so Room stays the source of truth and RAM is never used. */
        assertTrue(resource is Resource.Success)
        assertEquals(1, (resource as Resource.Success).data.results.first().id)
        assertFalse(given.storageHealthMonitor.isCompromised)
    }

    @Test
    fun `observeTopRated falls back to the RAM buffer when Room is corrupt and recreate also fails`() = runTest {
        /* Broken flow and no fix, e.g. the db file can't be deleted. */
        val repo = given
            .withCacheObserveThrowing(SQLiteDatabaseCorruptException("corrupt"))
            .withRecreateThrowing(IllegalStateException("delete failed"))
            .withNetworkPage(aTopRatedDto())
            .build()

        /* First collection trips the health monitor instead of hanging or crashing. */
        repo.observeTopRated().first()
        assertTrue(given.storageHealthMonitor.isCompromised)

        /* Once compromised, refresh() writes to RAM and observeTopRated() serves from it. */
        repo.refreshTopRatedApi(1)
        val resource = repo.observeTopRated().first { it !is Resource.Loading }

        assertTrue(resource is Resource.Success)
        assertEquals(1, (resource as Resource.Success).data.results.first().id)
        coVerify(exactly = 0) { given.topRatedDao.addDataToCache(any()) }
    }

    @Test
    fun `getById reads from Room when storage is healthy`() = runTest {
        val repo = given.withCachedRow(id = 1, row = aTopRatedEntity()).build()

        val result = repo.getById(1)

        assertEquals(1, result?.id)
        assertEquals("Inception", result?.title)
    }

    @Test
    fun `getById returns null when Room is healthy but has no matching row`() = runTest {
        val repo = given.withCachedRow(id = 404, row = null).build()

        assertNull(repo.getById(404))
    }

    @Test
    fun `getById falls back to the RAM buffer when Room throws`() = runTest {
        val repo = given
            .withCacheReadThrowing(id = 1, error = SQLiteFullException("disk full"))
            .withNetworkPage(aTopRatedDto())
            .build()

        /* Room throws and flips the monitor; RAM is still empty. */
        val beforeRefresh = repo.getById(1)
        /* Already compromised, so this refresh fills the RAM buffer. */
        repo.refreshTopRatedApi(1)
        val afterRefresh = repo.getById(1)

        assertNull(beforeRefresh)
        assertTrue(given.storageHealthMonitor.isCompromised)
        assertEquals(1, afterRefresh?.id)
    }
}
