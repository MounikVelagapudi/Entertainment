package com.example.entertainment.di

import com.example.entertainment.home.popularMovies.data.PopularMoviesRepoImpl
import com.example.entertainment.home.popularMovies.domain.PopularMoviesRepo
import com.example.entertainment.home.topRated.topRated.data.TopRatedRepoImpl
import com.example.entertainment.home.topRated.topRated.domain.TopRatedRepo
import com.example.entertainment.home.trendingMovies.data.TrendingRepoImpl
import com.example.entertainment.home.trendingMovies.domain.TrendingRepo
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTmDbRepository(impl: TrendingRepoImpl): TrendingRepo

    @Binds
    @Singleton
    abstract fun bindPopularMoviesRepo(impl: PopularMoviesRepoImpl):
            PopularMoviesRepo

    @Binds
    @Singleton
    abstract fun bindTopRatedRepo(impl: TopRatedRepoImpl):
            TopRatedRepo

}
