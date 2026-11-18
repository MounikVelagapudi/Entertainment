package com.example.popular.testdata

import com.example.entertainment.core.home.popularMovies.data.PopularMovieDTO
import com.example.entertainment.core.home.popularMovies.data.ResultDTO

fun aPopularResultDto(id: Int = 1, title: String = "Movie $id") = ResultDTO(id = id, title = title)

/* One network page holding a movie per id. */
fun aPopularPageDto(page: Int = 1, ids: List<Int> = listOf(1)) = PopularMovieDTO(
    page = page,
    resultDTOS = ids.map { aPopularResultDto(id = it) },
    totalPages = 5,
    totalResults = 100,
)
