package com.example.toprated

import com.example.entertainment.core.domain.NetworkError
import com.example.entertainment.core.domain.Resource
import com.example.entertainment.core.domain.Result
import com.example.toprated.domain.TopRatedDomainModel
import com.example.toprated.domain.TopRatedMovie
import com.example.toprated.domain.TopRatedRepo
import com.example.toprated.domain.TopRatedUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test


class TopRatedUseCaseTest {

    private val topRatedRepo = mockk<TopRatedRepo>()

    private val topRatedUseCase = TopRatedUseCase(topRatedRepo)

    private fun topRatedMovie(id: Int = 1) = TopRatedMovie(
        backdropPath = "/backdrop.jpg",
        genreIds = listOf(28, 12),
        id = id,
        originalLanguage = "en",
        originalTitle = "Inception",
        popularity = 123.4,
        posterPath = "/poster.jpg",
        releaseDate = "2010-07-16",
        title = "Inception",
        voteAverage = 8.8,
        voteCount = 30000,
        isAdult = false,
        isVideo = false
    )

    private fun topRatedDomainModel() = TopRatedDomainModel(
        page = 1,
        results = listOf(topRatedMovie()),
        totalPages = 1,
        totalResults = 1
    )

    /* observe()/refresh()/getById() are thin pass-throughs -- the offline-first logic
       itself lives in TopRatedRepoImpl and is covered by TopRatedRepoImplTest. */

    @Test
    fun `observe emits whatever the repo's unified Resource flow emits`() = runTest {
        val resource = Resource.Success(topRatedDomainModel())
        every { topRatedRepo.observeTopRated() } returns flowOf(resource)

        val emissions = topRatedUseCase.observe().toList()

        assertEquals(listOf(resource), emissions)
    }

    @Test
    fun `refresh returns Success when the repo sync succeeds`() = runTest {
        coEvery { topRatedRepo.refreshTopRatedApi(1) } returns Result.Success(topRatedDomainModel())

        val result = topRatedUseCase.refresh(1)

        assertTrue(result is Result.Success)
    }

    @Test
    fun `refresh surfaces the repo's failure as-is`() = runTest {
        coEvery { topRatedRepo.refreshTopRatedApi(1) } returns
            Result.Failure(NetworkError.NoInternetError)

        val result = topRatedUseCase.refresh(1)

        assertTrue(result is Result.Failure)
        assertEquals(NetworkError.NoInternetError, (result as Result.Failure).failure)
    }

    @Test
    fun `getById surfaces whatever the repo returns`() = runTest {
        val movie = topRatedMovie()
        coEvery { topRatedRepo.getById(1) } returns movie

        val result = topRatedUseCase.getById(1)

        assertEquals(movie, result)
    }

    @Test
    fun `getById surfaces null when the repo has no match`() = runTest {
        coEvery { topRatedRepo.getById(999) } returns null

        val result = topRatedUseCase.getById(999)

        assertEquals(null, result)
    }
}
