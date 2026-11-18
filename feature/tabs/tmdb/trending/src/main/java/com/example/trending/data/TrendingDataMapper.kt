package com.example.trending.data

import com.example.entertainment.core.data.local.trending.TrendingMovieEntity
import com.example.entertainment.core.domain.Mapper
import com.example.entertainment.core.home.trendingMovies.data.ResultDTO
import com.example.entertainment.core.home.trendingMovies.data.TrendingMovieDTO
import com.example.trending.domain.TrendingMoviesDomainModel
import com.example.trending.domain.TrendingMovie
import javax.inject.Inject
import kotlin.Boolean

class TrendingDataMapper @Inject constructor() :
    Mapper<TrendingMovieDTO, TrendingMoviesDomainModel> {

    override suspend fun map(input: TrendingMovieDTO): TrendingMoviesDomainModel {
        return TrendingMoviesDomainModel(
            page = input.page ?: 0,
            results = input.resultDTOS?.mapNotNull { it?.toDomain() } ?: emptyList(),
            totalPages = input.total_pages ?: 0,
            totalResults = input.total_results ?: 0
        )
    }

    private fun ResultDTO.toDomain(): TrendingMovie? {
        val movieId = id ?: return null

        return TrendingMovie(
            adult = adult ?: false,
            backdropPath = backdrop_path ?: "",
            genreIds = genre_ids?.filterNotNull() ?: emptyList(),
            id = movieId,
            mediaType = media_type ?: "",
            originalLanguage = original_language ?: "",
            originalTitle = original_title ?: "",
            overview = overview ?: "",
            popularity = popularity ?: 0.0,
            posterPath = poster_path ?: "",
            releaseDate = release_date ?: "",
            title = title ?: "",
            video = video ?: false,
            voteAverage = vote_average ?: 0.0,
            voteCount = vote_count ?: 0
        )
    }
}

fun TrendingMovie.toEntity(sortOrder: Int): TrendingMovieEntity {

    return TrendingMovieEntity(
        id = id,
        title = title,
        originalTitle = originalTitle,
        posterPath = posterPath,
        backdropPath = backdropPath,
        releaseDate = releaseDate,
        originalLanguage = originalLanguage,
        popularity = popularity,
        voteAverage = voteAverage,
        voteCount = voteCount,
        genreIds = genreIds,
        sortOrder = sortOrder,
        adult = adult,
        overview = overview,
        video = video,
        mediaType = mediaType
    )

}

fun TrendingMovieEntity.toDomain(): TrendingMovie {
    return TrendingMovie(
        adult = this.adult,
        backdropPath = this.backdropPath,
        genreIds = this.genreIds,
        id = this.id,
        originalLanguage = this.originalLanguage,
        originalTitle = this.originalTitle,
        popularity = this.popularity,
        posterPath = this.posterPath,
        releaseDate = this.releaseDate,
        title = this.title,
        video = this.video,
        voteAverage = this.voteAverage,
        voteCount = this.voteCount,
        mediaType = this.mediaType,
        overview = this.overview
    )
}