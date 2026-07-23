package com.example.entertainment.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.entertainment.MediaUiEvents
import com.example.entertainment.core.domain.Result
import com.example.entertainment.di.IoDispatcher
import com.example.entertainment.home.omdb.OmDbUiState
import com.example.entertainment.home.popularMovies.domain.GetPopularMovieUseCase
import com.example.entertainment.home.popularMovies.domain.PopularMovie
import com.example.entertainment.home.topRated.topRated.domain.TopRatedUseCase
import com.example.entertainment.home.trendingMovies.domain.TrendingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject


interface MediaEvents {

    fun onEvent(event: MediaUiEvents)

}


@HiltViewModel
class HomeViewModel @Inject constructor(
    private val trendingUseCase: TrendingUseCase,
    private val popularMovieUseCase: GetPopularMovieUseCase,
    private val topRatedUseCase: TopRatedUseCase,
    private val mediaResultsCache: MediaResultsCache,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel(), MediaEvents {

    private val _trendingMoviesUiState =
        MutableStateFlow<MediaSectionUiState>(MediaSectionUiState.Loading)
    private val _omDbUiState = MutableStateFlow<OmDbUiState>(OmDbUiState.Loading)
    private val _popularUiState = MutableStateFlow<MediaSectionUiState>(MediaSectionUiState.Loading)
    private val _topRatedMoviesUiState =
        MutableStateFlow<MediaSectionUiState>(MediaSectionUiState.Loading)

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage

    private val _disclosures = MutableStateFlow<MediaResults?>(null)
    val disclosures: StateFlow<MediaResults?> = _disclosures

    val uiState: StateFlow<HomeCombinedUiState> =
        combine(
            _omDbUiState,
            _trendingMoviesUiState,
            _popularUiState,
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

    fun loadAll() {
        // supervisorScope {
        viewModelScope.launch { fetchTrendingData() }
        viewModelScope.launch { fetchPopularData() }
        viewModelScope.launch { fetchTopRatedData() }
        // }

    }


    private suspend fun fetchTopRatedData() {
        _topRatedMoviesUiState.value = MediaSectionUiState.Loading
        when (val result = topRatedUseCase.invoke()) {
            is Result.Success -> {
                val uiModel = result.data.toMediaUiModel()
                _topRatedMoviesUiState.value = MediaSectionUiState.Success(uiModel)
            }

            is Result.Failure -> {
                _topRatedMoviesUiState.value =
                    MediaSectionUiState.Error(result.failure.toString())
            }

            is Result.Error -> {
                _topRatedMoviesUiState.value = MediaSectionUiState.Error(result.error.toString())
            }
        }
    }

    private suspend fun fetchPopularData() {

        _popularUiState.value = MediaSectionUiState.Loading
        when (val result = popularMovieUseCase.invoke()) {
            is Result.Success -> {
                val uiModel = result.data.toMediaUiModel()
                mediaResultsCache.putAll(uiModel.results)
                _popularUiState.value = MediaSectionUiState.Success(uiModel)
            }

            is Result.Failure -> {
                _popularUiState.value = MediaSectionUiState.Error(result.failure.toString())
            }

            is Result.Error -> {
                _popularUiState.value = MediaSectionUiState.Error(result.error.toString())
            }
        }
    }

    private suspend fun fetchTrendingData() {

        _trendingMoviesUiState.value = MediaSectionUiState.Loading
        when (val result = trendingUseCase.invoke()) {
            is Result.Success -> {
                val uiModel = result.data.toMediaUiModel()
                _trendingMoviesUiState.value = MediaSectionUiState.Success(uiModel)
            }

            is Result.Error -> {
                _trendingMoviesUiState.value = MediaSectionUiState.Error(
                    result.error.toString()
                )
            }

            is Result.Failure -> {
                _trendingMoviesUiState.value = MediaSectionUiState.Error(
                    result.failure.toString()
                )
            }
        }
    }

    override fun onEvent(event: MediaUiEvents) {
        when (event) {
            is MediaUiEvents.OnTapOfMovieCard -> {
                // Handled by the caller directly (navigates to the detail screen);
                // nothing for the ViewModel to do here.
            }

            is MediaUiEvents.OnTapOfRating -> {
                viewModelScope.launch {
                    _toastMessage.emit("Ratings tapped -- ${event.id}")
                }
            }

            is MediaUiEvents.OnTapOfDisclosure -> {
                _disclosures.value = event.media
            }
        }
    }

    fun dismissInfoSheet() {
        _disclosures.value = null
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