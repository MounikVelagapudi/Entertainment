package com.example.entertainment.home.omdb

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage


@Composable
fun OmDbMoviesUI(modifier: Modifier, omDbDTO: OmDbDTO) {

    Column(
        modifier = modifier.padding(4.dp)
    ) {
        Text(
            text = "OMDB",
            textAlign = TextAlign.Center,
            fontSize = 20.sp,
            modifier = modifier
        )

        AsyncImage(
            model = omDbDTO.poster,
            contentDescription = omDbDTO.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 4.dp)
        )

        LazyColumn {
            items(omDbDTO.ratings!!) { rating ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = rating.source!!,
                        modifier = Modifier.padding(2.dp)
                    )
                    Text(
                        text = rating.value!!,
                        modifier = Modifier.padding(2.dp)
                    )
                }
            }
        }
    }

}
