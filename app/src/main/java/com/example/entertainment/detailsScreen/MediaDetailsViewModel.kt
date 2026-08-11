package com.example.entertainment.detailsScreen

import androidx.lifecycle.ViewModel
import com.example.presentation.presentation.MediaResults
import com.example.presentation.presentation.MediaResultsCache
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class MediaDetailsViewModel @Inject constructor(
    private val mediaResultsCache: MediaResultsCache
) : ViewModel() {

    val _mediaDetailsUiState = MutableStateFlow<MediaDetailsUiState>(MediaDetailsUiState.Loading)

    val mediaDetailsUiState = _mediaDetailsUiState.asStateFlow()

    fun loadCachedData(id: Int) {
        _mediaDetailsUiState.value = mediaResultsCache.get(id)
            ?.let { MediaDetailsUiState.Success(it) }
            ?: MediaDetailsUiState.Error("Failure")
    }
}

sealed interface MediaDetailsUiState {
    data object Loading : MediaDetailsUiState
    data class Success(val data: MediaResults) : MediaDetailsUiState
    data class Error(val error: String) : MediaDetailsUiState
}