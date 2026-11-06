package com.example.entertainment.core.data.local.toprated

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local cache row for a single top-rated movie. This table is the offline-first
 * source of truth for the TopRated screen -- the UI reads from here, never
 * directly from the network response.
 */
@Entity(tableName = "top_rated_movies")
data class TopRatedMovieEntity(
    @PrimaryKey val id: Int,
    val title: String,
    val originalTitle: String,
    val posterPath: String,
    val backdropPath: String,
    val releaseDate: String,
    val originalLanguage: String,
    val popularity: Double,
    val voteAverage: Double,
    val voteCount: Int,
    val isAdult: Boolean,
    val isVideo: Boolean,
    val genreIds: List<Int>,
    /* Position within the last API response -- SQLite doesn't guarantee row order otherwise. */
    val sortOrder: Int,
)
