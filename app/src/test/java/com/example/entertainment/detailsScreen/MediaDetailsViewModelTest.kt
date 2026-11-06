package com.example.entertainment.detailsScreen

import app.cash.turbine.test
import com.example.entertainment.MainDispatcherRule
import com.example.entertainment.testdata.aMediaResults
import com.example.entertainment.testdata.aTopRatedMovie
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MediaDetailsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val details = MediaDetailsViewModelBuilder()

    @Test
    fun `loadCachedData returns Success when TopRated has a match`() = runTest {
        val viewModel = details.withCachedTopRated(id = 2, movie = aTopRatedMovie(id = 2)).build()

        viewModel.mediaDetailsUiState.test {
            awaitItem() // initial Loading
            viewModel.loadCachedData(2)

            assertEquals(
                MediaDetailsUiState.Success(aMediaResults(id = 2, imageUrl = "/poster.jpg")),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadCachedData surfaces an Error when neither source has a match`() = runTest {
        val viewModel = details.withCachedTopRated(id = 404, movie = null).build()

        viewModel.mediaDetailsUiState.test {
            awaitItem() // initial Loading
            viewModel.loadCachedData(404)

            assertEquals(MediaDetailsUiState.Error("Failure"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
