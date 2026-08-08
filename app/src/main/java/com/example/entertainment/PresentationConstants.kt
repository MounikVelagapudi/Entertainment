package com.example.entertainment

import com.example.entertainment.home.trendingMovies.data.ResultDTO
import com.example.trending.domain.TrendingMoviesDomainModel
import com.example.trending.domain.TrendingMovie

val previewResultDTOs = listOf(
    ResultDTO(
        adult = false,
        backdrop_path = "/backdrop1.jpg",
        genre_ids = listOf(28, 12),
        id = 1,
        media_type = "movie",
        original_language = "en",
        original_title = "The Great Adventure",
        overview = "A thrilling journey across unknown lands.",
        popularity = 123.4,
        poster_path = "/poster1.jpg",
        release_date = "2024-05-10",
        softcore = false,
        title = "The Great Adventure",
        video = false,
        vote_average = 7.8,
        vote_count = 1523
    ),
    ResultDTO(
        adult = false,
        backdrop_path = "/backdrop2.jpg",
        genre_ids = listOf(35, 18),
        id = 2,
        media_type = "movie",
        original_language = "en",
        original_title = "City Lights Reborn",
        overview = "A heartfelt drama set in a bustling metropolis.",
        popularity = 98.2,
        poster_path = "/poster2.jpg",
        release_date = "2023-11-22",
        softcore = false,
        title = "City Lights Reborn",
        video = false,
        vote_average = 6.9,
        vote_count = 842
    ),
    ResultDTO(
        adult = false,
        backdrop_path = "/backdrop3.jpg",
        genre_ids = listOf(27, 9648),
        id = 3,
        media_type = "movie",
        original_language = "en",
        original_title = "Silent Whispers",
        overview = "A mystery that unravels in the dead of night.",
        popularity = 76.5,
        poster_path = "/poster3.jpg",
        release_date = "2025-02-14",
        softcore = false,
        title = "Silent Whispers",
        video = false,
        vote_average = 8.2,
        vote_count = 2010
    )
)

val previewTmDbDTO = TrendingMoviesDomainModel(
    page = 1,
    results = previewResultDTOs.map {
        TrendingMovie(
            adult = it.adult,
            backdropPath = it.backdrop_path,
            genreIds = it.genre_ids,
            id = it.id,
            mediaType = it.media_type,
            originalLanguage = it.original_language,
            originalTitle = it.original_title,
            popularity = it.popularity,
            posterPath = it.poster_path,
            releaseDate = it.release_date,
            title = it.title,
            video = it.video,
            voteAverage = it.vote_average,
            voteCount = it.vote_count
        )
    },
    totalPages = 1,
    totalResults = previewResultDTOs.size
)
