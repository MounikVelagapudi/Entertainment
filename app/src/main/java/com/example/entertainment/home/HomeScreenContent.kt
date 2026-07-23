package com.example.entertainment.home

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.entertainment.MediaUiEvents
import com.example.entertainment.ui.components.GenericBottomSheet
import com.example.entertainment.ui.preview.PreviewConstants

@Composable
fun HomeScreenContent(
    modifier: Modifier = Modifier,
    homeViewModel: HomeViewModel = hiltViewModel(),
    onNavigateToDetail: (MediaResults) -> Unit,
) {
    val onEvent: (MediaUiEvents) -> Unit = { event ->
        when (event) {
            is MediaUiEvents.OnTapOfMovieCard -> onNavigateToDetail(event.media)
            else -> homeViewModel.onEvent(event)
        }
    }

    val trendingUiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val disclosuresData by homeViewModel.disclosures.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        homeViewModel.loadAll()
    }

    LaunchedEffect(Unit) {
        homeViewModel.toastMessage.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    GenericBottomSheet(
        item = disclosuresData,
        onDismiss = { homeViewModel.dismissInfoSheet() }
    ) { media ->
        MediaDetailBottomSheetContent(media)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag("home_screen")
    ) {
        MediaSectionScreen(
            title = "Trending this week",
            state = trendingUiState.tmdbUiState,
            isTappable = false,
            onEvent = onEvent
        )
        Spacer(modifier = Modifier.padding(horizontal = 8.dp))
        MediaSectionScreen(
            title = "Popular",
            state = trendingUiState.popularMovieUiState,
            isTappable = true,
            onEvent = onEvent
        )
        Spacer(modifier = Modifier.padding(horizontal = 8.dp))
        MediaSectionScreen(
            title = "Top Rated",
            state = trendingUiState.topRatedMoviesUiState,
            isTappable = false,
            onEvent = onEvent
        )
    }
}

@Composable
fun MediaSectionScreen(
    title: String,
    state: MediaSectionUiState,
    isTappable: Boolean,
    onEvent: (MediaUiEvents) -> Unit
) {
    when (state) {
        is MediaSectionUiState.Loading -> {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                CircularProgressIndicator()
            }
        }

        is MediaSectionUiState.Success -> {
            MediaComposableUI(
                title = title,
                uiModel = state.data,
                isTappable = isTappable,
                onEvent = onEvent
            )
        }

        is MediaSectionUiState.Error -> {
            Text(text = state.message)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MediaSectionScreenLoadingPreview() {
    MediaSectionScreen(
        title = "Trending this week",
        state = MediaSectionUiState.Loading,
        isTappable = false,
        onEvent = {}
    )
}

@Preview(showBackground = true)
@Composable
fun MediaSectionScreenSuccessPreview() {
    MediaSectionScreen(
        title = "Trending this week",
        state = MediaSectionUiState.Success(PreviewConstants.previewMediaUiModel),
        isTappable = false,
        onEvent = {}
    )
}

@Preview(showBackground = true)
@Composable
fun MediaSectionScreenErrorPreview() {
    MediaSectionScreen(
        title = "Trending this week",
        state = MediaSectionUiState.Error("Something went wrong"),
        isTappable = false,
        onEvent = {}
    )
}
