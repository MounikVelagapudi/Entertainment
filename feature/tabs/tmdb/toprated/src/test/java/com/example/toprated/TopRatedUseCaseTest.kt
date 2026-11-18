package com.example.toprated

import com.example.entertainment.core.domain.NetworkError
import com.example.entertainment.core.domain.Resource
import com.example.entertainment.core.domain.Result
import com.example.toprated.testdata.aTopRatedMovie
import com.example.toprated.testdata.aTopRatedPage
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/* observe/refresh/getById are thin pass-throughs; the offline-first logic is in TopRatedRepoImplTest. */
class TopRatedUseCaseTest {

    private val topRated = TopRatedUseCaseBuilder()

    @Test
    fun `observe emits whatever the repo's unified Resource flow emits`() = runTest {
        val resource = Resource.Success(aTopRatedPage())
        val useCase = topRated.withObserved(resource).build()

        val emissions = useCase.observe().toList()

        assertEquals(listOf(resource), emissions)
    }

    @Test
    fun `refresh returns Success when the repo sync succeeds`() = runTest {
        val useCase = topRated.withRefreshResult(page = 1, result = Result.Success(aTopRatedPage())).build()

        val result = useCase.refresh(1)

        assertTrue(result is Result.Success)
    }

    @Test
    fun `refresh surfaces the repo's failure as-is`() = runTest {
        val useCase = topRated
            .withRefreshResult(page = 1, result = Result.Failure(NetworkError.NoInternetError))
            .build()

        val result = useCase.refresh(1)

        assertTrue(result is Result.Failure)
        assertEquals(NetworkError.NoInternetError, (result as Result.Failure).failure)
    }

    @Test
    fun `getById surfaces whatever the repo returns`() = runTest {
        val movie = aTopRatedMovie()
        val useCase = topRated.withMovieById(id = 1, movie = movie).build()

        assertEquals(movie, useCase.getById(1))
    }

    @Test
    fun `getById surfaces null when the repo has no match`() = runTest {
        val useCase = topRated.withMovieById(id = 999, movie = null).build()

        assertNull(useCase.getById(999))
    }
}
