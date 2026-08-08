package com.example.entertainment.home.omdb

sealed interface OmDbUiState {
    data object Loading : OmDbUiState
    data class Success(val data: OmDbDTO) : OmDbUiState
    data class Error(val message: String) : OmDbUiState

}
