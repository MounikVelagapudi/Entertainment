package com.example.entertainment.home.trendingMovies.domain

import com.example.entertainment.core.domain.NetworkError
import com.example.entertainment.core.domain.NetworkFailure
import com.example.entertainment.core.domain.Result
import com.example.trending.domain.TrendingMovie
import com.example.trending.domain.TrendingMoviesDomainModel
import com.example.trending.domain.TrendingRepo
import com.example.trending.domain.TrendingUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TrendingUseCaseTest {

    private val trendingRepo = mockk<TrendingRepo>()
    private val trendingUseCase = TrendingUseCase(trendingRepo)

    private fun trendingMoviesDomainModel() = TrendingMoviesDomainModel(
        page = 1,
        results = listOf(
            TrendingMovie(
                adult = false,
                backdropPath = "/backdrop.jpg",
                genreIds = listOf(28, 12),
                id = 1,
                mediaType = "movie",
                originalLanguage = "en",
                originalTitle = "Inception",
                overview = "A thief who steals secrets.",
                popularity = 123.4,
                posterPath = "/poster.jpg",
                releaseDate = "2010-07-16",
                title = "Inception",
                video = false,
                voteAverage = 8.8,
                voteCount = 30000
            )
        ),
        totalPages = 1,
        totalResults = 1
    )

    @Test
    fun `invoke returns Result Success with repo data when repo succeeds`() = runTest {
        val domainModel = trendingMoviesDomainModel()
        coEvery { trendingRepo.refreshTrendingMovie() } returns Result.Success(domainModel)

        val result = trendingUseCase.refresh()

        assertTrue(result is Result.Success)
        assertEquals(domainModel, (result as Result.Success).data)
    }

    @Test
    fun `invoke returns Result Error when repo returns error`() = runTest {
        coEvery { trendingRepo.refreshTrendingMovie() } returns Result.Error(NetworkError.NoInternetError)

        val result = trendingUseCase.refresh()

        assertTrue(result is Result.Error)
        assertEquals(NetworkError.NoInternetError, (result as Result.Error).error)
    }

    @Test
    fun `invoke returns Result Failure when repo returns failure`() = runTest {
        coEvery { trendingRepo.refreshTrendingMovie() } returns Result.Failure(NetworkFailure.IOFailure)

        val result = trendingUseCase.refresh()

        assertTrue(result is Result.Failure)
        assertEquals(NetworkFailure.IOFailure, (result as Result.Failure).failure)
    }

    @Test
    fun `invoke calls repo getTrendingMovies exactly once`() = runTest {
        coEvery { trendingRepo.refreshTrendingMovie() } returns Result.Success(trendingMoviesDomainModel())

        trendingUseCase.refresh()

        coVerify(exactly = 1) { trendingRepo.refreshTrendingMovie() }
    }

    @Test
    fun `invoke returns the exact same success instance from repo without modification`() = runTest {
        val domainModel = trendingMoviesDomainModel()
        coEvery { trendingRepo.refreshTrendingMovie() } returns Result.Success(domainModel)

        val result = trendingUseCase.refresh() as Result.Success

        assertSame(domainModel, result.data)
    }
}
