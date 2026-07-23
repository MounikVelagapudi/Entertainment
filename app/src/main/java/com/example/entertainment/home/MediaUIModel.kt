package com.example.entertainment.home


interface MediaItem {
    val id: Int
    val title: String
    val releaseDate: String
    val posterPath: String
    val voteAverage: Double
}

interface MediaListDomainModel {
    val totalResults: Int
    val totalPages: Int
    val results: List<MediaItem>?
}


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
