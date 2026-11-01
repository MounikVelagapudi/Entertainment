package com.example.toprated.data

import com.example.entertainment.core.data.local.toprated.TopRatedMovieEntity
import com.example.entertainment.core.home.toprated.data.Result
import com.example.entertainment.core.home.toprated.data.TopRatedDTO
import com.example.entertainment.core.domain.Mapper
import com.example.toprated.domain.TopRatedDomainModel
import com.example.toprated.domain.TopRatedMovie
import javax.inject.Inject

class TopRatedDataMapper @Inject constructor() : Mapper<TopRatedDTO, TopRatedDomainModel> {

    /*DTO <-> Domain entity mapping for the initial screening before adding it to Offline DAO, to eliminate any null values or issues with backend API response*/
    override suspend fun map(input: TopRatedDTO): TopRatedDomainModel {

        return TopRatedDomainModel(
            page = input.page ?: 0,
            totalPages = input.totalPages ?: 0,
            totalResults = input.totalResults ?: 0,
            results = input.results?.mapNotNull { mapMovieToDomain(it) } ?: emptyList()
        )
    }

    private fun mapMovieToDomain(result: Result?): TopRatedMovie? {
        if (result == null) return null

        val movieId = result.id ?: return null

        return TopRatedMovie(
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
            isAdult = result.adult ?: false,
            isVideo = result.video ?: false,
            genreIds = result.genreIds?.filterNotNull() ?: emptyList()
        )
    }

}

/**
 * Domain <-> Room entity mapping for the offline cache. Kept as plain extension
 * functions (not part of the [Mapper] interface) since they're synchronous and
 * local to this module -- no network/IO involved.
 */
fun TopRatedMovie.toEntity(sortOrder: Int): TopRatedMovieEntity =
    TopRatedMovieEntity(
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
        isAdult = isAdult,
        isVideo = isVideo,
        genreIds = genreIds,
        sortOrder = sortOrder
    )

fun TopRatedMovieEntity.toDomain(): TopRatedMovie =
    TopRatedMovie(
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
        isAdult = isAdult,
        isVideo = isVideo,
        genreIds = genreIds
    )

fun List<TopRatedMovieEntity>.toDomainModel(): TopRatedDomainModel =
    TopRatedDomainModel(
        page = 0,
        results = map { it.toDomain() },
        totalPages = 0,
        totalResults = size
    )