package com.example.presentation.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.entertainment.core.presentation.reusable_components.events.MediaUiEvents
import com.example.entertainment.core.presentation.reusable_components.events.Section
import com.example.entertainment.core.domain.Resource
import com.example.entertainment.core.domain.Result
import com.example.entertainment.core.presentation.reusable_components.MediaResults
import com.example.entertainment.core.presentation.reusable_components.MediaUIModel
import com.example.entertainment.core.home.MediaListDomainModel
import com.example.entertainment.home.omdb.OmDbUiState
import com.example.popular.domain.GetPopularMovieUseCase
import com.example.popular.domain.PopularMovie
import com.example.presentation.presentation.sync.HomeSyncWorker
import com.example.toprated.domain.TopRatedUseCase
import com.example.trending.domain.TrendingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import java.util.concurrent.TimeUnit
import javax.inject.Inject


interface MediaEvents {

    fun onEvent(event: MediaUiEvents)

}


@HiltViewModel
class HomeViewModel @Inject constructor(
    private val trendingUseCase: TrendingUseCase,
    private val popularMovieUseCase: GetPopularMovieUseCase,
    private val topRatedUseCase: TopRatedUseCase,
    private val workManager: WorkManager,
    val savedStateHandle: SavedStateHandle
) : ViewModel(), MediaEvents {

    /* Start/stop are driven from Compose (DisposableEffect in HomeScreenContent), not
       init{}/onCleared() -- this ViewModel is shared across every tab under one nav
       destination, so onCleared() never fires on a tab switch, only Compose actually
       knows when this specific tab stops being shown. */
    fun startHomeSync() {
        val request = PeriodicWorkRequestBuilder<HomeSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            HomeSyncWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancelHomeSync() {
        workManager.cancelUniqueWork(HomeSyncWorker.UNIQUE_WORK_NAME)
    }

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage

    private val _disclosures = MutableStateFlow<MediaResults?>(null)
    val disclosures: StateFlow<MediaResults?> = _disclosures

    /* Guards against firing another refresh while one's still in flight -- the
       scroll-to-end signal can otherwise re-fire every recomposition at the last item. */
    private var isPopularRefreshInFlight = false
    private var isTopRatedRefreshInFlight = false

    /* Highest page fetched, and the last totalPages reported -- left unchanged on a
       failed fetch so the next scroll-to-end retries the same page instead of skipping it. */
    private var popularPage = 1
    private var popularTotalPages = Int.MAX_VALUE
    private var topRatedPage = 1
    private var topRatedTotalPages = Int.MAX_VALUE

    /* Timestamp of the last completed loadAll() -- null means "never loaded yet",
       which shouldRefresh() also treats as stale. */
    private var lastLoadedAt: Long? = null

    private val _trendingMoviesUiState =
        trendingUseCase.observe().map { resource ->
            when (resource) {
                is Resource.Loading -> MediaSectionUiState.Loading
                is Resource.Success -> MediaSectionUiState.Success(resource.data.toMediaUiModel())
                is Resource.Error -> MediaSectionUiState.Error(resource.message)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = MediaSectionUiState.Loading
        )

    private val _omDbUiState = MutableStateFlow<OmDbUiState>(OmDbUiState.Loading)

    private val _popularMoviesUiState: StateFlow<MediaSectionUiState> =
        popularMovieUseCase.observe()
            .map { resource ->
                when (resource) {
                    is Resource.Loading -> MediaSectionUiState.Loading
                    is Resource.Success -> MediaSectionUiState.Success(resource.data.toMediaUiModel())
                    is Resource.Error -> MediaSectionUiState.Error(resource.message)
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = MediaSectionUiState.Loading
            )

    private val _topRatedMoviesUiState: StateFlow<MediaSectionUiState> =
        topRatedUseCase.observe()
            .map { resource ->
                when (resource) {
                    is Resource.Loading -> MediaSectionUiState.Loading
                    is Resource.Success -> MediaSectionUiState.Success(resource.data.toMediaUiModel())
                    is Resource.Error -> MediaSectionUiState.Error(resource.message)
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = MediaSectionUiState.Loading
            )

    val uiState: StateFlow<HomeCombinedUiState> =
        combine(
            _omDbUiState,
            _trendingMoviesUiState,
            _popularMoviesUiState,
            _topRatedMoviesUiState
        ) { omDb, tmDb, popularUiState, topRatedMoviesUiState ->
            HomeCombinedUiState(omDb, tmDb, popularUiState, topRatedMoviesUiState)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Companion.WhileSubscribed(5000),
            initialValue = HomeCombinedUiState(
                OmDbUiState.Loading,
                MediaSectionUiState.Loading,
                MediaSectionUiState.Loading,
                MediaSectionUiState.Loading
            )
        )

    suspend fun fetchTrendingMovies() {
        refreshTrendingData()
    }

    suspend fun fetchNextPopularPage() {
        if (popularPage >= popularTotalPages) return
        refreshPopularData(page = popularPage + 1)
    }

    suspend fun fetchNextTopRatedPage() {
        if (topRatedPage >= topRatedTotalPages) return
        refreshTopRatedData(page = topRatedPage + 1)
    }

    suspend fun loadAll() {

        supervisorScope {
            val trending = async { refreshTrendingData() }
            val popular = async { refreshPopularData(page = popularPage) }
            val topRated = async { refreshTopRatedData(page = topRatedPage) }
            trending.await()
            popular.await()
            topRated.await()
        }
        lastLoadedAt = System.currentTimeMillis()
    }


    private suspend fun refreshTopRatedData(page: Int) {
        when (val result = topRatedUseCase.refresh(page)) {
            is Result.Success -> {
                topRatedPage = page
                topRatedTotalPages = result.data.totalPages
            }

            is Result.Error, is Result.Failure -> {
                /* Leave topRatedPage/topRatedTotalPages as-is so the next scroll-to-end retry doesn't skip a page. */
            }
        }
    }

    private suspend fun refreshPopularData(page: Int) {
        when (val result = popularMovieUseCase.refresh(page)) {
            is Result.Success -> {
                popularPage = page
                popularTotalPages = result.data.totalPages
            }

            is Result.Error, is Result.Failure -> {
                /* Leave popularPage/popularTotalPages as-is so the next scroll-to-end retry doesn't skip a page. */
            }
        }
    }

    /*
    * Called from Compose on every screen entry -- init{} only runs once per ViewModel
    * instance, so tab-return re-fetches have to be triggered from here. shouldRefresh()
    * decides whether that's actually needed; this also covers the very first load,
    * since lastLoadedAt starts out null.
    * */
    fun onScreenEntered() {
        if (!shouldRefresh()) return
        viewModelScope.launch {
            loadAll()
        }
    }

    private fun shouldRefresh(): Boolean =
        lastLoadedAt == null || (System.currentTimeMillis() -
                lastLoadedAt!!) > STALE_AFTER_MS

    private suspend fun refreshTrendingData() {
        trendingUseCase.refresh()
    }

    override fun onEvent(event: MediaUiEvents) {
        when (event) {
            is MediaUiEvents.OnTapOfMovieCard -> {
                /* Handled by the caller (navigates to the detail screen) -- nothing to do here. */
            }

            is MediaUiEvents.OnTapOfRating -> {
                viewModelScope.launch {
                    _toastMessage.emit("Ratings tapped -- ${event.id}")
                }
            }

            is MediaUiEvents.OnTapOfDisclosure -> {
                _disclosures.value = event.media
            }

            is MediaUiEvents.OnScrolledToEnd -> {
                when (event.section) {
                    /* getTrendingMovies() has no `page` param yet -- nothing to paginate here until it does. */
                    Section.TRENDING -> Unit

                    Section.POPULAR -> refreshSectionIfIdle(
                        inFlight = { isPopularRefreshInFlight },
                        setInFlight = { isPopularRefreshInFlight = it },
                        fetch = ::fetchNextPopularPage
                    )

                    Section.TOP_RATED -> refreshSectionIfIdle(
                        inFlight = { isTopRatedRefreshInFlight },
                        setInFlight = { isTopRatedRefreshInFlight = it },
                        fetch = ::fetchNextTopRatedPage
                    )
                }
            }
        }
    }

    private fun refreshSectionIfIdle(
        inFlight: () -> Boolean,
        setInFlight: (Boolean) -> Unit,
        fetch: suspend () -> Unit,
    ) {
        if (inFlight()) return
        setInFlight(true)
        viewModelScope.launch {
            try {
                fetch()
            } finally {
                setInFlight(false)
            }
        }
    }

    fun dismissInfoSheet() {
        _disclosures.value = null
    }

    private companion object {
        /* TMDB's trending/popular/top-rated lists don't move minute to minute --
           this just bounds how often a tab-flick re-fetches, same idea as an HTTP
           Cache-Control max-age for semi-static catalog data. */
        const val STALE_AFTER_MS = 5 * 60 * 1000L // 5 minutes
    }
}

fun MediaListDomainModel.toMediaUiModel(): MediaUIModel {
    return MediaUIModel(
        totalResults = totalResults,
        totalPages = totalPages,
        results = results.orEmpty()
            .filter { it.id != 0 }
            .map {
                MediaResults(
                    title = it.title,
                    releaseDate = it.releaseDate,
                    imageUrl = it.posterPath,
                    ratings = it.voteAverage,
                    id = it.id,
                    overview = (it as? PopularMovie)?.overview
                )
            }
    )
}