package com.example.entertainment.detailsScreen

import com.example.toprated.domain.TopRatedMovie
import com.example.toprated.domain.TopRatedUseCase
import io.mockk.coEvery
import io.mockk.mockk

class MediaDetailsViewModelBuilder {

    val topRatedUseCase = mockk<TopRatedUseCase>()

    /* null = nothing cached for that id. */
    fun withCachedTopRated(id: Int, movie: TopRatedMovie?) = apply {
        coEvery { topRatedUseCase.getById(id) } returns movie
    }

    fun build() = MediaDetailsViewModel(topRatedUseCase = topRatedUseCase)
}
