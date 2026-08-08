package com.example.presentation.presentation

import com.example.entertainment.home.omdb.OmDbUiState

data class HomeCombinedUiState(
    val omdbUiState: OmDbUiState,
    val tmdbUiState: MediaSectionUiState,
    val popularMovieUiState: MediaSectionUiState,
    val topRatedMoviesUiState: MediaSectionUiState
)

// Common extension function to check if any of the uiStates are in loading stage and based on that UI would be updated by calling loadMore
val HomeCombinedUiState.isAnyLoading: Boolean
    get() = tmdbUiState !is MediaSectionUiState.Loading
            || popularMovieUiState !is MediaSectionUiState.Loading
            || topRatedMoviesUiState !is MediaSectionUiState.Loading
