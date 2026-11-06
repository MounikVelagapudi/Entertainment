package com.example.entertainment.core.home.trendingMovies.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TrendingMovieDTO(
    val page: Int? = null,
    @SerialName("results")
    val resultDTOS: List<ResultDTO?>? = null,
    val total_pages: Int? = null,
    val total_results: Int? = null
)

@Serializable
data class ResultDTO(
    val adult: Boolean? = null,
    val backdrop_path: String? = null,
    val genre_ids: List<Int?>? = null,
    val id: Int? = null,
    val media_type: String? = null,
    val original_language: String? = null,
    val original_title: String? = null,
    val overview: String? = null,
    val popularity: Double? = null,
    val poster_path: String? = null,
    val release_date: String? = null,
    val softcore: Boolean? = null,
    val title: String? = null,
    val video: Boolean? = null,
    val vote_average: Double? = null,
    val vote_count: Int? = null
)
