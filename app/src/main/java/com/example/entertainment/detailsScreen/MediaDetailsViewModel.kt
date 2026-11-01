package com.example.entertainment.detailsScreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.entertainment.core.presentation.reusable_components.MediaResults
import com.example.toprated.domain.TopRatedMovie
import com.example.toprated.domain.TopRatedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MediaDetailsViewModel @Inject constructor(
    private val topRatedUseCase: TopRatedUseCase,
) : ViewModel() {

    val _mediaDetailsUiState = MutableStateFlow<MediaDetailsUiState>(MediaDetailsUiState.Loading)

    val mediaDetailsUiState = _mediaDetailsUiState.asStateFlow()

    fun loadCachedData(id: Int) {
        viewModelScope.launch {
            /* TopRated's repo only for now -- Trending/Popular have no lookup yet. */
            val topRatedMatch = topRatedUseCase.getById(id)?.toMediaResults()

            _mediaDetailsUiState.value = topRatedMatch
                ?.let { MediaDetailsUiState.Success(it) }
                ?: MediaDetailsUiState.Error("Failure")
        }
    }
}

private fun TopRatedMovie.toMediaResults(): MediaResults = MediaResults(
    title = title,
    releaseDate = releaseDate,
    imageUrl = posterPath,
    id = id,
    ratings = voteAverage
)

sealed interface MediaDetailsUiState {
    data object Loading : MediaDetailsUiState
    data class Success(val data: MediaResults) : MediaDetailsUiState
    data class Error(val error: String) : MediaDetailsUiState
}
