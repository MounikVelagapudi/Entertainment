package com.example.entertainment.home

import com.example.entertainment.home.omdb.OmDbUiState

data class HomeCombinedUiState(
    val omdbUiState: OmDbUiState,
    val tmdbUiState: MediaSectionUiState,
    val popularMovieUiState: MediaSectionUiState,
    val topRatedMoviesUiState: MediaSectionUiState
)
