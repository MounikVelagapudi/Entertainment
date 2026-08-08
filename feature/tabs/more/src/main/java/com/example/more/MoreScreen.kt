package com.example.entertainment.more

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MoreScreen(
    modifier: Modifier = Modifier
) {

    Column(modifier = modifier) {
        LazyColumn() {
            item {
                Text(
                    text = "MORE SCREEN",
                    modifier = Modifier.padding(14.dp)
                )
            }
        }
    }
}
