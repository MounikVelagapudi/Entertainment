package com.example.entertainment.di

import com.example.popular.data.PopularMoviesRepoImpl
import com.example.popular.domain.PopularMoviesRepo
import com.example.entertainment.home.trendingMovies.data.TrendingRepoImpl
import com.example.entertainment.home.trendingMovies.domain.TrendingRepo
import com.example.toprated.data.TopRatedRepoImpl
import com.example.toprated.domain.TopRatedRepo
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
