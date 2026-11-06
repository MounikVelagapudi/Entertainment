package com.example.popular.data

import com.example.entertainment.core.data.local.popular.PopularMovieEntity
import com.example.popular.testdata.aPopularPageDto
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the specific bug that was fixed: scrolling to the next page used to
 * restart sortOrder at 0 for every page, colliding with rows already cached
 * from earlier pages instead of appending after them. writeToRoom() in
 * OfflineFirstCache now reads dao.count() as an offset before assigning
 * sortOrder -- this asserts that offset is actually threaded through end to
 * end via the real repo, not just the DAO in isolation.
 */
class PopularMoviesRepoImplTest {

    private val given = PopularMoviesRepoBuilder()
    private val repo = given.build()

    @Test
    fun `second page's sortOrder continues from Room's current row count instead of restarting at 0`() = runTest {
        val writtenBatches = mutableListOf<List<PopularMovieEntity>>()

        /* Page 1: nothing cached yet. */
        given.withCachedCount(0).withNetworkPage(page = 1, dto = aPopularPageDto(page = 1, ids = listOf(1, 2)))
        repo.refreshPopularMovies(1)

        /* Page 2: Room now has page 1's 2 rows. */
        given.withCachedCount(2).withNetworkPage(page = 2, dto = aPopularPageDto(page = 2, ids = listOf(3, 4)))
        repo.refreshPopularMovies(2)

        coVerify(exactly = 2) { given.popularMoviesDao.addDataToCache(capture(writtenBatches)) }

        assertEquals(listOf(1, 2), writtenBatches[0].map { it.id })
        assertEquals(listOf(0, 1), writtenBatches[0].map { it.sortOrder })

        assertEquals(listOf(3, 4), writtenBatches[1].map { it.id })
        assertEquals(listOf(2, 3), writtenBatches[1].map { it.sortOrder })

        /* Neither call clears the table, so page 1's rows survive page 2. */
        coVerify(exactly = 0) { given.popularMoviesDao.clearAll() }
    }
}
