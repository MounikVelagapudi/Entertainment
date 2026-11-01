package com.example.presentation.presentation

import com.example.entertainment.core.presentation.reusable_components.MediaUIModel

sealed interface MediaSectionUiState {
    data object Loading : MediaSectionUiState
    data class Success(val data: MediaUIModel) : MediaSectionUiState
    data class Error(val message: String) : MediaSectionUiState
}
