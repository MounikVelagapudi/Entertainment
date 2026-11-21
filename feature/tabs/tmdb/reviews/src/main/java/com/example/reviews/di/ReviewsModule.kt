package com.example.reviews.di

import com.example.reviews.data.ReviewsApi
import com.example.reviews.data.ReviewsRepoImpl
import com.example.reviews.domain.ReviewsRepo
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ReviewsModule {

    @Binds
    @Singleton
    abstract fun bindReviewsRepo(impl: ReviewsRepoImpl): ReviewsRepo

    companion object {
        @Provides
        @Singleton
        fun provideReviewsApi(retrofit: Retrofit): ReviewsApi =
            retrofit.create(ReviewsApi::class.java)
    }
}
