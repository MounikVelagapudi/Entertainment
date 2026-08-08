package com.example.presentation.presentation

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.entertainment.MediaUiEvents
import com.example.entertainment.MediaUiFormatterUtils
import com.example.entertainment.ui.preview.PreviewConstants
import com.example.presentation.BuildConfig
import com.example.presentation.presentation.performance.RecompositionCounter

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
fun MediaComposableUI(
    modifier: Modifier = Modifier,
    title: String,
    uiModel: MediaUIModel,
    isTappable: Boolean,
    onEvent: (MediaUiEvents) -> Unit
) {
    val key = sectionKey(title)

    Column(
        modifier = modifier
            .padding(horizontal = 4.dp)
    ) {
        Box(
            Modifier
                .height(50.dp)
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

        LazyRow(modifier = Modifier.testTag("media_row_$key")) {
            items(
                uiModel.results,
                key = { it.id } // Stable key prevents recomposition
                /* IMP: key = { it.id } is not mandatory, but this will avoid recomposition when one of the item in the list changes,
                else it keeps recomposing even if one of the item gets updates in the list */
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
    // PRACTICAL DEBUG TOOL: logs how many times THIS specific card recomposes.
    // Filter Logcat on "ComposePerf" and scroll the Home screen's LazyRows.
    // Expected: each card recomposes once (its first composition) and then
    // stays put while you scroll, because MediaComposableUI's LazyRow passes
    // a stable `key = { it.id }` and MediaResults is @Immutable. Try
    // temporarily removing @Immutable from MediaResults in MediaUIModel.kt
    // and re-running -- you should see these counts climb on every scroll
    // frame instead of staying at 1.
    if(BuildConfig.DEBUG) {
        RecompositionCounter(tag = "movie_card_${result.id}")
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = modifier
            .width(105.dp) // Define horizontal boundaries at the card level
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .testTag("movie_card_${sectionKey(result.title)}")
            .conditional(isTappable) { // PREP: how to handle custom modifiers that can provide a border on tap of the card
                border(1.dp, Color.LightGray, CardDefaults.shape)
            }
            .clickable(enabled = isTappable) {
                onEvent(MediaUiEvents.OnTapOfMovieCard(result))
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp) // Locks the card height to your requested size
                .padding(6.dp)
        ) {
            // 1. Movie Poster Image (Height adjusted to leave room for your text)
            AsyncImage(
                model = MediaUiFormatterUtils.posterUrl(result.imageUrl),
                modifier = Modifier
                    .height(105.dp)
                    .fillMaxWidth(),
                contentScale = ContentScale.Crop,
                contentDescription = "Movie Poster"
            )

            // 2. Movie Title Text
            Text(
                text = result.title,
                color = Color.Black,
                textAlign = TextAlign.Start,
                fontSize = 11.sp,
                maxLines = 1, // Prevents text truncation issues
                modifier = Modifier.padding(top = 4.dp, start = 2.dp)
            )

            // 3. Movie Release Date Text
            Text(
                text = result.releaseDate,
                color = Color.Blue,
                textAlign = TextAlign.Start,
                fontSize = 8.sp,
                modifier = Modifier.padding(start = 2.dp)
            )

            // 4. Dynamic Spacer pushes everything below it to the absolute bottom
            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 5. Rating Badge sitting cleanly at the bottom edge
                RatingBadge(
                    voteAverage = result.ratings,
                    onEvent = onEvent
                )

                // 3. This spacer consumes all remaining space, pushing the icon to the right
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
    iconColor: Color = Color(0xFF0288D1) // Standard information blue
) {
    // 24.dp is the standard size for material action icons
    Canvas(modifier = modifier.size(24.dp)) {
        val width = size.width
        val height = size.height
        val radius = width * 0.45f
        val strokeWidth = width * 0.08f

        // 1. Draw outer boundary circle
        drawCircle(
            color = iconColor,
            radius = radius,
            center = Offset(x = width / 2, y = height / 2),
            style = Stroke(width = strokeWidth)
        )

        // 2. Draw information dot (the top part of the "i")
        drawCircle(
            color = iconColor,
            radius = strokeWidth * 0.6f,
            center = Offset(x = width / 2, y = height * 0.32f)
        )

        // 3. Draw vertical stem (the bottom part of the "i")
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
            // Small rounded corners for the badge container
            .background(
                color = Color.Black,
                shape = RoundedCornerShape(4.dp)
            )
            // Padding inside the black badge around the text
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clickable {
                onEvent(MediaUiEvents.OnTapOfRating(voteAverage))
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            // Safely format the double to 1 decimal place (e.g., 8.6)
            text = MediaUiFormatterUtils.rating(voteAverage),
            // Use yellow color to match your UI image exactly
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
fun MediaComposableUIPreview() {
    MediaComposableUI(
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
