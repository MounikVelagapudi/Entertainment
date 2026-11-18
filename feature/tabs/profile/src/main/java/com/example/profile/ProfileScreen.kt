package com.example.entertainment.profile

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.profile.LoginEvent
import com.example.profile.ProfileViewModel
import com.example.profile.session.SessionLabSection

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel()
) {

    val context = LocalContext.current

    val loginEvents: (LoginEvent) -> Unit = { event ->
        when (event) {
            is LoginEvent.onLoginClicked -> {
                viewModel.onLoginClicked()
            }
        }

    }

    LaunchedEffect(key1 = Unit) {
        viewModel.toastMessage.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }


    Column(modifier = modifier.verticalScroll(rememberScrollState())) {

        TextField(
            value = viewModel.userName,
            label = { Text("UserName") },
            modifier = Modifier.padding(14.dp),
            onValueChange = { viewModel.userNameChanged(it) }
        )

        Spacer(modifier = Modifier.padding(8.dp))

        TextField(
            value = viewModel.password,
            label = { Text("Password") },
            modifier = Modifier.padding(14.dp),
            onValueChange = { viewModel.passwordChanged(it) }
        )

        Spacer(modifier = Modifier.padding(vertical = 16.dp))

        OutlinedButton(
            onClick = { loginEvents(LoginEvent.onLoginClicked) }
        ) {
            Text(text = "LOGIN")
        }

        SessionLabSection(modifier = Modifier.padding(14.dp))
    }
}
