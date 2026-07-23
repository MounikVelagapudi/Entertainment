package com.example.entertainment.home

sealed interface MediaSectionUiState {
    data object Loading : MediaSectionUiState
    data class Success(val data: MediaUIModel) : MediaSectionUiState
    data class Error(val message: String) : MediaSectionUiState
}
