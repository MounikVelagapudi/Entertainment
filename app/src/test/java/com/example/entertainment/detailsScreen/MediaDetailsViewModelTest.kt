package com.example.entertainment.detailsScreen

import app.cash.turbine.test
import com.example.entertainment.MainDispatcherRule
import com.example.entertainment.core.presentation.reusable_components.MediaResults
import com.example.toprated.domain.TopRatedMovie
import com.example.toprated.domain.TopRatedUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MediaDetailsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val topRatedUseCase = mockk<TopRatedUseCase>()

    private fun viewModel() = MediaDetailsViewModel(
        topRatedUseCase = topRatedUseCase,
    )

    private fun topRatedMovie(id: Int) = TopRatedMovie(
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
        genreIds = listOf(28, 12)
    )

    @Test
    fun `loadCachedData returns Success when TopRated has a match`() = runTest {
        coEvery { topRatedUseCase.getById(2) } returns topRatedMovie(id = 2)
        val viewModel = viewModel()

        viewModel.mediaDetailsUiState.test {
            awaitItem() // initial Loading state
            viewModel.loadCachedData(2)
            val result = awaitItem()

            assertEquals(
                MediaDetailsUiState.Success(
                    MediaResults(
                        title = "Inception",
                        releaseDate = "2010-07-16",
                        imageUrl = "/poster.jpg",
                        id = 2,
                        ratings = 8.8,
                    )
                ),
                result,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadCachedData surfaces an Error when neither source has a match`() = runTest {
        coEvery { topRatedUseCase.getById(404) } returns null
        val viewModel = viewModel()

        viewModel.mediaDetailsUiState.test {
            awaitItem() // initial Loading state
            viewModel.loadCachedData(404)
            val result = awaitItem()

            assertEquals(MediaDetailsUiState.Error("Failure"), result)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
