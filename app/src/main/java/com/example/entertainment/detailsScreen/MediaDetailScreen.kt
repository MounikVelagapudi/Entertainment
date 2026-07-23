package com.example.entertainment.detailsScreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.entertainment.MediaUiFormatterUtils
import com.example.entertainment.home.MediaResults
import com.example.entertainment.ui.preview.PreviewConstants

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDetailScreen(
    mediaId: Int,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MediaDetailsViewModel = hiltViewModel()
) {

    val uiState = viewModel.mediaDetailsUiState.collectAsStateWithLifecycle()

    LaunchedEffect(key1 = Unit) {
        viewModel.loadCachedData(mediaId)
    }

    when (val result = uiState.value) {
        is MediaDetailsUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator()
            }
        }

        is MediaDetailsUiState.Success -> {
            MediaDetailScreenContent(modifier, result.data, onBackClick)
        }

        is MediaDetailsUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize()) {
                Text(text = "ERROR")
            }
        }

    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDetailScreenContent(
    modifier: Modifier = Modifier,
    media: MediaResults,
    onBackClick: () -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick
                    ) {
                        Text(text = "BACK")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            MediaDetailScreenUI(modifier, media)
        }
    }
}

@Composable
fun MediaDetailScreenUI(modifier: Modifier, media: MediaResults) {

    Box(
        modifier = Modifier
            .padding(16.dp)
            .background(Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
        ) {
            // 1. Movie Poster Image (Height adjusted to leave room for your text)
            AsyncImage(
                model = MediaUiFormatterUtils.posterUrl(media.imageUrl),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentScale = ContentScale.Crop,
                contentDescription = "Movie Poster"
            )

            // 2. Movie Title Text
            Text(
                text = media.title,
                color = Color.Black,
                textAlign = TextAlign.Start,
                fontSize = 11.sp,
                maxLines = 1, // Prevents text truncation issues
                modifier = Modifier.padding(top = 4.dp, start = 2.dp)
            )

            // 3. Movie Release Date Text
            Text(
                text = media.releaseDate,
                color = Color.Blue,
                textAlign = TextAlign.Start,
                fontSize = 8.sp,
                modifier = Modifier.padding(start = 2.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MediaDetailScreenContentPreview() {
    MediaDetailScreenContent(media = PreviewConstants.previewMovie) {
    }
}

@Preview(showBackground = true)
@Composable
fun MediaDetailScreenUIPreview() {
    MediaDetailScreenUI(modifier = Modifier, media = PreviewConstants.previewMovie)
}
