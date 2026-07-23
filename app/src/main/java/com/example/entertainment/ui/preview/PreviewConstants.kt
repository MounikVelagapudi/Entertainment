package com.example.entertainment.ui.preview

import com.example.entertainment.home.MediaResults
import com.example.entertainment.home.MediaUIModel

object PreviewConstants {

    val previewMovie = MediaResults(
        title = "The Great Adventure",
        releaseDate = "2024-05-10",
        imageUrl = "/poster1.jpg",
        id = 1,
        ratings = 7.8,
        overview = "A thrilling journey across unknown lands, testing the limits of courage and friendship."
    )

    val previewMovieNoOverview = MediaResults(
        title = "Beyond the Horizon",
        releaseDate = "2025-01-18",
        imageUrl = "/poster3.jpg",
        id = 3,
        ratings = 8.2,
        overview = null
    )

    val previewMovieList = listOf(
        previewMovie,
        MediaResults(
            title = "Midnight in the City",
            releaseDate = "2023-11-02",
            imageUrl = "/poster2.jpg",
            id = 2,
            ratings = 6.4,
            overview = "A detective unravels a decades-old mystery in the neon-lit streets of downtown."
        ),
        previewMovieNoOverview
    )

    val previewMediaUiModel = MediaUIModel(
        totalResults = previewMovieList.size,
        totalPages = 1,
        results = previewMovieList
    )
}
