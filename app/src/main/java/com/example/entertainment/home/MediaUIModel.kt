package com.example.entertainment.home


data class MediaUIModel(
    val totalResults: Int,
    val totalPages: Int,
    val results: List<MediaResults>
)

data class MediaResults(
    val title: String,
    val releaseDate: String,
    val imageUrl: String,
    val id: Int,
    val ratings: Double,
    val overview: String? = null
)
