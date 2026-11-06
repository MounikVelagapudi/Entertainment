package com.example.entertainment.home.trendingMovies.domain

import com.example.entertainment.core.domain.NetworkError
import com.example.entertainment.core.domain.NetworkFailure
import com.example.entertainment.core.domain.Result
import com.example.entertainment.testdata.aTrendingPage
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TrendingUseCaseTest {

    private val trending = TrendingUseCaseBuilder()

    @Test
    fun `invoke returns Result Success with repo data when repo succeeds`() = runTest {
        val page = aTrendingPage()
        val useCase = trending.withRefreshResult(Result.Success(page)).build()

        val result = useCase.refresh()

        assertTrue(result is Result.Success)
        assertEquals(page, (result as Result.Success).data)
    }

    @Test
    fun `invoke returns Result Error when repo returns error`() = runTest {
        val useCase = trending.withRefreshResult(Result.Error(NetworkError.NoInternetError)).build()

        val result = useCase.refresh()

        assertTrue(result is Result.Error)
        assertEquals(NetworkError.NoInternetError, (result as Result.Error).error)
    }

    @Test
    fun `invoke returns Result Failure when repo returns failure`() = runTest {
        val useCase = trending.withRefreshResult(Result.Failure(NetworkFailure.IOFailure)).build()

        val result = useCase.refresh()

        assertTrue(result is Result.Failure)
        assertEquals(NetworkFailure.IOFailure, (result as Result.Failure).failure)
    }

    @Test
    fun `invoke calls repo getTrendingMovies exactly once`() = runTest {
        val useCase = trending.withRefreshResult(Result.Success(aTrendingPage())).build()

        useCase.refresh()

        coVerify(exactly = 1) { trending.trendingRepo.refreshTrendingMovie() }
    }

    @Test
    fun `invoke returns the exact same success instance from repo without modification`() = runTest {
        val page = aTrendingPage()
        val useCase = trending.withRefreshResult(Result.Success(page)).build()

        val result = useCase.refresh() as Result.Success

        assertSame(page, result.data)
    }
}
