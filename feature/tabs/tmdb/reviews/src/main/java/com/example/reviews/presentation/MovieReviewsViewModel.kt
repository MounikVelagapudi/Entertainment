package com.example.reviews.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.entertainment.core.domain.APIError
import com.example.entertainment.core.domain.APIFailure
import com.example.entertainment.core.domain.NetworkError
import com.example.entertainment.core.domain.Result
import com.example.entertainment.core.domain.TRPError
import com.example.reviews.domain.GetMovieReviewsUseCase
import com.example.reviews.domain.MovieReview
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class MovieReviewsViewModel @Inject constructor(
    private val getMovieReviews: GetMovieReviewsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<MovieReviewsUiState>(MovieReviewsUiState.Loading)
    val uiState: StateFlow<MovieReviewsUiState> = _uiState.asStateFlow()

    private var movieId: Int? = null
    private var loadJob: Job? = null

    /* Idempotent per movieId -- the screen's LaunchedEffect re-runs after rotation. */
    fun load(movieId: Int) {
        if (this.movieId == movieId) return
        this.movieId = movieId
        fetch(movieId)
    }

    fun retry() {
        movieId?.let { fetch(it) }
    }

    private fun fetch(movieId: Int) {
        loadJob?.cancel()
        _uiState.value = MovieReviewsUiState.Loading
        loadJob = viewModelScope.launch {
            _uiState.value = when (val result = getMovieReviews(movieId)) {
                is Result.Success ->
                    if (result.data.isEmpty()) MovieReviewsUiState.Empty
                    else MovieReviewsUiState.Success(result.data)

                is Result.Error -> MovieReviewsUiState.Error(result.error.toMessage())
                is Result.Failure -> MovieReviewsUiState.Error(result.failure.toMessage())
            }
        }
    }
}

private fun TRPError.toMessage(): String = when (this) {
    NetworkError.NoInternetError, NetworkError.ConnectionError -> "No internet connection"
    NetworkError.TimeoutError -> "Request timed out"
    /* Server message can be the raw unparsed body (TMDB's error shape differs), so show the code only. */
    is APIError -> "Couldn't load reviews ($code)"
    is APIFailure -> "Couldn't load reviews ($code)"
    else -> "Couldn't load reviews"
}

sealed interface MovieReviewsUiState {
    data object Loading : MovieReviewsUiState
    data class Success(val reviews: List<MovieReview>) : MovieReviewsUiState
    data object Empty : MovieReviewsUiState
    data class Error(val message: String) : MovieReviewsUiState
}
