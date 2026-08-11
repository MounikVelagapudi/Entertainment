package com.example.presentation.presentation

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.entertainment.MediaUiEvents
import com.example.entertainment.ui.components.GenericBottomSheet
import com.example.entertainment.ui.preview.PreviewConstants
import com.example.presentation.presentation.performance.ComposePerformanceMonitor
import com.example.presentation.presentation.performance.TrackJankState
import com.example.presentation.presentation.performance.TrackScreenPerformanceTrace

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

    // Any dropped frame TrackJank() (in MainActivity) logs while this screen
    // is composed will carry states = {screen=Home}, so you can tell it
    // apart from jank happening on Favourites/Profile/etc.
    TrackJankState(key = "screen", value = "Home")

    // Starts (and, on leaving Home, stops/uploads) a Firebase Performance
    // trace named "screen_home" for this screen visit -- TrackJank's frame
    // listener feeds its frame_count/jank_frame_count/jank_frame_duration_ms
    // metrics into it while it's active.
    TrackScreenPerformanceTrace(screenName = "Home")

    val combinedUiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val disclosuresData by homeViewModel.disclosures.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val listState =
        rememberLazyListState() // PREP: rememberLazyListState will remember the state of the UI on where the current state is

    // To check if the scroll reached the end and trigger a new and fresh set of data
    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem?.index != 0 &&
                    lastVisibleItem?.index == listState.layoutInfo.totalItemsCount - 1
        }
    }

    LaunchedEffect(Unit) {
        homeViewModel.loadAll()
    }


    LaunchedEffect(shouldLoadMore.value, combinedUiState) {
        if (shouldLoadMore.value && !combinedUiState.isAnyLoading) {
            homeViewModel.loadAll()
        }
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
            state = combinedUiState.tmdbUiState,
            isTappable = false,
            onEvent = onEvent
        )
        Spacer(modifier = Modifier.padding(horizontal = 8.dp))
        MediaSectionScreen(
            title = "Popular",
            state = combinedUiState.popularMovieUiState,
            isTappable = true,
            onEvent = onEvent
        )
        Spacer(modifier = Modifier.padding(horizontal = 8.dp))
        MediaSectionScreen(
            title = "Top Rated",
            state = combinedUiState.topRatedMoviesUiState,
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
    // One monitor instance per section call site (Trending/Popular/Top
    // Rated each get their own), remembered so it survives recompositions
    // rather than being recreated every time.
    val performanceMonitor = remember { ComposePerformanceMonitor() }

    when (state) {
        is MediaSectionUiState.Loading -> {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                CircularProgressIndicator()
            }
        }

        is MediaSectionUiState.Success -> {
            // PRACTICAL DEBUG TOOL: times how long composing this section's
            // row of cards takes. Watch Logcat ("ComposePerf") when new data
            // arrives (loadAll() triggers pagination) -- a section with many
            // items or expensive per-item work will show up here before it
            // shows up as janky scrolling.
            performanceMonitor.Measured(key = "section_${title}") {
                MediaComposableUI(
                    modifier = Modifier.fadeInOnLoad(),
                    title = title,
                    uiModel = state.data,
                    isTappable = isTappable,
                    onEvent = onEvent
                )
            }
        }

        is MediaSectionUiState.Error -> {
            Text(text = state.message)
        }
    }
}

// ✅ Use composed { } for stateful modifiers.
fun Modifier.fadeInOnLoad() =
    composed { // PREP: use composed annotation that can bring in composable build-in functions
        val alpha = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            alpha.animateTo(1f)
        }
        this.alpha(alpha.value)
    }

@Composable
// Conditional modifiers that can be used like when tapped on a card etc
fun Modifier.conditional(
    condition: Boolean,
    modifier: @Composable Modifier.() -> Modifier
): Modifier {
    return if (condition) {
        then(modifier(Modifier))
    } else {
        this
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
