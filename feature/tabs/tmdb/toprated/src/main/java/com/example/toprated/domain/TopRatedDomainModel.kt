package com.example.toprated.domain

import com.example.entertainment.core.home.MediaItem
import com.example.entertainment.core.home.MediaListDomainModel


data class TopRatedDomainModel(
    val page: Int,
    override val results: List<TopRatedMovie>,
    override val totalPages: Int,
    override val totalResults: Int
): MediaListDomainModel

data class TopRatedMovie(
    override val id: Int,
    override val title: String,
    val originalTitle: String,
    override val posterPath: String,
    val backdropPath: String,
    override val releaseDate: String,
    val originalLanguage: String,
    val popularity: Double,
    override val voteAverage: Double,
    val voteCount: Int,
    val isAdult: Boolean,
    val isVideo: Boolean,
    val genreIds: List<Int>,
): MediaItem