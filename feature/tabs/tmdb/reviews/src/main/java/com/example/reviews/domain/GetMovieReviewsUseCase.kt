package com.example.reviews.domain

import com.example.entertainment.core.domain.Result
import com.example.entertainment.core.domain.TRPError
import javax.inject.Inject

class GetMovieReviewsUseCase @Inject constructor(
    private val reviewsRepo: ReviewsRepo,
) {
    suspend operator fun invoke(movieId: Int): Result<List<MovieReview>, TRPError> =
        reviewsRepo.getMovieReviews(movieId)
}
