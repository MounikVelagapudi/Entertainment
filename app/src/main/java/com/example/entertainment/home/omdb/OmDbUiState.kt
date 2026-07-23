package com.example.entertainment.home.omdb

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.entertainment.core.data.networking.apiCall
import com.example.entertainment.core.domain.Result
import com.example.entertainment.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow


sealed interface OmDbUiState {
    data object Loading : OmDbUiState
    data class Success(val data: OmDbDTO) : OmDbUiState
    data class Error(val message: String) : OmDbUiState

}
