package com.example.entertainment.core.presentation.reusable_components.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import coil3.compose.AsyncImage
import com.example.core.BuildConfig
import com.example.entertainment.core.MediaUiFormatterUtils
import com.example.entertainment.core.presentation.reusable_components.MediaResults
import com.example.entertainment.core.presentation.reusable_components.MediaUIModel
import com.example.entertainment.core.presentation.reusable_components.events.MediaUiEvents
import com.example.entertainment.core.presentation.reusable_components.performance.RecompositionCounter
import com.example.entertainment.core.presentation.reusable_components.preview.PreviewConstants

/**
 * Stable, automation-friendly key derived from a section's display title
 * (e.g. "Trending this week" -> "trending_this_week"). Used to build
 * deterministic testTags for the section title and its LazyRow so an
 * external test suite (Appium) can locate a specific section regardless of
 * copy changes to the visible label.
 */
fun sectionKey(title: String): String =
    title.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

@Composable
fun MediaCardComponentUI(
    modifier: Modifier = Modifier,
    title: String,
    uiModel: MediaUIModel,
    isTappable: Boolean,
    onEvent: (MediaUiEvents) -> Unit,
    onScrollToEnd: () -> Unit = {}
) {
    val key = sectionKey(title)

    val lazyListState = rememberLazyListState()

    val isAtEnd by remember {
        derivedStateOf {
            val layoutInfo = lazyListState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems == 0) false
            else {
                val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisibleIndex >= totalItems - 1
            }
        }
    }

    LaunchedEffect(isAtEnd) {
        if (isAtEnd) {
            onScrollToEnd()
        }
    }

    Column(
        modifier = modifier
            .padding(horizontal = 4.dp)
    ) {
        Box(
            Modifier
                .height(30.dp)
        ) {
            Text(
                text = title,
                fontSize = 20.sp,
                textAlign = TextAlign.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(2.dp)
                    .testTag("section_title_$key")
            )
        }

        Spacer(modifier = Modifier.padding(horizontal = 0.dp))

        LazyRow(
            state = lazyListState,
            modifier = Modifier
                .testTag("media_row_$key")
        ) {
            items(
                uiModel.results,
                key = { it.id }
            ) { item ->
                MediaMovieItem(item, Modifier, isTappable = isTappable, onEvent = onEvent)
            }
        }
    }
}


@Composable
fun MediaMovieItem(
    result: MediaResults,
    modifier: Modifier,
    isTappable: Boolean,
    onEvent: (MediaUiEvents) -> Unit
) {
    /* Recomposition counter, DEBUG only. Climbing past 1 per scroll (Logcat "ComposePerf")
       usually means @Immutable got removed from MediaResults. */
    if (BuildConfig.DEBUG) {
        RecompositionCounter(tag = "movie_card_${result.id}")
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = modifier
            .width(105.dp)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .testTag("movie_card_${sectionKey(result.title)}")
            .conditional(isTappable) {
                border(1.dp, Color.LightGray, CardDefaults.shape)
            }
            .clickable(enabled = isTappable) {
                onEvent(MediaUiEvents.OnTapOfMovieCard(result))
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(6.dp)
        ) {
            AsyncImage(
                model = MediaUiFormatterUtils.posterUrl(result.imageUrl),
                modifier = Modifier
                    .height(105.dp)
                    .fillMaxWidth(),
                contentScale = ContentScale.Crop,
                contentDescription = "Movie Poster"
            )

            Text(
                text = result.title,
                color = Color.Black,
                textAlign = TextAlign.Start,
                fontSize = 11.sp,
                maxLines = 1,
                modifier = Modifier.padding(top = 4.dp, start = 2.dp)
            )

            Text(
                text = result.releaseDate,
                color = Color.Blue,
                textAlign = TextAlign.Start,
                fontSize = 8.sp,
                modifier = Modifier.padding(start = 2.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RatingBadge(
                    voteAverage = result.ratings,
                    onEvent = onEvent
                )

                Spacer(modifier = Modifier.weight(1f))

                InfoIcon(
                    modifier = Modifier.clickable {
                        onEvent(MediaUiEvents.OnTapOfDisclosure(result))
                    }

                )
            }

        }
    }
}


@Composable
fun InfoIcon(
    modifier: Modifier = Modifier,
    iconColor: Color = Color(0xFF0288D1)
) {
    Canvas(modifier = modifier.size(24.dp)) {
        val width = size.width
        val height = size.height
        val radius = width * 0.45f
        val strokeWidth = width * 0.08f

        drawCircle(
            color = iconColor,
            radius = radius,
            center = Offset(x = width / 2, y = height / 2),
            style = Stroke(width = strokeWidth)
        )

        drawCircle(
            color = iconColor,
            radius = strokeWidth * 0.6f,
            center = Offset(x = width / 2, y = height * 0.32f)
        )

        drawLine(
            color = iconColor,
            start = Offset(x = width / 2, y = height * 0.48f),
            end = Offset(x = width / 2, y = height * 0.72f),
            strokeWidth = strokeWidth
        )
    }
}

@Preview(showBackground = true)
@Composable
fun InfoIconPreview() {
    InfoIcon()
}

@Composable
fun RatingBadge(
    voteAverage: Double,
    modifier: Modifier = Modifier,
    onEvent: (MediaUiEvents) -> Unit
) {
    Box(
        modifier = modifier
            .background(
                color = Color.Black,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clickable {
                onEvent(MediaUiEvents.OnTapOfRating(voteAverage))
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = MediaUiFormatterUtils.rating(voteAverage),
            color = Color(0xFFFBC02D),
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        )
    }
}

@Composable
fun MediaDetailBottomSheetContent(
    media: MediaResults,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        AsyncImage(
            model = MediaUiFormatterUtils.posterUrl(media.imageUrl),
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop,
            contentDescription = media.title
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = media.title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "${media.releaseDate}  •  ${MediaUiFormatterUtils.rating(media.ratings)}",
            fontSize = 12.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = media.overview.orEmpty().ifBlank { "No description available." },
            fontSize = 14.sp
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MediaCardComponentUIPreview() {
    MediaCardComponentUI(
        title = "Trending this week",
        uiModel = PreviewConstants.previewMediaUiModel,
        isTappable = false,
        onEvent = {}
    )
}

@Preview(showBackground = true)
@Composable
fun MediaMovieItemPreview() {
    MediaMovieItem(
        result = PreviewConstants.previewMovie,
        modifier = Modifier,
        isTappable = true,
        onEvent = {}
    )
}

@Preview(showBackground = true)
@Composable
fun RatingBadgePreview() {
    RatingBadge(
        voteAverage = PreviewConstants.previewMovie.ratings,
        onEvent = {}
    )
}

@Preview(showBackground = true)
@Composable
fun MediaDetailBottomSheetContentPreview() {
    MediaDetailBottomSheetContent(media = PreviewConstants.previewMovie)
}

@Preview(showBackground = true)
@Composable
fun MediaDetailBottomSheetContentNoOverviewPreview() {
    MediaDetailBottomSheetContent(media = PreviewConstants.previewMovieNoOverview)
}
