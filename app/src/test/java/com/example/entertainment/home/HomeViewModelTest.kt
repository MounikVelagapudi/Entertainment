package com.example.entertainment.home

import app.cash.turbine.test
import com.example.entertainment.MainDispatcherRule
import com.example.entertainment.core.domain.NetworkError
import com.example.entertainment.core.domain.Resource
import com.example.entertainment.core.domain.Result
import com.example.entertainment.core.presentation.reusable_components.events.MediaUiEvents
import com.example.entertainment.home.omdb.OmDbUiState
import com.example.entertainment.testdata.aMediaResults
import com.example.entertainment.testdata.aPopularPage
import com.example.entertainment.testdata.aTopRatedPage
import com.example.entertainment.testdata.aTrendingPage
import com.example.presentation.presentation.HomeCombinedUiState
import com.example.presentation.presentation.HomeViewModel
import com.example.presentation.presentation.MediaSectionUiState
import com.example.presentation.presentation.toMediaUiModel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val home = HomeViewModelBuilder()

    @Test
    fun `onEvent OnTapOfDisclosure sets disclosures to the tapped media`() {
        val viewModel = home.build()
        val media = aMediaResults()

        viewModel.onEvent(MediaUiEvents.OnTapOfDisclosure(media))

        assertEquals(media, viewModel.disclosures.value)
    }

    @Test
    fun `onEvent OnTapOfMovieCard does not change disclosures`() {
        val viewModel = home.build()

        /* No-op here, the caller handles navigation. */
        viewModel.onEvent(MediaUiEvents.OnTapOfMovieCard(aMediaResults()))

        assertNull(viewModel.disclosures.value)
    }

    @Test
    fun `onEvent OnTapOfRating emits a toast message with the tapped id`() = runTest {
        val viewModel = home.build()

        /* No replay, so subscribe before the event fires. */
        viewModel.toastMessage.test {
            viewModel.onEvent(MediaUiEvents.OnTapOfRating(id = 4.5))

            assertEquals("Ratings tapped -- 4.5", awaitItem())
        }
    }

    @Test
    fun `dismissInfoSheet clears a previously set disclosure back to null`() {
        val viewModel = home.build()
        viewModel.onEvent(MediaUiEvents.OnTapOfDisclosure(aMediaResults()))
        assertEquals(aMediaResults(), viewModel.disclosures.value)

        viewModel.dismissInfoSheet()

        assertNull(viewModel.disclosures.value)
    }

    @Test
    fun `dismissInfoSheet on an already-null disclosure is a no-op`() {
        val viewModel = home.build()

        viewModel.dismissInfoSheet()

        assertNull(viewModel.disclosures.value)
    }

    @Test
    fun `uiState starts as Loading for every section before loadAll is called`() {
        val viewModel = home.build()

        /* stateIn's initialValue is there synchronously, even with no collectors. */
        val initialState = viewModel.uiState.value

        assertEquals(OmDbUiState.Loading, initialState.omdbUiState)
        assertEquals(MediaSectionUiState.Loading, initialState.tmdbUiState)
        assertEquals(MediaSectionUiState.Loading, initialState.popularMovieUiState)
        assertEquals(MediaSectionUiState.Loading, initialState.topRatedMoviesUiState)
    }

    @Test
    fun `loadAll updates uiState to Success for every section when all use cases succeed`() = runTest {
        val trending = aTrendingPage()
        val popular = aPopularPage()
        val topRated = aTopRatedPage()
        val viewModel = home
            .withTrending(Resource.Success(trending))
            .withTrendingRefresh(Result.Success(trending))
            .withPopular(Resource.Success(popular))
            .withTopRated(Resource.Success(topRated))
            .build()

        val finalState = settledStateAfterLoadAll(viewModel)

        /* loadAll() never writes omdbUiState. */
        assertEquals(OmDbUiState.Loading, finalState.omdbUiState)
        assertEquals(MediaSectionUiState.Success(trending.toMediaUiModel()), finalState.tmdbUiState)
        assertEquals(MediaSectionUiState.Success(popular.toMediaUiModel()), finalState.popularMovieUiState)
        assertEquals(MediaSectionUiState.Success(topRated.toMediaUiModel()), finalState.topRatedMoviesUiState)
    }

    @Test
    fun `topRated stays on the cached Success state when a refresh fails (offline-first)`() = runTest {
        /* Cached data from an earlier refresh stays visible even though this one fails. */
        val topRated = aTopRatedPage()
        val viewModel = home
            .withTopRated(Resource.Success(topRated))
            .withTopRatedRefresh(Result.Error(NetworkError.NoInternetError))
            .withPopular(Resource.Success(aPopularPage()))
            .build()

        val finalState = settledStateAfterLoadAll(viewModel)

        assertEquals(MediaSectionUiState.Success(topRated.toMediaUiModel()), finalState.topRatedMoviesUiState)
    }

    @Test
    fun `topRated maps a Resource Error from the use case straight through`() = runTest {
        val viewModel = home
            .withTopRated(Resource.Error("NoInternetError"))
            .withPopular(Resource.Success(aPopularPage()))
            .build()

        val finalState = settledStateAfterLoadAll(viewModel)

        assertEquals(MediaSectionUiState.Error("NoInternetError"), finalState.topRatedMoviesUiState)
    }

    /* Subscribing starts combine(); sections settle independently, so keep only the last state. */
    private suspend fun TestScope.settledStateAfterLoadAll(viewModel: HomeViewModel): HomeCombinedUiState {
        lateinit var finalState: HomeCombinedUiState
        viewModel.uiState.test {
            awaitItem()
            viewModel.loadAll()
            testScheduler.advanceUntilIdle()
            finalState = expectMostRecentItem()
            cancelAndIgnoreRemainingEvents()
        }
        return finalState
    }
}
