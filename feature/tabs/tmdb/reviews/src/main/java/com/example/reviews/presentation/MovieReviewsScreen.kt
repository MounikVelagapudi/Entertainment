package com.example.reviews.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.reviews.domain.MovieReview
import java.util.Locale

private const val COLLAPSED_MAX_LINES = 4

/**
 * Reviews for [movieId] (TMDB page 1). Loads once per movie; back pops via [onBackClick].
 */
@Composable
fun MovieReviewsScreen(
    movieId: Int,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MovieReviewsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(movieId) {
        viewModel.load(movieId)
    }

    MovieReviewsContent(
        uiState = uiState,
        onBackClick = onBackClick,
        onRetryClick = viewModel::retry,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieReviewsContent(
    uiState: MovieReviewsUiState,
    onBackClick: () -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = "Reviews") },
                navigationIcon = {
                    TextButton(onClick = onBackClick) {
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
            when (uiState) {
                MovieReviewsUiState.Loading -> CircularProgressIndicator()

                MovieReviewsUiState.Empty -> Text(text = "No reviews yet")

                is MovieReviewsUiState.Error -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = uiState.message)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onRetryClick) {
                        Text(text = "Retry")
                    }
                }

                is MovieReviewsUiState.Success -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.reviews, key = { it.id }) { review ->
                        ReviewCard(review = review)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(review: MovieReview, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable(review.id) { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = review.author,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                review.rating?.let { rating ->
                    Text(
                        text = String.format(Locale.ROOT, "%.1f/10", rating),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Text(
                /* ISO-8601 timestamp; the date part is enough here. */
                text = review.createdAt.take(10),
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = review.content,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_MAX_LINES,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private val previewReviews = listOf(
    MovieReview(
        id = "r1",
        author = "Jane Critic",
        rating = 8.0,
        content = "A tight, well-paced thriller with a final act that actually lands. " +
            "The lead performance carries the slower middle stretch.",
        createdAt = "2024-05-12T10:15:30.000Z"
    ),
    MovieReview(
        id = "r2",
        author = "moviebuff42",
        rating = null,
        content = "Not for me. Pretty to look at, but the story never quite comes together.",
        createdAt = "2024-06-01T08:00:00.000Z"
    ),
)

@Preview(showBackground = true)
@Composable
private fun MovieReviewsSuccessPreview() {
    MovieReviewsContent(
        uiState = MovieReviewsUiState.Success(previewReviews),
        onBackClick = {},
        onRetryClick = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun MovieReviewsErrorPreview() {
    MovieReviewsContent(
        uiState = MovieReviewsUiState.Error("No internet connection"),
        onBackClick = {},
        onRetryClick = {},
    )
}
