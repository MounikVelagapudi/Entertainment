package com.example.entertainment.testdata

import com.example.entertainment.core.presentation.reusable_components.MediaResults
import com.example.popular.domain.PopularMovie
import com.example.popular.domain.PopularMoviesDomainModel
import com.example.toprated.domain.TopRatedDomainModel
import com.example.toprated.domain.TopRatedMovie
import com.example.trending.domain.TrendingMovie
import com.example.trending.domain.TrendingMoviesDomainModel

/* Test data builders. Override only what a test asserts on. Default ids differ per section
   (trending 1, popular 2, top rated 3) so a mixed-up section shows up in assertions. */

fun aTrendingMovie(
    id: Int = 1,
    title: String = "Trending Movie",
    posterPath: String = "/trending_poster.jpg",
    releaseDate: String = "2020-01-01",
    voteAverage: Double = 7.0,
) = TrendingMovie(
    adult = false,
    backdropPath = "/backdrop.jpg",
    genreIds = listOf(28),
    id = id,
    mediaType = "movie",
    originalLanguage = "en",
    originalTitle = title,
    overview = "A trending movie.",
    popularity = 10.0,
    posterPath = posterPath,
    releaseDate = releaseDate,
    title = title,
    video = false,
    voteAverage = voteAverage,
    voteCount = 100,
)

fun aTrendingPage(vararg movies: TrendingMovie = arrayOf(aTrendingMovie())) = TrendingMoviesDomainModel(
    page = 1,
    results = movies.toList(),
    totalPages = 1,
    totalResults = movies.size,
)

fun aPopularMovie(
    id: Int = 2,
    title: String = "Popular Movie",
    posterPath: String = "/popular_poster.jpg",
    releaseDate: String = "2021-02-02",
    voteAverage: Double = 8.0,
) = PopularMovie(
    adult = false,
    backdropPath = "/backdrop.jpg",
    genreIds = listOf(12),
    id = id,
    originalLanguage = "en",
    originalTitle = title,
    overview = "A popular movie.",
    popularity = 20.0,
    posterPath = posterPath,
    releaseDate = releaseDate,
    title = title,
    video = false,
    voteAverage = voteAverage,
    voteCount = 200,
)

fun aPopularPage(vararg movies: PopularMovie = arrayOf(aPopularMovie())) = PopularMoviesDomainModel(
    page = 1,
    results = movies.toList(),
    totalPages = 1,
    totalResults = movies.size,
)

fun aTopRatedMovie(
    id: Int = 3,
    title: String = "Inception",
    posterPath: String = "/poster.jpg",
    releaseDate: String = "2010-07-16",
    voteAverage: Double = 8.8,
) = TopRatedMovie(
    id = id,
    title = title,
    originalTitle = title,
    posterPath = posterPath,
    backdropPath = "/backdrop.jpg",
    releaseDate = releaseDate,
    originalLanguage = "en",
    popularity = 123.4,
    voteAverage = voteAverage,
    voteCount = 30000,
    isAdult = false,
    isVideo = false,
    genreIds = listOf(28, 12),
)

fun aTopRatedPage(vararg movies: TopRatedMovie = arrayOf(aTopRatedMovie())) = TopRatedDomainModel(
    page = 1,
    results = movies.toList(),
    totalPages = 1,
    totalResults = movies.size,
)

fun aMediaResults(
    id: Int = 1,
    title: String = "Inception",
    releaseDate: String = "2010-07-16",
    imageUrl: String = "https://example.com/inception.jpg",
    ratings: Double = 8.8,
) = MediaResults(
    title = title,
    releaseDate = releaseDate,
    imageUrl = imageUrl,
    id = id,
    ratings = ratings,
)
