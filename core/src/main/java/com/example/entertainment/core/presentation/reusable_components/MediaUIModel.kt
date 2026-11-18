package com.example.entertainment.core.presentation.reusable_components

import androidx.compose.runtime.Immutable


@Immutable
data class MediaUIModel(
    val totalResults: Int,
    var totalPages: Int,
    val results: List<MediaResults>
)

@Immutable
data class MediaResults(
    val title: String,
    val releaseDate: String,
    val imageUrl: String,
    val id: Int,
    val ratings: Double,
    val overview: String? = null
)
