package com.example.entertainment.home.trendingMovies.data

import com.example.entertainment.core.domain.Mapper
import com.example.entertainment.home.trendingMovies.domain.TrendingMoviesDomainModel
import com.example.entertainment.home.trendingMovies.domain.TrendingMovie
import javax.inject.Inject

class TrendingMapper @Inject constructor() : Mapper<TrendingMovieDTO, TrendingMoviesDomainModel> {

    override suspend fun map(input: TrendingMovieDTO): TrendingMoviesDomainModel {
        return TrendingMoviesDomainModel(
            page = input.page,
            results = input.resultDTOS?.map { it.toDomain() },
            totalPages = input.total_pages,
            totalResults = input.total_results
        )
    }

    private fun ResultDTO.toDomain(): TrendingMovie {
        return TrendingMovie(
            adult = adult,
            backdropPath = backdrop_path,
            genreIds = genre_ids,
            id = id,
            mediaType = media_type,
            originalLanguage = original_language,
            originalTitle = original_title,
            popularity = popularity,
            posterPath = poster_path,
            releaseDate = release_date,
            title = title,
            video = video,
            voteAverage = vote_average,
            voteCount = vote_count
        )
    }
}
