package com.example.entertainment.home

import com.example.entertainment.MainDispatcherRule
import com.example.entertainment.core.presentation.reusable_components.events.MediaUiEvents
import com.example.entertainment.core.domain.NetworkError
import com.example.entertainment.core.domain.Resource
import com.example.entertainment.core.domain.Result
import com.example.entertainment.home.omdb.OmDbUiState
import com.example.popular.domain.GetPopularMovieUseCase
import com.example.popular.domain.PopularMovie
import com.example.popular.domain.PopularMoviesDomainModel
import com.example.toprated.domain.TopRatedDomainModel
import com.example.toprated.domain.TopRatedMovie
import com.example.toprated.domain.TopRatedUseCase
import com.example.trending.domain.TrendingMovie
import com.example.trending.domain.TrendingMoviesDomainModel
import com.example.trending.domain.TrendingUseCase
import androidx.lifecycle.SavedStateHandle
import androidx.work.WorkManager
import app.cash.turbine.test
import com.example.presentation.presentation.HomeViewModel
import com.example.entertainment.core.presentation.reusable_components.MediaResults
import com.example.presentation.presentation.MediaSectionUiState
import com.example.presentation.presentation.toMediaUiModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {

    /* TestDispatcher swap for viewModelScope (needs Dispatchers.Main), which the JVM test runner doesn't have. */
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /* Mocked -- these tests only care about HomeViewModel's own logic, not the use cases' internals. */
    private val trendingUseCase = mockk<TrendingUseCase>()
    private val popularMovieUseCase = mockk<GetPopularMovieUseCase>()
    private val topRatedUseCase = mockk<TopRatedUseCase>()

    /* Relaxed -- these tests don't care about the periodic sync schedule, just that
       init{} calling into it doesn't blow up with no stub configured. */
    private val workManager = mockk<WorkManager>(relaxed = true)

    init {
        /* observe() is collected eagerly at construction, so every test needs a default even if it
           doesn't care about TopRated -- tests that do care override this before calling viewModel(). */
        every { topRatedUseCase.observe() } returns MutableStateFlow(Resource.Loading)
        coEvery { topRatedUseCase.refresh(any()) } returns Result.Success(topRatedDomainModel())

        /* Same idea for Popular. */
        every { popularMovieUseCase.observe() } returns MutableStateFlow(Resource.Loading)
        coEvery { popularMovieUseCase.refresh(any()) } returns Result.Success(popularDomainModel())

        /* Same idea for Trending. */
        every { trendingUseCase.observe() } returns MutableStateFlow(Resource.Loading)
        coEvery { trendingUseCase.refresh() } returns Result.Success(trendingDomainModel())
    }

    private fun viewModel() = HomeViewModel(
        trendingUseCase = trendingUseCase,
        popularMovieUseCase = popularMovieUseCase,
        topRatedUseCase = topRatedUseCase,
        workManager = workManager,
        savedStateHandle = SavedStateHandle(),
    )

    private fun mediaResults(id: Int = 1) = MediaResults(
        title = "Inception",
        releaseDate = "2010-07-16",
        imageUrl = "https://example.com/inception.jpg",
        id = id,
        ratings = 8.8
    )

    /* Distinct id per section, so a bug that mixes up which section's data landed where would actually show. */
    private fun trendingDomainModel(id: Int = 1) = TrendingMoviesDomainModel(
        page = 1,
        results = listOf(
            TrendingMovie(
                adult = false,
                backdropPath = "/backdrop.jpg",
                genreIds = listOf(28),
                id = id,
                mediaType = "movie",
                originalLanguage = "en",
                originalTitle = "Trending Movie",
                overview = "A trending movie.",
                popularity = 10.0,
                posterPath = "/trending_poster.jpg",
                releaseDate = "2020-01-01",
                title = "Trending Movie",
                video = false,
                voteAverage = 7.0,
                voteCount = 100
            )
        ),
        totalPages = 1,
        totalResults = 1
    )

    private fun popularDomainModel(id: Int = 2) = PopularMoviesDomainModel(
        page = 1,
        results = listOf(
            PopularMovie(
                adult = false,
                backdropPath = "/backdrop.jpg",
                genreIds = listOf(12),
                id = id,
                originalLanguage = "en",
                originalTitle = "Popular Movie",
                overview = "A popular movie.",
                popularity = 20.0,
                posterPath = "/popular_poster.jpg",
                releaseDate = "2021-02-02",
                title = "Popular Movie",
                video = false,
                voteAverage = 8.0,
                voteCount = 200
            )
        ),
        totalPages = 1,
        totalResults = 1
    )

    private fun topRatedDomainModel(id: Int = 3) = TopRatedDomainModel(
        page = 1,
        results = listOf(
            TopRatedMovie(
                id = id,
                title = "Top Rated Movie",
                originalTitle = "Top Rated Movie",
                posterPath = "/top_rated_poster.jpg",
                backdropPath = "/backdrop.jpg",
                releaseDate = "2019-03-03",
                originalLanguage = "en",
                popularity = 30.0,
                voteAverage = 9.0,
                voteCount = 300,
                isAdult = false,
                isVideo = false,
                genreIds = listOf(18)
            )
        ),
        totalPages = 1,
        totalResults = 1
    )

    @Test
    fun `onEvent OnTapOfDisclosure sets disclosures to the tapped media`() {
        val viewModel = viewModel()
        val media = mediaResults()

        viewModel.onEvent(MediaUiEvents.OnTapOfDisclosure(media))

        assertEquals(media, viewModel.disclosures.value)
    }

    @Test
    fun `onEvent OnTapOfMovieCard does not change disclosures`() {
        val viewModel = viewModel()

        /* OnTapOfMovieCard is a no-op here -- navigation is handled by the caller. */
        viewModel.onEvent(MediaUiEvents.OnTapOfMovieCard(mediaResults()))

        assertNull(viewModel.disclosures.value)
    }

    @Test
    fun `onEvent OnTapOfRating emits a toast message with the tapped id`() = runTest {
        val viewModel = viewModel()

        /* toastMessage has no replay, so the collector must subscribe before the event fires. */
        viewModel.toastMessage.test {
            viewModel.onEvent(MediaUiEvents.OnTapOfRating(id = 4.5))

            assertEquals("Ratings tapped -- 4.5", awaitItem())
        }
    }

    @Test
    fun `dismissInfoSheet clears a previously set disclosure back to null`() {
        val viewModel = viewModel()
        viewModel.onEvent(MediaUiEvents.OnTapOfDisclosure(mediaResults()))
        assertEquals(mediaResults(), viewModel.disclosures.value)

        viewModel.dismissInfoSheet()

        assertNull(viewModel.disclosures.value)
    }

    @Test
    fun `dismissInfoSheet on an already-null disclosure is a no-op`() {
        val viewModel = viewModel()

        viewModel.dismissInfoSheet()

        assertNull(viewModel.disclosures.value)
    }

    @Test
    fun `uiState starts as Loading for every section before loadAll is called`() {
        val viewModel = viewModel()

        /* stateIn's initialValue is available synchronously, even with zero collectors. */
        val initialState = viewModel.uiState.value

        assertEquals(OmDbUiState.Loading, initialState.omdbUiState)
        assertEquals(MediaSectionUiState.Loading, initialState.tmdbUiState)
        assertEquals(MediaSectionUiState.Loading, initialState.popularMovieUiState)
        assertEquals(MediaSectionUiState.Loading, initialState.topRatedMoviesUiState)
    }

    @Test
    fun `loadAll updates uiState to Success for every section when all use cases succeed`() =
        runTest {
            val trendingModel = trendingDomainModel()
            val popularModel = popularDomainModel()
            val topRatedModel = topRatedDomainModel()
            coEvery { trendingUseCase.refresh() } returns Result.Success(trendingModel)
            every { trendingUseCase.observe() } returns MutableStateFlow(Resource.Success(trendingModel))
            every { popularMovieUseCase.observe() } returns MutableStateFlow(
                Resource.Success(popularModel)
            )
            every { topRatedUseCase.observe() } returns MutableStateFlow(
                Resource.Success(
                    topRatedModel
                )
            )
            val viewModel = viewModel()

            viewModel.uiState.test {
                /* Subscribing here is what starts combine() collecting the section flows. */
                awaitItem()

                viewModel.loadAll()
                testScheduler.advanceUntilIdle()

                /* Sections can flip Loading -> Success independently and combine() re-emits each
                   time, so grab only the final settled state. */
                val finalState = expectMostRecentItem()

                /* loadAll() never writes omdbUiState, so it stays at its initial value. */
                assertEquals(OmDbUiState.Loading, finalState.omdbUiState)

                assertEquals(
                    MediaSectionUiState.Success(trendingModel.toMediaUiModel()),
                    finalState.tmdbUiState
                )
                assertEquals(
                    MediaSectionUiState.Success(popularModel.toMediaUiModel()),
                    finalState.popularMovieUiState
                )
                assertEquals(
                    MediaSectionUiState.Success(topRatedModel.toMediaUiModel()),
                    finalState.topRatedMoviesUiState
                )

                cancelAndIgnoreRemainingEvents()
            }
        }


    @Test
    fun `topRated stays on the cached Success state when a refresh fails (offline-first)`() =
        runTest {
            /* Cache already has data from a previous refresh -- that stays visible even though this one fails. */
            val topRatedModel = topRatedDomainModel()
            every { topRatedUseCase.observe() } returns MutableStateFlow(
                Resource.Success(
                    topRatedModel
                )
            )
            coEvery { topRatedUseCase.refresh(any()) } returns Result.Error(NetworkError.NoInternetError)
            coEvery { trendingUseCase.refresh() } returns Result.Success(trendingDomainModel())
            every { popularMovieUseCase.observe() } returns MutableStateFlow(
                Resource.Success(popularDomainModel())
            )
            val viewModel = viewModel()

            viewModel.uiState.test {
                awaitItem()
                viewModel.loadAll()
                testScheduler.advanceUntilIdle()
                val finalState = expectMostRecentItem()

                assertEquals(
                    MediaSectionUiState.Success(topRatedModel.toMediaUiModel()),
                    finalState.topRatedMoviesUiState
                )

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `topRated maps a Resource Error from the use case straight through`() =
        runTest {
            every { topRatedUseCase.observe() } returns MutableStateFlow(Resource.Error("NoInternetError"))
            coEvery { trendingUseCase.refresh() } returns Result.Success(trendingDomainModel())
            every { popularMovieUseCase.observe() } returns MutableStateFlow(
                Resource.Success(popularDomainModel())
            )
            val viewModel = viewModel()

            viewModel.uiState.test {
                awaitItem()
                viewModel.loadAll()
                testScheduler.advanceUntilIdle()
                val finalState = expectMostRecentItem()

                assertEquals(
                    MediaSectionUiState.Error("NoInternetError"),
                    finalState.topRatedMoviesUiState
                )

                cancelAndIgnoreRemainingEvents()
            }
        }
}
