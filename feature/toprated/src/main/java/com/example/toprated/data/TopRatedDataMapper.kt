package com.example.toprated.data

import com.example.entertainment.core.domain.Mapper
import com.example.toprated.domain.TopRatedDomainModel
import com.example.toprated.domain.TopRatedMovie
import javax.inject.Inject

class TopRatedDataMapper @Inject constructor() : Mapper<TopRatedDTO, TopRatedDomainModel> {

    override suspend fun map(topRatedDTO: TopRatedDTO): TopRatedDomainModel {

        return TopRatedDomainModel(
            page = topRatedDTO.page ?: 0,
            totalPages = topRatedDTO.totalPages ?: 0,
            totalResults = topRatedDTO.totalResults ?: 0,
            results = topRatedDTO.results?.mapNotNull { mapMovieToDomain(it) } ?: emptyList()
        )
    }

    private fun mapMovieToDomain(result: Result?): TopRatedMovie? {
        if (result == null) return null

        // Critical safeguard: Skip entries missing a valid ID
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