import android.widget.Space
import com.example.presentation.presentation.HomeViewModel
import com.example.presentation.presentation.MediaSectionUiState
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.entertainment.core.presentation.reusable_components.events.MediaUiEvents
import com.example.entertainment.core.presentation.reusable_components.events.Section
import com.example.entertainment.core.presentation.reusable_components.ui.MediaCardComponentUI
import com.example.entertainment.core.presentation.reusable_components.ui.MediaDetailBottomSheetContent
import com.example.entertainment.core.presentation.reusable_components.MediaResults
import com.example.entertainment.ui.components.GenericBottomSheet
import com.example.entertainment.core.presentation.reusable_components.preview.PreviewConstants
import com.example.entertainment.core.presentation.reusable_components.performance.ComposePerformanceMonitor
import com.example.presentation.presentation.performance.TrackJankState
import com.example.presentation.presentation.performance.TrackScreenPerformanceTrace

@Composable
fun HomeScreenContent(
    modifier: Modifier = Modifier,
    homeViewModel: HomeViewModel = hiltViewModel(),
    onNavigateToDetailScreen: (MediaResults) -> Unit,
) {
    val onEvent: (MediaUiEvents) -> Unit = { event ->
        when (event) {
            is MediaUiEvents.OnTapOfMovieCard -> onNavigateToDetailScreen(event.media)
            else -> homeViewModel.onEvent(event)
        }
    }

//    TrackJankState(key = "screen", value = "Home")
//    TrackScreenPerformanceTrace(screenName = "Home")

    val combinedUiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val disclosuresData by homeViewModel.disclosures.collectAsStateWithLifecycle()
    val context = LocalContext.current

    /*  repeatOnLifecycle re-runs its block every time the lifecycle (re-)enters
        STARTED, and cancels whatever's running inside it the moment the lifecycle
        drops below STARTED. Wrapping onScreenEntered() in it means it fires on
        first composition, on returning to this tab (a fresh LaunchedEffect starts
        and the lifecycle is already STARTED, so it runs immediately), and again
        whenever the whole app is backgrounded and resumed -- three cases, one
        trigger, without a second LaunchedEffect(Unit) racing this one.
     */
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            homeViewModel.onScreenEntered()
        }
    }

    // HomeViewModel is shared across every tab (one nav destination for all of
    // them), so onCleared() never fires on a tab switch -- only Compose actually
    // knows when this tab specifically stops being shown. Start the periodic sync
    // when this composable enters composition, cancel it when it leaves.
    DisposableEffect(Unit) {
        homeViewModel.startHomeSync()
        onDispose {
            homeViewModel.cancelHomeSync()
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
            onEvent = onEvent,
            onScrollToEnd = { onEvent(MediaUiEvents.OnScrolledToEnd(Section.TRENDING)) }
        )
        Spacer(modifier = Modifier.padding(horizontal = 8.dp))
        MediaSectionScreen(
            title = "Popular",
            state = combinedUiState.popularMovieUiState,
            isTappable = true,
            onEvent = onEvent,
            onScrollToEnd = { onEvent(MediaUiEvents.OnScrolledToEnd(Section.POPULAR)) }
        )
        Spacer(modifier = Modifier.padding(horizontal = 8.dp))
        MediaSectionScreen(
            title = "Top Rated",
            state = combinedUiState.topRatedMoviesUiState,
            isTappable = false,
            onEvent = onEvent,
            onScrollToEnd = { onEvent(MediaUiEvents.OnScrolledToEnd(Section.TOP_RATED)) }
        )
    }
}

@Composable
fun MediaSectionScreen(
    title: String,
    state: MediaSectionUiState,
    isTappable: Boolean,
    onEvent: (MediaUiEvents) -> Unit,
    onScrollToEnd: () -> Unit = {}
) {
    /* One monitor per section call site, remembered so it survives recompositions. */
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
            performanceMonitor.Measured(key = "section_${title}") {
                MediaCardComponentUI(
                    modifier = Modifier.fadeInOnLoad(),
                    title = title,
                    uiModel = state.data,
                    isTappable = isTappable,
                    onEvent = onEvent,
                    onScrollToEnd = onScrollToEnd
                )
            }
        }

        is MediaSectionUiState.Error -> {
            Text(text = state.message)
        }
    }
}

/* Use composed { } for stateful modifiers. */
fun Modifier.fadeInOnLoad() =
    composed { /* PREP: composed{} lets a Modifier factory call other @Composable functions. */
        val alpha = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            alpha.animateTo(1f)
        }
        this.alpha(alpha.value)
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


@Composable
fun test() {

    Row(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize(),
        verticalAlignment = Alignment.Bottom
    ) {
        Button(
            onClick = {},
        ) {
            Text(
                text = "Button1",
            )
        }

        Spacer(modifier = Modifier.height(4.dp))


        Button(
            onClick = {},
        ) {
            Text(
                text = "Button1",
            )
        }
    }

}
