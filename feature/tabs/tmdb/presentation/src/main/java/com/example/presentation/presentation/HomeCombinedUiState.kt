package com.example.presentation.presentation

import com.example.entertainment.home.omdb.OmDbUiState

data class HomeCombinedUiState(
    val omdbUiState: OmDbUiState,
    val tmdbUiState: MediaSectionUiState,
    val popularMovieUiState: MediaSectionUiState,
    val topRatedMoviesUiState: MediaSectionUiState
)

val HomeCombinedUiState.isAnyLoading: Boolean
    get() = tmdbUiState !is MediaSectionUiState.Loading
            || popularMovieUiState !is MediaSectionUiState.Loading
            || topRatedMoviesUiState !is MediaSectionUiState.Loading
