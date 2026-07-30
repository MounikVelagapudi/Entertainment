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
