package com.example.profile

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ProfileViewModel @Inject constructor(

) : ViewModel() {

    private val _sharedFlow = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _sharedFlow

    var userName by mutableStateOf("")
        private set

    var password by mutableStateOf("")
        private set


    fun userNameChanged(userN: String) {
        userName = userN
    }

    fun passwordChanged(pass: String) {
        password = pass
    }

    fun onLoginClicked() {
        viewModelScope.launch {
            if (userName.isNotEmpty() && password.isNotEmpty()) {
                _sharedFlow.emit("Login clicked")
            } else {
                _sharedFlow.emit("Please enter UN or PWD")
            }
        }
    }
}

sealed interface LoginEvent {
    data object onLoginClicked : LoginEvent
}