package com.example.entertainment.home.trendingMovies.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TrendingMovieDTO(
    val page: Int = 0,
    @SerialName("results")
    val resultDTOS: List<ResultDTO>? = null,
    val total_pages: Int = 0,
    val total_results: Int = 0
)

@Serializable
data class ResultDTO(
    val adult: Boolean = false,
    val backdrop_path: String = "",
    val genre_ids: List<Int> = emptyList(),
    val id: Int = 0,
    val media_type: String = "",
    val original_language: String = "",
    val original_title: String = "",
    val overview: String = "",
    val popularity: Double = 0.0,
    val poster_path: String = "",
    val release_date: String = "",
    val softcore: Boolean = false,
    val title: String = "",
    val video: Boolean = false,
    val vote_average: Double = 0.0,
    val vote_count: Int = 0
)
