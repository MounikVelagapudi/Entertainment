package com.example.popular.data

import com.example.entertainment.core.data.local.popular.PopularMovieEntity
import com.example.entertainment.core.domain.Mapper
import com.example.entertainment.core.home.popularMovies.data.PopularMovieDTO
import com.example.entertainment.core.home.popularMovies.data.ResultDTO
import com.example.entertainment.core.home.toprated.data.Result
import com.example.popular.domain.PopularMovie
import com.example.popular.domain.PopularMoviesDomainModel
import java.nio.ByteOrder
import javax.inject.Inject
import kotlin.Int

class PopularMovieDataMapper @Inject constructor() :
    Mapper<PopularMovieDTO, PopularMoviesDomainModel> {

    override suspend fun map(input: PopularMovieDTO): PopularMoviesDomainModel {

        return PopularMoviesDomainModel(
            page = input.page ?: 0,
            results = input.resultDTOS?.mapNotNull { mapMovieToDomain(it) } ?: emptyList(),
            totalPages = input.totalPages ?: 0,
            totalResults = input.totalResults ?: 0,
        )
    }
}

private fun mapMovieToDomain(result: ResultDTO?): PopularMovie? {
    if (result == null) return null

    val movieId = result.id ?: return null

    return PopularMovie(
        id = movieId,
        title = result.title ?: "",
        originalTitle = result.originalTitle ?: "",
        posterPath = result.posterPath ?: "",
        backdropPath = result.backdropPath ?: "",
        releaseDate = result.releaseDate ?: "",
        originalLanguage = result.originalLanguage ?: "",
        popularity = result.popularity ?: 0.0,
        voteAverage = result.voteAverage ?: 0.0,
        voteCount = result.voteCount ?: 0,
        adult = result.adult ?: false,
        video = result.video ?: false,
        genreIds = result.genreIds?.filterNotNull() ?: emptyList(),
        overview = result.overview ?: ""
    )
}


fun PopularMovie.toEntity(sortOrder: Int): PopularMovieEntity {
    return PopularMovieEntity(
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
        video = video
    )
}


fun PopularMovieEntity.toDomain(): PopularMovie {

    return PopularMovie(
        adult = this.adult ?: false,
        backdropPath = this.backdropPath ?: "",
        genreIds = this.genreIds ?: emptyList(),
        id = this.id ?: 0,
        originalLanguage = this.originalLanguage ?: "",
        originalTitle = this.originalTitle ?: "",
        overview = this.overview ?: "",
        popularity = this.popularity ?: 0.0,
        posterPath = this.posterPath ?: "",
        releaseDate = this.releaseDate ?: "",
        title = this.title ?: "",
        video = this.video ?: false,
        voteAverage = this.voteAverage ?: 0.0,
        voteCount = this.voteCount ?: 0
    )
}
