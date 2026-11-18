package com.example.entertainment.home

import androidx.lifecycle.SavedStateHandle
import androidx.work.WorkManager
import com.example.entertainment.core.domain.Resource
import com.example.entertainment.core.domain.Result
import com.example.entertainment.core.domain.TRPError
import com.example.entertainment.testdata.aPopularPage
import com.example.entertainment.testdata.aTopRatedPage
import com.example.entertainment.testdata.aTrendingPage
import com.example.popular.domain.GetPopularMovieUseCase
import com.example.popular.domain.PopularMoviesDomainModel
import com.example.presentation.presentation.HomeViewModel
import com.example.toprated.domain.TopRatedDomainModel
import com.example.toprated.domain.TopRatedUseCase
import com.example.trending.domain.TrendingMoviesDomainModel
import com.example.trending.domain.TrendingUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow

/* Builds a HomeViewModel over mocked use cases. Every section starts as Loading with a
   successful refresh, since observe() is collected at construction; tests override what they need. */
class HomeViewModelBuilder {

    val trendingUseCase = mockk<TrendingUseCase>()
    val popularMovieUseCase = mockk<GetPopularMovieUseCase>()
    val topRatedUseCase = mockk<TopRatedUseCase>()

    /* Relaxed: init{} schedules periodic sync, which these tests don't care about. */
    private val workManager = mockk<WorkManager>(relaxed = true)

    init {
        withTrending(Resource.Loading)
        withTrendingRefresh(Result.Success(aTrendingPage()))
        withPopular(Resource.Loading)
        withPopularRefresh(Result.Success(aPopularPage()))
        withTopRated(Resource.Loading)
        withTopRatedRefresh(Result.Success(aTopRatedPage()))
    }

    fun withTrending(resource: Resource<TrendingMoviesDomainModel>) = apply {
        every { trendingUseCase.observe() } returns MutableStateFlow(resource)
    }

    fun withTrendingRefresh(result: Result<TrendingMoviesDomainModel, TRPError>) = apply {
        coEvery { trendingUseCase.refresh() } returns result
    }

    fun withPopular(resource: Resource<PopularMoviesDomainModel>) = apply {
        every { popularMovieUseCase.observe() } returns MutableStateFlow(resource)
    }

    fun withPopularRefresh(result: Result<PopularMoviesDomainModel, TRPError>) = apply {
        coEvery { popularMovieUseCase.refresh(any()) } returns result
    }

    fun withTopRated(resource: Resource<TopRatedDomainModel>) = apply {
        every { topRatedUseCase.observe() } returns MutableStateFlow(resource)
    }

    fun withTopRatedRefresh(result: Result<TopRatedDomainModel, TRPError>) = apply {
        coEvery { topRatedUseCase.refresh(any()) } returns result
    }

    fun build() = HomeViewModel(
        trendingUseCase = trendingUseCase,
        popularMovieUseCase = popularMovieUseCase,
        topRatedUseCase = topRatedUseCase,
        workManager = workManager,
        savedStateHandle = SavedStateHandle(),
    )
}
