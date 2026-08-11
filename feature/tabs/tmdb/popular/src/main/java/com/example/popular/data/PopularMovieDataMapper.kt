package com.example.popular.data

import com.example.entertainment.core.domain.Mapper
import com.example.entertainment.home.popularMovies.data.PopularMovieDTO
import com.example.entertainment.home.popularMovies.data.ResultDTO
import com.example.popular.domain.PopularMovie
import com.example.popular.domain.PopularMoviesDomainModel
import javax.inject.Inject

class PopularMovieDataMapper @Inject constructor() :
    Mapper<PopularMovieDTO, PopularMoviesDomainModel> {

    override suspend fun map(input: PopularMovieDTO): PopularMoviesDomainModel {

        return PopularMoviesDomainModel(
            page = input.page ?: 0,
            results = input.resultDTOS?.mapNotNull { it?.toDomain() } ?: emptyList(),
            totalPages = input.totalPages ?: 0,
            totalResults = input.totalResults ?: 0,
        )
    }

}

fun ResultDTO.toDomain(): PopularMovie {

    return PopularMovie(
        adult = this.adult ?: false,
        backdropPath = this.backdropPath ?: "",
        genreIds = this.genreIds?.filterNotNull() ?: emptyList(),
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
