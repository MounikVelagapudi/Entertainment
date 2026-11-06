package com.example.entertainment.home.omdb

import com.example.entertainment.core.home.omdb.OmDbDTO

sealed interface OmDbUiState {
    data object Loading : OmDbUiState
    data class Success(val data: OmDbDTO) : OmDbUiState
    data class Error(val message: String) : OmDbUiState

}
