package com.example.toprated.testdata

import com.example.entertainment.core.data.local.toprated.TopRatedMovieEntity
import com.example.entertainment.core.home.toprated.data.Result as TopRatedResultDto
import com.example.entertainment.core.home.toprated.data.TopRatedDTO
import com.example.toprated.domain.TopRatedDomainModel
import com.example.toprated.domain.TopRatedMovie

/* Test data builders for every Top Rated layer: network DTO, Room entity, domain model. */

fun aTopRatedResultDto(id: Int = 1, title: String = "Inception") = TopRatedResultDto(
    adult = false,
    backdropPath = "/backdrop.jpg",
    genreIds = listOf(28, 12),
    id = id,
    originalLanguage = "en",
    originalTitle = title,
    overview = "A thief who steals secrets.",
    popularity = 123.4,
    posterPath = "/poster.jpg",
    releaseDate = "2010-07-16",
    softcore = false,
    title = title,
    video = false,
    voteAverage = 8.8,
    voteCount = 30000,
)

fun aTopRatedDto(vararg results: TopRatedResultDto = arrayOf(aTopRatedResultDto())) = TopRatedDTO(
    page = 1,
    totalPages = 1,
    totalResults = results.size,
    results = results.toList(),
)

fun aTopRatedEntity(id: Int = 1, title: String = "Inception", sortOrder: Int = 0) = TopRatedMovieEntity(
    id = id,
    title = title,
    originalTitle = title,
    posterPath = "/poster.jpg",
    backdropPath = "/backdrop.jpg",
    releaseDate = "2010-07-16",
    originalLanguage = "en",
    popularity = 123.4,
    voteAverage = 8.8,
    voteCount = 30000,
    isAdult = false,
    isVideo = false,
    genreIds = listOf(28, 12),
    sortOrder = sortOrder,
)

fun aTopRatedMovie(id: Int = 1, title: String = "Inception") = TopRatedMovie(
    backdropPath = "/backdrop.jpg",
    genreIds = listOf(28, 12),
    id = id,
    originalLanguage = "en",
    originalTitle = title,
    popularity = 123.4,
    posterPath = "/poster.jpg",
    releaseDate = "2010-07-16",
    title = title,
    voteAverage = 8.8,
    voteCount = 30000,
    isAdult = false,
    isVideo = false,
)

fun aTopRatedPage(vararg movies: TopRatedMovie = arrayOf(aTopRatedMovie())) = TopRatedDomainModel(
    page = 1,
    results = movies.toList(),
    totalPages = 1,
    totalResults = movies.size,
)
