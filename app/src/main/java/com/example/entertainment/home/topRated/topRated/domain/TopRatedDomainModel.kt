package com.example.entertainment.home.topRated.topRated.domain

import com.example.entertainment.home.MediaItem
import com.example.entertainment.home.MediaListDomainModel


data class TopRatedDomainModel(
    val page: Int = 0,
    override val results: List<TopRatedMovie> = emptyList(),
    override val totalPages: Int = 0,
    override val totalResults: Int = 0
): MediaListDomainModel

data class TopRatedMovie(
    override val id: Int,
    override val title: String,
    val originalTitle: String = "",
    override val posterPath: String = "",
    val backdropPath: String = "",
    override val releaseDate: String = "",
    val originalLanguage: String = "",
    val popularity: Double = 0.0,
    override val voteAverage: Double = 0.0,
    val voteCount: Int = 0,
    val isAdult: Boolean = false,
    val isVideo: Boolean = false,
    val genreIds: List<Int> = emptyList(),
): MediaItem