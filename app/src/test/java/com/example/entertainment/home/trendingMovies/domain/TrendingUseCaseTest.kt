package com.example.entertainment.home.trendingMovies.domain

import com.example.entertainment.core.domain.NetworkError
import com.example.entertainment.core.domain.NetworkFailure
import com.example.entertainment.core.domain.Result
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
        // Arrange
        val domainModel = trendingMoviesDomainModel()
        coEvery { trendingRepo.getTrendingMovies() } returns Result.Success(domainModel)

        // Act
        val result = trendingUseCase.invoke()

        // Assert
        assertTrue(result is Result.Success)
        assertEquals(domainModel, (result as Result.Success).data)
    }

    @Test
    fun `invoke returns Result Error when repo returns error`() = runTest {
        // Arrange
        coEvery { trendingRepo.getTrendingMovies() } returns Result.Error(NetworkError.NoInternetError)

        // Act
        val result = trendingUseCase.invoke()

        // Assert
        assertTrue(result is Result.Error)
        assertEquals(NetworkError.NoInternetError, (result as Result.Error).error)
    }

    @Test
    fun `invoke returns Result Failure when repo returns failure`() = runTest {
        // Arrange
        coEvery { trendingRepo.getTrendingMovies() } returns Result.Failure(NetworkFailure.IOFailure)

        // Act
        val result = trendingUseCase.invoke()

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals(NetworkFailure.IOFailure, (result as Result.Failure).failure)
    }

    @Test
    fun `invoke calls repo getTrendingMovies exactly once`() = runTest {
        // Arrange
        coEvery { trendingRepo.getTrendingMovies() } returns Result.Success(trendingMoviesDomainModel())

        // Act
        trendingUseCase.invoke()

        // Assert
        coVerify(exactly = 1) { trendingRepo.getTrendingMovies() }
    }

    @Test
    fun `invoke returns the exact same success instance from repo without modification`() = runTest {
        // Arrange
        val domainModel = trendingMoviesDomainModel()
        coEvery { trendingRepo.getTrendingMovies() } returns Result.Success(domainModel)

        // Act
        val result = trendingUseCase.invoke() as Result.Success

        // Assert
        assertSame(domainModel, result.data)
    }
}
