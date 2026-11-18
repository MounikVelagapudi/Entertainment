package com.example.toprated

import com.example.entertainment.core.domain.Resource
import com.example.entertainment.core.domain.Result
import com.example.entertainment.core.domain.TRPError
import com.example.toprated.domain.TopRatedDomainModel
import com.example.toprated.domain.TopRatedMovie
import com.example.toprated.domain.TopRatedRepo
import com.example.toprated.domain.TopRatedUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf

class TopRatedUseCaseBuilder {

    val topRatedRepo = mockk<TopRatedRepo>()

    fun withObserved(vararg emissions: Resource<TopRatedDomainModel>) = apply {
        every { topRatedRepo.observeTopRated() } returns flowOf(*emissions)
    }

    fun withRefreshResult(page: Int, result: Result<TopRatedDomainModel, TRPError>) = apply {
        coEvery { topRatedRepo.refreshTopRatedApi(page) } returns result
    }

    fun withMovieById(id: Int, movie: TopRatedMovie?) = apply {
        coEvery { topRatedRepo.getById(id) } returns movie
    }

    fun build() = TopRatedUseCase(topRatedRepo)
}
