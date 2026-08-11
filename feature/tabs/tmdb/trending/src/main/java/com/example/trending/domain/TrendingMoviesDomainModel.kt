package com.example.trending.domain

import com.example.entertainment.home.MediaItem
import com.example.entertainment.home.MediaListDomainModel

data class TrendingMoviesDomainModel(
    val page: Int,
    override val results: List<TrendingMovie>?,
    override val totalPages: Int,
    override val totalResults: Int
) : MediaListDomainModel

data class TrendingMovie(
    val adult: Boolean,
    val backdropPath: String,
    val genreIds: List<Int>,
    override val id: Int,
    val mediaType: String,
    val originalLanguage: String,
    val originalTitle: String,
    val popularity: Double,
    override val posterPath: String,
    override val releaseDate: String,
    override val title: String,
    val video: Boolean,
    override val voteAverage: Double,
    val voteCount: Int
) : MediaItem