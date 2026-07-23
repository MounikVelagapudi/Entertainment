package com.example.entertainment

import java.util.Locale

const val TMDB_POSTER_BASE_URL = "https://image.tmdb.org/t/p/w342"


object MediaUiFormatterUtils {

    fun posterUrl(path: String): String? =
        path.takeIf { it.isNotEmpty() }?.let {
            "$TMDB_POSTER_BASE_URL$it"
        }

    fun rating(voteAverage: Double): String =
        String.format(Locale.US, "%.1f", voteAverage)
}