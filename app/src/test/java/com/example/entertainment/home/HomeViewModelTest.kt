package com.example.entertainment.home

import com.example.entertainment.MainDispatcherRule
import com.example.entertainment.MediaUiEvents
import com.example.entertainment.core.domain.NetworkError
import com.example.entertainment.core.domain.NetworkFailure
import com.example.entertainment.core.domain.Result
import com.example.entertainment.home.omdb.OmDbUiState
import com.example.popular.domain.GetPopularMovieUseCase
import com.example.popular.domain.PopularMovie
import com.example.popular.domain.PopularMoviesDomainModel
import com.example.toprated.domain.TopRatedDomainModel
import com.example.toprated.domain.TopRatedMovie
import com.example.toprated.domain.TopRatedUseCase
import com.example.entertainment.home.trendingMovies.domain.TrendingMovie
import com.example.entertainment.home.trendingMovies.domain.TrendingMoviesDomainModel
import com.example.entertainment.home.trendingMovies.domain.TrendingUseCase
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {

    // Swaps Dispatchers.Main for a TestDispatcher for the duration of each test.
    // Needed because HomeViewModel uses viewModelScope (= Dispatchers.Main) internally,
    // e.g. in the onEvent(OnTapOfRating) branch below.
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // The use cases and cache are mocked because these tests only care about
    // HomeViewModel's own logic (state updates, event handling), not what the
    // use cases do internally -- that's already covered by their own test classes.
    private val trendingUseCase = mockk<TrendingUseCase>()
    private val popularMovieUseCase = mockk<GetPopularMovieUseCase>()
    private val topRatedUseCase = mockk<TopRatedUseCase>()
    private val mediaResultsCache = mockk<MediaResultsCache>()

    private fun viewModel() = HomeViewModel(
        trendingUseCase = trendingUseCase,
        popularMovieUseCase = popularMovieUseCase,
        topRatedUseCase = topRatedUseCase,
        mediaResultsCache = mediaResultsCache,
        ioDispatcher = StandardTestDispatcher()
    )

    private fun mediaResults(id: Int = 1) = MediaResults(
        title = "Inception",
        releaseDate = "2010-07-16",
        imageUrl = "https://example.com/inception.jpg",
        id = id,
        ratings = 8.8
    )

    // One domain model per section, each with a distinct id so a bug that mixed up
    // which section's data ended up where would actually be visible in an assertion.
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
        // Arrange
        val viewModel = viewModel()
        val media = mediaResults()

        // Act
        viewModel.onEvent(MediaUiEvents.OnTapOfDisclosure(media))

        // Assert
        assertEquals(media, viewModel.disclosures.value)
    }

    @Test
    fun `onEvent OnTapOfMovieCard does not change disclosures`() {
        // Arrange
        val viewModel = viewModel()

        // Act
        // OnTapOfMovieCard is intentionally a no-op in the ViewModel -- navigation is
        // handled by the caller -- so disclosures should stay at its initial value (null).
        viewModel.onEvent(MediaUiEvents.OnTapOfMovieCard(mediaResults()))

        // Assert
        assertNull(viewModel.disclosures.value)
    }

    @Test
    fun `onEvent OnTapOfRating emits a toast message with the tapped id`() = runTest {
        // Arrange
        val viewModel = viewModel()

        // toastMessage.test { ... } subscribes to the SharedFlow *before* running the
        // block inside, which is exactly the ordering this test needs: the flow has no
        // replay, so a collector that started late would miss the emission entirely.
        viewModel.toastMessage.test {
            // Act
            viewModel.onEvent(MediaUiEvents.OnTapOfRating(id = 4.5))

            // Assert
            // awaitItem() suspends until the next emission (or fails the test if none
            // arrives) -- no manual advanceUntilIdle()/list bookkeeping needed.
            assertEquals("Ratings tapped -- 4.5", awaitItem())

            // The test { } block cancels its collector automatically when it ends, so
            // there's no job to clean up by hand (unlike the manual launch/collect version).
        }
    }

    @Test
    fun `dismissInfoSheet clears a previously set disclosure back to null`() {
        // Arrange
        val viewModel = viewModel()
        viewModel.onEvent(MediaUiEvents.OnTapOfDisclosure(mediaResults()))
        assertEquals(mediaResults(), viewModel.disclosures.value)

        // Act
        viewModel.dismissInfoSheet()

        // Assert
        assertNull(viewModel.disclosures.value)
    }

    @Test
    fun `dismissInfoSheet on an already-null disclosure is a no-op`() {
        // Arrange
        val viewModel = viewModel()

        // Act
        viewModel.dismissInfoSheet()

        // Assert
        assertNull(viewModel.disclosures.value)
    }

    @Test
    fun `uiState starts as Loading for every section before loadAll is called`() {
        // Arrange
        val viewModel = viewModel()

        // Act
        // stateIn's initialValue is available synchronously the moment the ViewModel is
        // built, even with zero collectors -- unlike the emissions loadAll() triggers
        // below, reading .value here doesn't need us to subscribe to uiState first.
        val initialState = viewModel.uiState.value

        // Assert
        assertEquals(OmDbUiState.Loading, initialState.omdbUiState)
        assertEquals(MediaSectionUiState.Loading, initialState.tmdbUiState)
        assertEquals(MediaSectionUiState.Loading, initialState.popularMovieUiState)
        assertEquals(MediaSectionUiState.Loading, initialState.topRatedMoviesUiState)
    }

    @Test
    fun `loadAll updates uiState to Success for every section when all use cases succeed`() =
        runTest {
            // Arrange
            val trendingModel = trendingDomainModel()
            val popularModel = popularDomainModel()
            val topRatedModel = topRatedDomainModel()
            coEvery { trendingUseCase.invoke() } returns Result.Success(trendingModel)
            coEvery { popularMovieUseCase.invoke() } returns Result.Success(popularModel)
            coEvery { topRatedUseCase.invoke() } returns Result.Success(topRatedModel)
            val viewModel = viewModel()

            viewModel.uiState.test {
                // uiState is built with .stateIn(scope = viewModelScope, started =
                // WhileSubscribed(5000), ...), so the combine() of the four section flows
                // only starts actively collecting once something subscribes to uiState.
                // This `test { }` block IS that subscriber. If loadAll() were called before
                // subscribing, the section flows could update before combine was listening,
                // and this test could see stale data instead of failing loudly.
                awaitItem() // the initial Loading/Loading/Loading/Loading state

                // Act
                viewModel.loadAll()
                // loadAll() fires three separate viewModelScope.launch { } coroutines.
                // Nothing here actually suspends on real I/O (the use cases are mocked),
                // so advanceUntilIdle() just needs to run all three to completion.
                testScheduler.advanceUntilIdle()

                // combine() re-emits once per upstream change, so three sections flipping
                // Loading -> Success independently (in whatever order the dispatcher
                // happened to run them) can produce several intermediate combined states.
                // expectMostRecentItem() discards all of those and returns only the final,
                // settled state -- which is the only one these assertions care about.
                val finalState = expectMostRecentItem()

                // Assert
                // omdbUiState is never written to by loadAll() (nothing in HomeViewModel
                // currently populates it), so it should still be at its initial value.
                assertEquals(OmDbUiState.Loading, finalState.omdbUiState)

                // Reusing the production toMediaUiModel() mapper to build the expected
                // value keeps this test focused on ViewModel behavior (does it react to
                // Result.Success correctly?) rather than re-deriving mapping logic here.
                // Trade-off: a bug inside toMediaUiModel() itself wouldn't be caught by
                // this test -- that mapper deserves its own dedicated unit test.
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
    fun `loadAll caches the popular movies results only when that use case succeeds`() =
        runTest {
            // Arrange
            val popularModel = popularDomainModel()
            coEvery { trendingUseCase.invoke() } returns Result.Success(trendingDomainModel())
            coEvery { popularMovieUseCase.invoke() } returns Result.Success(popularModel)
            coEvery { topRatedUseCase.invoke() } returns Result.Success(topRatedDomainModel())
            val viewModel = viewModel()

            viewModel.uiState.test {
                // Act
                awaitItem() // initial Loading state
                viewModel.loadAll()
                testScheduler.advanceUntilIdle()
                expectMostRecentItem() // wait for the final settled state before asserting
                cancelAndIgnoreRemainingEvents()
            }

            // Assert
            // fetchPopularData() pushes results into the shared MediaResultsCache only on
            // success, so the movie-details screen can look up a tapped movie by id without
            // a second network call. This is a side effect of the popular-movies branch
            // specifically, so it's asserted separately from the uiState shape above.
            verify(exactly = 1) { mediaResultsCache.putAll(popularModel.toMediaUiModel().results) }
        }

    @Test
    fun `loadAll surfaces Result Error and Result Failure as Error states using their toString message`() =
        runTest {
            // Arrange
            // Deliberately mixing Result.Error and Result.Failure across sections: HomeViewModel
            // maps both to the same MediaSectionUiState.Error, using error/failure.toString() as
            // the message -- Kotlin `object` declarations default toString() to their simple
            // class name, which is what we assert against below (e.g. "NoInternetError").
            coEvery { trendingUseCase.invoke() } returns Result.Error(NetworkError.NoInternetError)
            coEvery { popularMovieUseCase.invoke() } returns Result.Failure(NetworkFailure.IOFailure)
            val topRatedModel = topRatedDomainModel()
            coEvery { topRatedUseCase.invoke() } returns Result.Success(topRatedModel)
            val viewModel = viewModel()

            viewModel.uiState.test {
                // Act
                awaitItem() // initial Loading state
                viewModel.loadAll()
                testScheduler.advanceUntilIdle()
                val finalState = expectMostRecentItem()

                // Assert
                assertEquals(
                    MediaSectionUiState.Error("NoInternetError"),
                    finalState.tmdbUiState
                )
                assertEquals(
                    MediaSectionUiState.Error("IOFailure"),
                    finalState.popularMovieUiState
                )
                // The topRated section still succeeds independently -- one section failing
                // must not drag the others down, since they're separate viewModelScope.launch
                // coroutines rather than one all-or-nothing block.
                assertEquals(
                    MediaSectionUiState.Success(topRatedModel.toMediaUiModel()),
                    finalState.topRatedMoviesUiState
                )

                cancelAndIgnoreRemainingEvents()
            }

            // Assert (continued)
            // A failed popular-movies fetch has nothing worth caching.
            verify(exactly = 0) { mediaResultsCache.putAll(any()) }
        }
}
