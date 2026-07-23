package com.example.entertainment.home.topRatedMovies

import com.example.entertainment.core.domain.NetworkError
import com.example.entertainment.core.domain.Result
import com.example.entertainment.core.domain.TRPError
import com.example.entertainment.home.topRated.topRated.domain.TopRatedDomainModel
import com.example.entertainment.home.topRated.topRated.domain.TopRatedMovie
import com.example.entertainment.home.topRated.topRated.domain.TopRatedRepo
import com.example.entertainment.home.topRated.topRated.domain.TopRatedUseCase
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test


class TopRatedUseCaseTest {

    private val topRatedRepo = mockk<TopRatedRepo>()

    private val topRatedUseCase = TopRatedUseCase(topRatedRepo)

    private fun topRatedDomainModel() = TopRatedDomainModel(
        page = 1,
        results = listOf(
            TopRatedMovie(
                backdropPath = "/backdrop.jpg",
                genreIds = listOf(28, 12),
                id = 1,
                originalLanguage = "en",
                originalTitle = "Inception",
                popularity = 123.4,
                posterPath = "/poster.jpg",
                releaseDate = "2010-07-16",
                title = "Inception",
                voteAverage = 8.8,
                voteCount = 30000
            )
        ),
        totalPages = 1,
        totalResults = 1
    )

    @Test
    fun `test top rated usecase with mock data`() = runTest {

        // Arrange
        val domainModel = topRatedDomainModel()
        coEvery { topRatedRepo.getTopRatedMovies() } returns Result.Success(domainModel)

        // Act
        val result = topRatedUseCase()

        // Assert
        assertTrue(result is Result.Success)
        assertEquals(domainModel, (result as Result.Success).data)

    }

    @Test
    fun `test top rated usecase with failure data`() = runTest {

        //Arrange
        val domainModel = topRatedDomainModel()
        coEvery { topRatedRepo.getTopRatedMovies() }  returns Result.Failure(NetworkError.NoInternetError)

        //Act
        val result = topRatedUseCase()

        //Assert
        assertTrue(result is Result.Failure)
        assertEquals(NetworkError.NoInternetError, (result as Result.Failure).failure)
    }
}