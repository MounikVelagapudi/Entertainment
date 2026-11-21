package com.example.reviews.domain

import com.example.reviews.testdata.ReviewsResult
import io.mockk.coEvery
import io.mockk.mockk

class GetMovieReviewsUseCaseBuilder {

    val reviewsRepo = mockk<ReviewsRepo>()

    fun withRepoResult(movieId: Int, result: ReviewsResult) = apply {
        coEvery { reviewsRepo.getMovieReviews(movieId) } returns result
    }

    fun build() = GetMovieReviewsUseCase(reviewsRepo)
}
